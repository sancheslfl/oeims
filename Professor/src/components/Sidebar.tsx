import { type SubmitEventHandler, useState } from "react";
import type {CreateExamRequest, ExamResponse, SessionResponse} from "../types";

type SidebarProps = {
    isOpen: boolean;
    onToggle: () => void;
    exams: ExamResponse[];
    sessionsByExamId: Record<string, SessionResponse>;
    isLoadingExams: boolean;
    isCreatingExam: boolean;
    creatingSessionExamId: string | null;
    error: string;
    onCreateExam: (draft: CreateExamRequest) => Promise<void>;
    onGenerateSession: (examId: string) => Promise<void>;
};

type ExamCardProps = {
    exam: ExamResponse;
    session?: SessionResponse;
    isExpanded: boolean;
    isCreatingSession: boolean;
    onToggleSession: (examId: string) => Promise<void>;
};

function ExamCard({
                      exam,
                      session,
                      isExpanded,
                      isCreatingSession,
                      onToggleSession,
                  }: ExamCardProps) {
    return (
        <article className="grid gap-3 rounded-md border-2 border-isel-purple bg-isel-white p-4">
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

            <button
                type="button"
                className="app-button app-button-secondary"
                disabled={isCreatingSession}
                onClick={() => void onToggleSession(exam.id)}
            >
                {isCreatingSession
                    ? "Generating..."
                    : session
                        ? isExpanded
                            ? "Hide code"
                            : "Show code"
                        : "Generate session"}
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

export function Sidebar({
                            isOpen,
                            onToggle,
                            exams,
                            sessionsByExamId,
                            isLoadingExams,
                            isCreatingExam,
                            creatingSessionExamId,
                            error,
                            onCreateExam,
                            onGenerateSession,
                        }: SidebarProps) {
    const [examTitle, setExamTitle] = useState("");
    const [examDescription, setExamDescription] = useState("");
    const [durationMins, setDurationMins] = useState("");
    const [expandedExamId, setExpandedExamId] = useState<string | null>(null);

    const handleCreateExam: SubmitEventHandler<HTMLFormElement> = async (event) => {
        event.preventDefault();

        const title = examTitle.trim();
        const description = examDescription.trim();
        const duration = Number(durationMins);

        if (!title || !description || !Number.isInteger(duration) || duration <= 0) {
            return;
        }

        try {
            await onCreateExam({
                title,
                description,
                durationMins: duration,
            });

            setExamTitle("");
            setExamDescription("");
            setDurationMins("");
        } catch {
            // Error is displayed by the parent.
        }
    };

    async function handleToggleSession(examId: string) {
        if (expandedExamId === examId) {
            setExpandedExamId(null);
            return;
        }

        try {
            if (!sessionsByExamId[examId]) {
                await onGenerateSession(examId);
            }

            setExpandedExamId(examId);
        } catch {
            // Error is displayed by the parent.
        }
    }

    return (
        <aside
            className={`relative shrink-0 border-r-2 border-isel-purple bg-isel-white transition-[width] duration-150 ${
                isOpen ? "w-80" : "w-14"
            }`}
        >
            <button
                type="button"
                onClick={onToggle}
                aria-expanded={isOpen}
                aria-controls="dashboard-sidebar-content"
                className="absolute top-1/2 -right-4 z-10 grid h-12 w-8 -translate-y-1/2 place-items-center rounded-md border-2 border-isel-purple bg-isel-white text-2xl font-bold text-isel-purple"
            >
                {isOpen ? "‹" : "›"}
            </button>

            {isOpen ? (
                <div id="dashboard-sidebar-content" className="grid gap-8 p-6">
                    {error && (
                        <p className="rounded-md border-2 border-isel-red bg-isel-pink px-3 py-2 text-sm font-semibold text-isel-purple">
                            {error}
                        </p>
                    )}

                    <section className="grid gap-4">
                        <h2 className="app-section-title">Create exam</h2>

                        <form className="grid gap-4" onSubmit={handleCreateExam}>
                            <div className="grid gap-1">
                                <label htmlFor="exam-title" className="app-label">
                                    Title
                                </label>
                                <input
                                    id="exam-title"
                                    className="app-input"
                                    value={examTitle}
                                    onChange={(event) => setExamTitle(event.target.value)}
                                    placeholder="Operating Systems"
                                    required
                                />
                            </div>

                            <div className="grid gap-1">
                                <label htmlFor="exam-description" className="app-label">
                                    Description
                                </label>
                                <textarea
                                    id="exam-description"
                                    className="app-input min-h-24 resize-none"
                                    value={examDescription}
                                    onChange={(event) => setExamDescription(event.target.value)}
                                    placeholder="Final assessment for the OS module"
                                    required
                                />
                            </div>

                            <div className="grid gap-1">
                                <label htmlFor="exam-duration" className="app-label">
                                    Duration in minutes
                                </label>
                                <input
                                    id="exam-duration"
                                    className="app-input"
                                    type="number"
                                    min={1}
                                    value={durationMins}
                                    onChange={(event) => setDurationMins(event.target.value)}
                                    placeholder="90"
                                    required
                                />
                            </div>

                            <button
                                type="submit"
                                className="app-button"
                                disabled={isCreatingExam}
                            >
                                {isCreatingExam ? "Creating..." : "Create exam"}
                            </button>
                        </form>
                    </section>

                    <section className="grid gap-4">
                        <h2 className="app-section-title">Available exams</h2>

                        {isLoadingExams ? (
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
                                    />
                                ))}
                            </div>
                        )}
                    </section>
                </div>
            ) : (
                <span className="absolute top-6 left-1/2 -translate-x-1/2 text-xs font-bold uppercase tracking-widest text-isel-purple [writing-mode:vertical-rl]">
                    Menu
                </span>
            )}
        </aside>
    );
}