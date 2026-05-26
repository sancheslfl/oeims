import { useState } from "react";
import type { ExamResponse, SessionResponse } from "../types";

type SessionListProps = {
    exams: ExamResponse[];
    sessionsByExamId: Record<string, SessionResponse>;
    isLoading: boolean;
    creatingSessionExamId: string | null;
    onGenerateSession: (examId: string) => Promise<void>;
    onOpenSession: (examId: string) => void;
};

type ExamCardProps = {
    exam: ExamResponse;
    session?: SessionResponse;
    isExpanded: boolean;
    isCreatingSession: boolean;
    onToggleSession: (examId: string) => Promise<void>;
    onOpenSession: (examId: string) => void;
};

function ExamCard({
                      exam,
                      session,
                      isExpanded,
                      isCreatingSession,
                      onToggleSession,
                      onOpenSession,
                  }: ExamCardProps) {
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

            <button
                type="button"
                className="app-button app-button-secondary"
                disabled={isCreatingSession}
                onClick={() => void onToggleSession(exam.id)}
            >
                {getSessionButtonLabel(session, isExpanded, isCreatingSession)}
            </button>

            {isExpanded && session && (
                <div className="grid gap-3 rounded-md border-2 border-isel-red bg-isel-pink px-3 py-2">
                    <div>
                        <span className="text-xs font-bold uppercase tracking-widest text-isel-purple">
                            Session code
                        </span>

                        <p className="mt-1 text-2xl font-bold text-isel-red">
                            {session.code}
                        </p>
                    </div>

                    <button
                        type="button"
                        className="app-button"
                        onClick={() => onOpenSession(exam.id)}
                    >
                        Open
                    </button>
                </div>
            )}
        </article>
    );
}

export function SessionList({
                                exams,
                                sessionsByExamId,
                                isLoading,
                                creatingSessionExamId,
                                onGenerateSession,
                                onOpenSession,
                            }: SessionListProps) {
    const [expandedExamId, setExpandedExamId] = useState<string | null>(null);

    async function handleToggleSession(examId: string) {
        if (expandedExamId === examId) {
            setExpandedExamId(null);
            return;
        }

        if (!sessionsByExamId[examId]) {
            await onGenerateSession(examId);
        }

        setExpandedExamId(examId);
    }

    return (
        <section className="grid min-h-0 flex-1 grid-rows-[auto_1fr] gap-4">
            <h2 className="app-section-title">Available exams</h2>

            <div className="min-h-0 overflow-y-auto pr-1">
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
                                session={sessionsByExamId[exam.id]}
                                isExpanded={expandedExamId === exam.id}
                                isCreatingSession={creatingSessionExamId === exam.id}
                                onToggleSession={handleToggleSession}
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