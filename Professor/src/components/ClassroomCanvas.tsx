import type { OpenedSession, SessionResponse } from "../types";
import { useAuth } from "../AuthContext";
import {useCanvasSession} from "../hooks/useCanvasSession.ts";
import {useCanvasRealtimeData} from "../hooks/useCanvasRealtimeData.ts";
import {ClassroomSeats} from "./ClassroomSeats.tsx";

type ClassroomCanvasProps = {
    openedSession: OpenedSession | null;
};

export function ClassroomCanvas({ openedSession }: ClassroomCanvasProps) {
    const { auth } = useAuth();

    const {
        session,
        isStarting,
        isEnding,
        error: sessionError,
        startCurrentSession,
        endCurrentSession,
    } = useCanvasSession(openedSession?.session, auth?.token);

    const {
        participants,
        eventsByParticipantId,
        error: realtimeError,
    } = useCanvasRealtimeData(auth?.token, session);

    const error = sessionError || realtimeError;
    const canStartSession = session?.status === "PENDING";
    const canEndSession = session?.status === "ACTIVE";

    return (
        <div className="grid min-h-104 w-full min-w-3xl max-w-5xl gap-8 rounded-[3.5rem] border-3 border-isel-purple bg-isel-white p-8 shadow-[0_0.75rem_2rem_rgba(95,20,55,0.12)]">
            <div className="flex items-start justify-between gap-4">
                <div>
                    <h1 className="m-0 text-3xl font-bold text-isel-purple">
                        {openedSession?.exam.title ?? "Classroom"}
                    </h1>

                    <p className="mt-1 font-semibold text-isel-red">
                        {getSessionStatusLabel(session)}
                    </p>

                    {error && (
                        <p className="mt-3 rounded-md border-2 border-isel-red bg-isel-pink px-3 py-2 text-sm font-semibold text-isel-purple">
                            {error}
                        </p>
                    )}
                </div>

                <div className="flex gap-3">
                    {canStartSession && (
                        <button
                            type="button"
                            className="app-button"
                            disabled={isStarting}
                            onClick={() => void startCurrentSession()}
                        >
                            {isStarting ? "Starting..." : "Begin session"}
                        </button>
                    )}

                    {canEndSession && (
                        <button
                            type="button"
                            className="app-button"
                            disabled={isEnding}
                            onClick={() => void endCurrentSession()}
                        >
                            {isEnding ? "Ending..." : "End session"}
                        </button>
                    )}
                </div>
            </div>

            <ClassroomSeats
                sessionId={session?.id}
                sessionStatus={session?.status}
                participants={participants}
                eventsByParticipantId={eventsByParticipantId}
            />
        </div>
    );
}

function getSessionStatusLabel(session?: SessionResponse) {
    if (!session) {
        return "No active session";
    }

    switch (session.status) {
        case "PENDING":
            return "Waiting for students";
        case "ACTIVE":
            return "Session started";
        case "ENDED":
            return "Session ended";
    }
}