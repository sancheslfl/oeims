import { useEffect, useMemo, useState } from "react";
import type { ExamResponse, OpenedSession, SessionResponse } from "../../types";
import { useAuth } from "../../auth.ts";
import { getExams } from "../../api/exams";
import { getActiveSessions } from "../../api/sessions";
import { CreateExamForm } from "./CreateExamForm";
import { SessionList } from "./SessionList";
import { loadAllowedEmailDomain } from "../../localStorage.ts";
import {
    REALTIME_CHANNELS,
    REALTIME_EVENTS,
    useEventListener,
} from "../../hooks/useEventListener";

type SidebarExamsViewProps = {
    openedSession: OpenedSession | null;
    onOpenSession: (openedSession: OpenedSession) => void;
    onCloseSession: () => void;
};

export function ExamsView({
                                     openedSession,
                                     onOpenSession,
                                     onCloseSession,
                                 }: SidebarExamsViewProps) {
    const { auth } = useAuth();

    const [exams, setExams] = useState<ExamResponse[]>([]);
    const [activeSessions, setActiveSessions] = useState<SessionResponse[]>([]);
    const [isLoadingExams, setIsLoadingExams] = useState(false);
    const [error, setError] = useState("");

    const allowedEmailDomain = loadAllowedEmailDomain(auth?.id) ?? "";

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

        return () => {
            ignore = true;
        };
    }, [auth?.token]);

    const sseHandlers = useMemo(
        () => ({
            [REALTIME_EVENTS.SessionCreated]: (data: unknown) => {
                const session = data as SessionResponse;

                setActiveSessions((current) =>
                    current.some((s) => s.id === session.id)
                        ? current
                        : [...current, session],
                );
            },

            [REALTIME_EVENTS.SessionStatusUpdated]: (data: unknown) => {
                const session = data as SessionResponse;

                setActiveSessions((current) => {
                    if (session.status === "ENDED") {
                        return current.filter((s) => s.id !== session.id);
                    }

                    return current.map((s) =>
                        s.id === session.id ? session : s,
                    );
                });

                if (openedSession?.session.id !== session.id) {
                    return;
                }

                if (session.status === "ENDED") {
                    onCloseSession();
                    return;
                }

                onOpenSession({ ...openedSession, session });
            },
        }),
        [openedSession, onOpenSession, onCloseSession],
    );

    useEventListener(auth ? REALTIME_CHANNELS.sessions : null, sseHandlers);

    function handleExamCreated(exam: ExamResponse) {
        setExams((current) => [exam, ...current]);
    }

    return (
        <>
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
                allowedEmailDomain={allowedEmailDomain}
                onOpenSession={onOpenSession}
            />
        </>
    );
}
