import { useState } from "react";
import {endSession, startSession} from "../api/sessions.ts";
import type {SessionResponse} from "../types";

export function useCanvasSession(
    openedSession: SessionResponse | undefined,
    token: string | undefined,
) {
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

    async function startCurrentSession() {
        if (!token || !session) {
            return;
        }

        setError("");
        setIsStarting(true);

        try {
            const started = await startSession(session.id, token);
            setSessionOverride(started);
        } catch (error) {
            setError(error instanceof Error ? error.message : "Unexpected error.");
        } finally {
            setIsStarting(false);
        }
    }

    async function endCurrentSession() {
        if (!token || !session) {
            return;
        }

        setError("");
        setIsEnding(true);

        try {
            const ended = await endSession(session.id, token);
            setSessionOverride(ended);
        } catch (error) {
            if (error instanceof Error) {
                setError(error.message);
            }
        } finally {
            setIsEnding(false);
        }
    }

    return {
        session,
        isStarting,
        isEnding,
        error,
        startCurrentSession,
        endCurrentSession,
    };
}