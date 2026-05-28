import type {SessionResponse} from "../../types";
import {useCanvasSession} from "./CanvasSessionContext.ts";


type CanvasHeaderProps = {
    title: string;
    realtimeError: string;
};

export function CanvasHeader({ title, realtimeError }: CanvasHeaderProps) {
    const {
        session,
        canStartSession,
        canEndSession,
        isStarting,
        isEnding,
        error: sessionError,
        startCurrentSession,
        endCurrentSession,
    } = useCanvasSession();

    const error = sessionError || realtimeError;

    return (
        <div className="flex items-start justify-between gap-4">
            <div>
                <h1 className="m-0 text-3xl font-bold text-isel-purple">
                    {title}
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