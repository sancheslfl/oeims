import {type SubmitEventHandler, useState} from "react";
import type { ExamDraft, SessionDraft } from "../types";

type SidebarProps = {
    isOpen: boolean;
    onToggle: () => void;
    onCreateExam: (draft: ExamDraft) => void;
    onGenerateSession: (draft: SessionDraft) => void;
};

export function Sidebar({
                                     isOpen,
                                     onToggle,
                                     onCreateExam,
                                     onGenerateSession,
                                 }: SidebarProps) {
    const [examTitle, setExamTitle] = useState("");
    const [scheduledAt, setScheduledAt] = useState("");
    const [examCode, setExamCode] = useState("");

    const handleCreateExam: SubmitEventHandler<HTMLFormElement> = (event) => {
        event.preventDefault();

        const title = examTitle.trim();

        if (!title) {
            return;
        }

        onCreateExam({
            title,
            scheduledAt,
        });
    };

    const handleGenerateSession: SubmitEventHandler<HTMLFormElement> = (event) => {
        event.preventDefault();

        const code = examCode.trim();

        if (!code) {
            return;
        }

        onGenerateSession({
            examCode: code,
        });
    };

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
                    <section className="grid gap-4">
                        <h2 className="app-section-title">Create exam</h2>

                        <form className="grid gap-4" onSubmit={handleCreateExam}>
                            <div className="grid gap-1">
                                <label htmlFor="exam-title" className="app-label">
                                    Exam title
                                </label>
                                <input
                                    id="exam-title"
                                    className="app-input"
                                    value={examTitle}
                                    onChange={(event) => setExamTitle(event.target.value)}
                                    placeholder="Operating Systems"
                                />
                            </div>

                            <div className="grid gap-1">
                                <label htmlFor="exam-date" className="app-label">
                                    Scheduled date
                                </label>
                                <input
                                    id="exam-date"
                                    className="app-input"
                                    type="datetime-local"
                                    value={scheduledAt}
                                    onChange={(event) => setScheduledAt(event.target.value)}
                                />
                            </div>

                            <button type="submit" className="app-button">
                                Create exam
                            </button>
                        </form>
                    </section>

                    <section className="grid gap-4">
                        <h2 className="app-section-title">Generate session</h2>

                        <form className="grid gap-4" onSubmit={handleGenerateSession}>
                            <div className="grid gap-1">
                                <label htmlFor="exam-code" className="app-label">
                                    Exam code
                                </label>
                                <input
                                    id="exam-code"
                                    className="app-input"
                                    value={examCode}
                                    onChange={(event) => setExamCode(event.target.value)}
                                    placeholder="EXAM-001"
                                />
                            </div>

                            <button type="submit" className="app-button app-button-secondary">
                                Generate session
                            </button>
                        </form>
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