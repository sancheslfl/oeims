import { type ReactNode, useState } from "react";
import type { SessionResponse } from "../../types";
import { useAuth } from "../../auth.ts";
import { endSession, getSessionReport, startSession } from "../../api/sessions";
import {
    CanvasSessionContext,
    type PendingSessionOperation,
} from "./CanvasSessionContext";

type CanvasSessionProviderProps = {
    openedSession: SessionResponse | undefined;
    children: ReactNode;
};

export function CanvasSessionProvider({
                                           openedSession,
                                           children,
                                       }: CanvasSessionProviderProps) {
    const { auth } = useAuth();

    const [sessionOverride, setSessionOverride] = useState<SessionResponse | null>(null);
    const [pendingOperation, setPendingOperation] =
        useState<PendingSessionOperation>(null);
    const [error, setError] = useState("");

    const session =
        openedSession
            ? sessionOverride?.id === openedSession.id
                ? sessionOverride
                : openedSession
            : sessionOverride ?? undefined;

    async function runSessionOperation(
        operation: Exclude<PendingSessionOperation, null>,
        execute: () => Promise<void>,
    ) {
        setError("");
        setPendingOperation(operation);

        try {
            await execute();
        } catch (error) {
            setError(error instanceof Error ? error.message : "Unexpected error.");
        } finally {
            setPendingOperation(null);
        }
    }

    function startCurrentSession() {
        if (!auth || !session) {
            return Promise.resolve();
        }

        return runSessionOperation("start", async () => {
            setSessionOverride(await startSession(session.id, auth.token));
        });
    }

    function endCurrentSession() {
        if (!auth || !session) {
            return Promise.resolve();
        }

        return runSessionOperation("end", async () => {
            setSessionOverride(await endSession(session.id, auth.token));
        });
    }

    function downloadCurrentReport() {
        if (!auth || !session) {
            return Promise.resolve();
        }

        return runSessionOperation("download", async () => {
            const report = await getSessionReport(session.id, auth.token);
            downloadBlob(report, `oeims-session-${session.id}.txt`);
        });
    }

    return (
        <CanvasSessionContext.Provider
            value={{
                session,
                pendingOperation,
                error,
                startCurrentSession,
                endCurrentSession,
                downloadCurrentReport,
            }}
        >
            {children}
        </CanvasSessionContext.Provider>
    );
}

function downloadBlob(blob: Blob, filename: string) {
    const url = URL.createObjectURL(blob);

    try {
        const link = document.createElement("a");
        link.href = url;
        link.download = filename;
        link.click();
    } finally {
        URL.revokeObjectURL(url);
    }
}
