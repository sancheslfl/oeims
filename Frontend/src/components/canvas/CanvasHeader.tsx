import type { SessionResponse } from "../../types";
import {
    type PendingSessionOperation,
    useCanvasSession,
} from "./CanvasSessionContext.ts";

type CanvasHeaderProps = {
    title: string;
    realtimeError: string;
};

type SessionAction = {
    label: string;
    disabled: boolean;
    run: () => Promise<void>;
};

export function CanvasHeader({ title, realtimeError }: CanvasHeaderProps) {
    const {
        session,
        pendingOperation,
        error: sessionError,
        startCurrentSession,
        endCurrentSession,
        downloadCurrentReport,
    } = useCanvasSession();

    const error = sessionError || realtimeError;

    const sessionAction = getSessionAction({
        session,
        pendingOperation,
        startCurrentSession,
        endCurrentSession,
        downloadCurrentReport,
    });

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
                {sessionAction && (
                    <button
                        type="button"
                        className="app-button"
                        disabled={sessionAction.disabled}
                        onClick={() => void sessionAction.run()}
                    >
                        {sessionAction.label}
                    </button>
                )}
            </div>
        </div>
    );
}

type SessionActionArgs = {
    session: SessionResponse | undefined;
    pendingOperation: PendingSessionOperation;
    startCurrentSession: () => Promise<void>;
    endCurrentSession: () => Promise<void>;
    downloadCurrentReport: () => Promise<void>;
};

function getSessionAction({
                              session,
                              pendingOperation,
                              startCurrentSession,
                              endCurrentSession,
                              downloadCurrentReport,
                          }: SessionActionArgs): SessionAction | undefined {
    const isBusy = pendingOperation !== null;

    switch (session?.status) {
        case "PENDING":
            return {
                label: pendingOperation === "start" ? "Starting..." : "Begin session",
                disabled: isBusy,
                run: startCurrentSession,
            };

        case "ACTIVE":
            return {
                label: pendingOperation === "end" ? "Ending..." : "End session",
                disabled: isBusy,
                run: endCurrentSession,
            };

        case "ENDED":
            return {
                label:
                    pendingOperation === "download"
                        ? "Downloading..."
                        : "Download report",
                disabled: isBusy,
                run: downloadCurrentReport,
            };

        default:
            return undefined;
    }
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