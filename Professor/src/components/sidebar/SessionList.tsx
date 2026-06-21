import { type SubmitEventHandler, useState } from "react";
import type { ExamResponse, OpenedSession, SessionResponse } from "../../types";
import { useAuth } from "../../AuthContext";
import { createSession, joinSessionAsSupervisor } from "../../api/sessions";
import { getExam } from "../../api/exams";
import { saveLastSessionId } from "../../localStorage.ts";

type SessionListProps = {
    exams: ExamResponse[];
    activeSessions: SessionResponse[];
    isLoading: boolean;
    openedSession: OpenedSession | null;
    allowedEmailDomain: string;
    onOpenSession: (openedSession: OpenedSession) => void;
};

type ExamCardProps = {
    exam: ExamResponse;
    activeSession: SessionResponse | undefined;
    restoredSession?: SessionResponse;
    allowedEmailDomain: string;
    onOpenSession: (openedSession: OpenedSession) => void;
};

function ExamCard({
                      exam,
                      activeSession,
                      restoredSession,
                      allowedEmailDomain,
                      onOpenSession,
                  }: ExamCardProps) {
    const { auth } = useAuth();

    const ownActiveSession =
        activeSession?.supervisorId === auth?.id ? activeSession : undefined;

    const [session, setSession] = useState<SessionResponse | undefined>(
        restoredSession ?? ownActiveSession,
    );
    const [isExpanded, setIsExpanded] = useState(Boolean(restoredSession));
    const [isCreatingSession, setIsCreatingSession] = useState(false);

    const [joinCode, setJoinCode] = useState("");
    const [isJoining, setIsJoining] = useState(false);
    const [showJoinInput, setShowJoinInput] = useState(false);

    const [error, setError] = useState("");

    const openSession = session?.status !== "ENDED" ? session : undefined;
    const hasActiveSession = Boolean(activeSession) && !openSession;

    async function handleToggleSession() {
        if (isExpanded) {
            setIsExpanded(false);
            return;
        }

        if (openSession) {
            setIsExpanded(true);
            onOpenSession({ exam, session: openSession });
            return;
        }

        if (!auth) return;

        setError("");
        setIsCreatingSession(true);

        try {
            const createdSession = await createSession(
                exam.id,
                allowedEmailDomain,
                auth.token
            );

            setSession(createdSession);
            setIsExpanded(true);
            saveLastSessionId(auth.id, createdSession.id);

            onOpenSession({ exam, session: createdSession });
        } catch (error) {
            if (error instanceof Error) {
                setError(error.message);
            }
        } finally {
            setIsCreatingSession(false);
        }
    }

    const handleJoin: SubmitEventHandler<HTMLFormElement> = async (event) => {
        event.preventDefault();

        if (!auth || !joinCode.trim()) return;

        setError("");
        setIsJoining(true);

        try {
            const joinedSession = await joinSessionAsSupervisor(
                joinCode.trim(),
                auth.token,
            );
            const joinedExam = await getExam(joinedSession.examId, auth.token);

            saveLastSessionId(auth.id, joinedSession.id);
            onOpenSession({ exam: joinedExam, session: joinedSession });
        } catch (error) {
            if (error instanceof Error) {
                setError(error.message);
            }
        } finally {
            setIsJoining(false);
        }
    };

    const borderClass =
        openSession || hasActiveSession
            ? "border-green-500"
            : "border-isel-purple";

    return (
        <article
            className={`grid gap-3 rounded-md border-2 ${borderClass} bg-isel-white p-4 transition-colors duration-300`}
        >
            <div className="grid gap-1">
                <h3 className="font-bold text-isel-purple">{exam.title}</h3>

                {exam.description && (
                    <p className="text-sm text-isel-purple/80">
                        {exam.description}
                    </p>
                )}

                <span className="text-sm font-semibold text-isel-red">
                    {exam.durationMins} minutes
                </span>
            </div>

            {error && (
                <p className="rounded-md border-2 border-isel-red bg-isel-pink px-3 py-2 text-sm font-semibold text-isel-purple">
                    {error}
                </p>
            )}

            {hasActiveSession && !showJoinInput && (
                <button
                    type="button"
                    className="app-button"
                    onClick={() => setShowJoinInput(true)}
                >
                    Join session
                </button>
            )}

            {hasActiveSession && showJoinInput && (
                <form className="grid gap-2" onSubmit={handleJoin}>
                    <input
                        className="app-input font-mono uppercase tracking-widest"
                        placeholder="Session code"
                        maxLength={6}
                        value={joinCode}
                        onChange={(event) =>
                            setJoinCode(event.currentTarget.value.toUpperCase())
                        }
                        autoFocus
                    />

                    <div className="flex gap-2">
                        <button
                            type="submit"
                            className="app-button flex-1"
                            disabled={isJoining || joinCode.trim().length !== 6}
                        >
                            {isJoining ? "Joining..." : "Join"}
                        </button>

                        <button
                            type="button"
                            className="app-button app-button-secondary flex-1"
                            onClick={() => {
                                setShowJoinInput(false);
                                setJoinCode("");
                                setError("");
                            }}
                        >
                            Cancel
                        </button>
                    </div>
                </form>
            )}

            {!hasActiveSession && (
                <button
                    type="button"
                    className="app-button app-button-secondary"
                    disabled={isCreatingSession}
                    onClick={handleToggleSession}
                >
                    {getSessionButtonLabel(
                        openSession,
                        isExpanded,
                        isCreatingSession,
                    )}
                </button>
            )}

            {isExpanded && openSession && (
                <div className="rounded-md border-2 border-isel-red bg-isel-pink px-3 py-2">
                    <span className="text-xs font-bold uppercase tracking-widest text-isel-purple">
                        Session code
                    </span>

                    <p className="mt-1 text-2xl font-bold text-isel-red">
                        {openSession.code}
                    </p>
                </div>
            )}
        </article>
    );
}

export function SessionList({
                                exams,
                                activeSessions,
                                isLoading,
                                openedSession,
                                allowedEmailDomain,
                                onOpenSession,
                            }: SessionListProps) {
    return (
        <section className="grid min-h-0 flex-1 grid-rows-[auto_1fr] gap-4">
            <h2 className="app-section-title">Available exams</h2>

            <div className="min-h-0">
                {isLoading ? (
                    <p className="text-sm font-semibold text-isel-purple/70">
                        Loading exams...
                    </p>
                ) : exams.length === 0 ? (
                    <p className="text-sm font-semibold text-isel-purple/70">
                        No exams created yet.
                    </p>
                ) : (
                    <div className="grid gap-3">
                        {exams.map((exam) => {
                            const restoredSession =
                                openedSession?.exam.id === exam.id
                                    ? openedSession.session
                                    : undefined;

                            const activeSession = activeSessions.find(
                                (session) => session.examId === exam.id,
                            );

                            return (
                                <ExamCard
                                    key={`${exam.id}:${restoredSession?.id ?? "empty"}`}
                                    exam={exam}
                                    activeSession={activeSession}
                                    restoredSession={restoredSession}
                                    allowedEmailDomain={allowedEmailDomain}
                                    onOpenSession={onOpenSession}
                                />
                            );
                        })}
                    </div>
                )}
            </div>
        </section>
    );
}

function getSessionButtonLabel(
    session: SessionResponse | undefined,
    isExpanded: boolean,
    isCreatingSession: boolean,
) {
    if (isCreatingSession) {
        return "Generating...";
    }

    if (!session) {
        return "Generate session";
    }

    return isExpanded ? "Hide code" : "Show code";
}