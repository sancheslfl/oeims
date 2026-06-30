import {
    type SubmitEventHandler,
    useEffect,
    useRef,
    useState,
} from "react";
import type {ExamResponse, OpenedSession, SessionResponse} from "../../types";
import {useAuth} from "../../AuthContext";
import {createSession, joinSessionAsSupervisor} from "../../api/sessions";
import {getExam} from "../../api/exams";
import {saveLastSessionId} from "../../localStorage.ts";

const JOIN_PAGE_PATH = "/student/join";

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
    const {auth} = useAuth();

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
    const [copyAnimationKey, setCopyAnimationKey] = useState(0);
    const copyFeedbackTimeoutRef = useRef<number | undefined>(undefined);

    useEffect(() => {
        return () => {
            window.clearTimeout(copyFeedbackTimeoutRef.current);
        };
    }, []);

    const openSession = session?.status !== "ENDED" ? session : undefined;
    const hasActiveSession = Boolean(activeSession) && !openSession;

    async function handleToggleSession() {
        if (isExpanded) {
            setIsExpanded(false);
            return;
        }

        if (openSession) {
            setIsExpanded(true);
            onOpenSession({exam, session: openSession});
            return;
        }

        if (!auth) return;

        setError("");
        setIsCreatingSession(true);

        try {
            const createdSession = await createSession(
                exam.id,
                allowedEmailDomain,
                auth.token,
            );

            setSession(createdSession);
            setIsExpanded(true);
            saveLastSessionId(auth.id, createdSession.id);

            onOpenSession({exam, session: createdSession});
        } catch (error) {
            if (error instanceof Error) {
                setError(error.message);
            }
        } finally {
            setIsCreatingSession(false);
        }
    }

    async function handleCopyStudentLink(session: SessionResponse) {
        setError("");

        try {
            await copyToClipboard(buildStudentSessionLink(session.code));
            setCopyAnimationKey((current) => current + 1);
        } catch (error) {
            if (error instanceof Error) {
                setError(error.message);
            }
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
            onOpenSession({exam: joinedExam, session: joinedSession});
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

                    <div className="mt-1 flex items-center gap-2">
                        <p className="text-2xl font-bold text-isel-red">
                            {openSession.code}
                        </p>

                        <button
                            type="button"
                            aria-label="Copy student session link"
                            title="Copy student session link"
                            onClick={() => handleCopyStudentLink(openSession)}
                            className="grid h-8 w-8 place-items-center rounded-md border-2 border-isel-purple bg-isel-white text-isel-purple transition-colors hover:border-isel-red hover:text-isel-red"
                        >
                        <span
                            key={copyAnimationKey}
                            aria-hidden="true"
                            className="grid h-4 w-4 place-items-center"
                        >
                            <span
                                className={`col-start-1 row-start-1 ${
                                    copyAnimationKey > 0
                                        ? "animate-[copy-session-link-hide_2s_ease_both]"
                                        : ""
                                }`}
                            >
                                <LinkIcon />
                            </span>

                            <span
                                className={`col-start-1 row-start-1 opacity-0 ${
                                    copyAnimationKey > 0
                                        ? "animate-[copy-session-link-check_2s_ease_both]"
                                        : ""
                                }`}
                            >
                                <CheckIcon />
                            </span>
                        </span>
                        </button>
                    </div>
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

function buildStudentSessionLink(code: string) {
    return new URL(
        `${JOIN_PAGE_PATH}/${encodeURIComponent(code)}`,
        window.location.origin,
    ).toString();
}

async function copyToClipboard(value: string) {
    if (!window.isSecureContext || !navigator.clipboard) {
        throw new Error(
            "Could not copy the student link. Clipboard access requires HTTPS or localhost.",
        );
    }

    await navigator.clipboard.writeText(value);
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

function LinkIcon() {
    return (
        <svg
            aria-hidden="true"
            viewBox="0 0 24 24"
            className="h-4 w-4"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
        >
            <path d="M10 13a5 5 0 0 0 7.07 0l2.12-2.12a5 5 0 0 0-7.07-7.07L11 4.93"/>
            <path d="M14 11a5 5 0 0 0-7.07 0L4.81 13.12a5 5 0 0 0 7.07 7.07L13 19.07"/>
        </svg>
    );
}

function CheckIcon() {
    return (
        <svg
            aria-hidden="true"
            viewBox="0 0 24 24"
            className="h-4 w-4"
            fill="none"
            stroke="currentColor"
            strokeWidth="2.5"
            strokeLinecap="round"
            strokeLinejoin="round"
        >
            <path d="M20 6 9 17l-5-5"/>
        </svg>
    );
}