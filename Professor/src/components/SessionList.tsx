import { useState } from "react";
import type { ExamResponse, OpenedSession, SessionResponse } from "../types";
import { useAuth } from "../AuthContext";
import { createSession } from "../api/sessions";

type SessionListProps = {
    exams: ExamResponse[];
    isLoading: boolean;
    onOpenSession: (openedSession: OpenedSession) => void;
};

type ExamCardProps = {
    exam: ExamResponse;
    onOpenSession: (openedSession: OpenedSession) => void;
};

function ExamCard({ exam, onOpenSession }: ExamCardProps) {
    const { auth } = useAuth();

    const [session, setSession] = useState<SessionResponse | undefined>();
    const [isExpanded, setIsExpanded] = useState(false);
    const [isCreatingSession, setIsCreatingSession] = useState(false);
    const [error, setError] = useState("");

    async function handleToggleSession() {
        if (isExpanded) {
            setIsExpanded(false);
            return;
        }

        if (session) {
            setIsExpanded(true);
            return;
        }

        if (!auth) {
            return;
        }

        setError("");
        setIsCreatingSession(true);

        try {
            const createdSession = await createSession(exam.id, auth.token);

            setSession(createdSession);
            setIsExpanded(true);
            onOpenSession({
                exam,
                session: createdSession,
            });
        } catch (error) {
            setError(error instanceof Error ? error.message : "Unexpected error.");
        } finally {
            setIsCreatingSession(false);
        }
    }

    return (
        <article className="grid gap-3 rounded-md border-2 border-isel-purple bg-isel-white p-4">
            <div className="grid gap-1">
                <h3 className="font-bold text-isel-purple">
                    {exam.title}
                </h3>

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

            <button
                type="button"
                className="app-button app-button-secondary"
                disabled={isCreatingSession}
                onClick={() => void handleToggleSession()}
            >
                {getSessionButtonLabel(session, isExpanded, isCreatingSession)}
            </button>

            {isExpanded && session && (
                <div className="rounded-md border-2 border-isel-red bg-isel-pink px-3 py-2">
                    <span className="text-xs font-bold uppercase tracking-widest text-isel-purple">
                        Session code
                    </span>

                    <p className="mt-1 text-2xl font-bold text-isel-red">
                        {session.code}
                    </p>
                </div>
            )}
        </article>
    );
}

export function SessionList({
                                exams,
                                isLoading,
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
                        {exams.map((exam) => (
                            <ExamCard
                                key={exam.id}
                                exam={exam}
                                onOpenSession={onOpenSession}
                            />
                        ))}
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