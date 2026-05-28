import { type ReactNode, useState } from "react";
import type { SessionResponse } from "../../types";
import { useAuth } from "../../AuthContext";
import { endSession, startSession } from "../../api/sessions";
import { CanvasSessionContext } from "./CanvasSessionContext";

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
    const [isStarting, setIsStarting] = useState(false);
    const [isEnding, setIsEnding] = useState(false);
    const [error, setError] = useState("");

    const canUseSessionOverride =
        sessionOverride &&
        openedSession &&
        sessionOverride.id === openedSession.id &&
        openedSession.status !== "ENDED";

    const session = canUseSessionOverride ? sessionOverride : openedSession;

    const canStartSession = session?.status === "PENDING";
    const canEndSession = session?.status === "ACTIVE";

    async function startCurrentSession() {
        if (!auth || !session) {
            return;
        }

        setError("");
        setIsStarting(true);

        try {
            const started = await startSession(session.id, auth.token);
            setSessionOverride(started);
        } catch (error) {
            setError(error instanceof Error ? error.message : "Unexpected error.");
        } finally {
            setIsStarting(false);
        }
    }

    async function endCurrentSession() {
        if (!auth || !session) {
            return;
        }

        setError("");
        setIsEnding(true);

        try {
            const ended = await endSession(session.id, auth.token);
            setSessionOverride(ended);
        } catch (error) {
            setError(error instanceof Error ? error.message : "Unexpected error.");
        } finally {
            setIsEnding(false);
        }
    }

    return (
        <CanvasSessionContext.Provider
            value={{
                session,
                canStartSession,
                canEndSession,
                isStarting,
                isEnding,
                error,
                startCurrentSession,
                endCurrentSession,
            }}
        >
            {children}
        </CanvasSessionContext.Provider>
    );
}