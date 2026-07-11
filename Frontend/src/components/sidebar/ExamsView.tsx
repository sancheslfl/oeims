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
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState("");

    const allowedEmailDomain = loadAllowedEmailDomain(auth?.id);

    const professorEventId = auth
        ? REALTIME_CHANNELS.professor(auth.id)
        : null;

    const sseMessageHandler = useMemo(
        () => ({
            [REALTIME_EVENTS.SessionCreated]: (data: unknown) => {
                const session = data as SessionResponse;

                setActiveSessions((currentSessions) =>
                    upsertSession(currentSessions, session),
                );
            },
            [REALTIME_EVENTS.SessionStarted]: (data: unknown) => {
                const session = data as SessionResponse;

                setActiveSessions((currentSessions) =>
                    upsertSession(currentSessions, session),
                );
            },
            [REALTIME_EVENTS.SessionEnded]: (data: unknown) => {
                const session = data as SessionResponse;

                setActiveSessions((currentSessions) =>
                    currentSessions.filter(
                        (currentSession) => currentSession.id !== session.id,
                    ),
                );

                if (openedSession?.session.id === session.id) {
                    onCloseSession();
                }
            },
        }),
        [openedSession, onCloseSession],
    );

    useEventListener(professorEventId, sseMessageHandler);

    useEffect(() => {
        if (!auth) {
            return;
        }

        let ignore = false;

        async function loadData(token: string) {
            setIsLoading(true);
            setError("");

            try {
                const [exams, activeSessions] = await Promise.all([
                    getExams(token),
                    getActiveSessions(token),
                ]);

                if (!ignore) {
                    setExams(exams);
                    setActiveSessions(activeSessions);
                }
            } catch (error) {
                if (!ignore) {
                    setError(
                        error instanceof Error
                            ? error.message
                            : "Could not load exams.",
                    );
                }
            } finally {
                if (!ignore) {
                    setIsLoading(false);
                }
            }
        }

        void loadData(auth.token);

        return () => {
            ignore = true;
        };
    }, [auth]);

    function handleExamCreated(exam: ExamResponse) {
        setExams((currentExams) => [exam, ...currentExams]);
    }

    return (
        <>
            <CreateExamForm onExamCreated={handleExamCreated} />

            {error && (
                <p className="rounded-md border-2 border-isel-red bg-isel-pink px-3 py-2 text-sm font-semibold text-isel-purple">
                    {error}
                </p>
            )}

            <SessionList
                exams={exams}
                activeSessions={activeSessions}
                isLoading={isLoading}
                openedSession={openedSession}
                allowedEmailDomain={allowedEmailDomain}
                onOpenSession={onOpenSession}
            />
        </>
    );
}

function upsertSession(
    sessions: SessionResponse[],
    session: SessionResponse,
): SessionResponse[] {
    const existingSessionIndex = sessions.findIndex(
        (currentSession) => currentSession.id === session.id,
    );

    if (existingSessionIndex === -1) {
        return [session, ...sessions];
    }

    return sessions.map((currentSession) =>
        currentSession.id === session.id ? session : currentSession,
    );
}
