import { useEffect, useState } from "react";
import type { ExamResponse, OpenedSession } from "../types";
import { useAuth } from "../AuthContext";
import { getExams } from "../api/exams";
import { CreateExamForm } from "./CreateExamForm";
import { SessionList } from "./SessionList";

type SidebarProps = {
    onOpenSession: (openedSession: OpenedSession) => void;
};

export function Sidebar({ onOpenSession }: SidebarProps) {
    const { auth } = useAuth();

    const [isOpen, setIsOpen] = useState(true);
    const [exams, setExams] = useState<ExamResponse[]>([]);
    const [isLoadingExams, setIsLoadingExams] = useState(false);
    const [error, setError] = useState("");

    useEffect(() => {
        const token = auth?.token;

        if (!token) return;

        let ignore = false;

        async function loadExams(token: string) {
            setError("");
            setIsLoadingExams(true);

            try {
                const loadedExams = await getExams(token);

                if (!ignore) {
                    setExams(loadedExams);
                }
            } catch (error) {
                if (!ignore && error instanceof Error) {
                    setError(error.message);
                }
            } finally {
                if (!ignore) {
                    setIsLoadingExams(false);
                }
            }
        }

        void loadExams(token);

        return () => {
            ignore = true;
        };
    }, [auth?.token]);

    function handleExamCreated(exam: ExamResponse) {
        setExams((current) => [exam, ...current]);
    }

    return (
        <aside
            className={`relative h-full min-h-0 shrink-0 overflow-visible border-r-2 border-isel-purple bg-isel-white transition-[width] duration-150 ${
                isOpen ? "w-80" : "w-14"
            }`}
        >
            <button
                type="button"
                onClick={() => setIsOpen((current) => !current)}
                aria-expanded={isOpen}
                className="absolute top-1/2 -right-5 z-20 grid h-12 w-8 -translate-y-1/2 place-items-center rounded-md border-2 border-isel-purple bg-isel-white text-2xl font-bold text-isel-purple"
            >
                {isOpen ? "‹" : "›"}
            </button>

            {isOpen ? (
                <div className="sidebar-scroll h-full min-h-0 overflow-y-auto">
                    <div className="grid gap-8 p-6">
                        {error && (
                            <p className="rounded-md border-2 border-isel-red bg-isel-pink px-3 py-2 text-sm font-semibold text-isel-purple">
                                {error}
                            </p>
                        )}

                        <CreateExamForm onExamCreated={handleExamCreated} />

                        <SessionList
                            exams={exams}
                            isLoading={isLoadingExams}
                            onOpenSession={onOpenSession}
                        />
                    </div>
                </div>
            ) : (
                <span className="absolute top-6 left-1/2 -translate-x-1/2 text-xs font-bold uppercase tracking-widest text-isel-purple [writing-mode:vertical-rl]">
                    Menu
                </span>
            )}
        </aside>
    );
}