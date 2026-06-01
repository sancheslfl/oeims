import { useEffect, useRef, useState } from "react";
import type { ExamResponse, OpenedSession, SessionResponse } from "../../types";
import { useAuth } from "../../AuthContext";
import { getExams } from "../../api/exams";
import { getActiveSessions } from "../../api/sessions";
import { CreateExamForm } from "./CreateExamForm";
import { SessionList } from "./SessionList";

type SidebarProps = {
    openedSession: OpenedSession | null;
    onOpenSession: (openedSession: OpenedSession) => void;
    onCloseSession: () => void;
};

export function Sidebar({ openedSession, onOpenSession, onCloseSession }: SidebarProps) {
    const { auth } = useAuth();

    const [isOpen, setIsOpen] = useState(true);
    const [exams, setExams] = useState<ExamResponse[]>([]);
    const [activeSessions, setActiveSessions] = useState<SessionResponse[]>([]);
    const [isLoadingExams, setIsLoadingExams] = useState(false);
    const [error, setError] = useState("");

    const openedSessionRef = useRef(openedSession);
    const onOpenSessionRef = useRef(onOpenSession);
    const onCloseSessionRef = useRef(onCloseSession);
    openedSessionRef.current = openedSession;
    onOpenSessionRef.current = onOpenSession;
    onCloseSessionRef.current = onCloseSession;

    useEffect(() => {
        const token = auth?.token;

        if (!token) return;

        let ignore = false;

        async function loadData(token: string) {
            setError("");
            setIsLoadingExams(true);

            try {
                const [loadedExams, loadedActiveSessions] = await Promise.all([
                    getExams(token),
                    getActiveSessions(token),
                ]);

                if (!ignore) {
                    setExams(loadedExams);
                    setActiveSessions(loadedActiveSessions);
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

        void loadData(token);

        const interval = setInterval(() => {
            getActiveSessions(token).then((sessions) => {
                if (ignore) return;
                setActiveSessions(sessions);
                const current = openedSessionRef.current;
                if (current) {
                    const updated = sessions.find((s) => s.id === current.session.id);
                    if (!updated) {
                        onCloseSessionRef.current();
                    } else if (updated.status !== current.session.status) {
                        onOpenSessionRef.current({ ...current, session: updated });
                    }
                }
            }).catch(() => {});
        }, 3_000);

        return () => {
            ignore = true;
            clearInterval(interval);
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
                            activeSessions={activeSessions}
                            isLoading={isLoadingExams}
                            openedSession={openedSession}
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
