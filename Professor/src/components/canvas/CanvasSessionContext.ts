import { createContext, useContext } from "react";
import type { SessionResponse } from "../../types";

export type CanvasSessionContextValue = {
    session: SessionResponse | undefined;
    canStartSession: boolean;
    canEndSession: boolean;
    isStarting: boolean;
    isEnding: boolean;
    error: string;
    startCurrentSession: () => Promise<void>;
    endCurrentSession: () => Promise<void>;
};

export const CanvasSessionContext =
    createContext<CanvasSessionContextValue | null>(null);

/**
 * Custom hook to access the CanvasSessionContext that owns state related
 * to the currently opened session in the canvas, and actions to start and end it.
 *
 * @throws Will throw an error if used outside a CanvasSessionProvider.
 * @returns The context value of the CanvasSessionContext.
 */
export function useCanvasSession() {
    const context = useContext(CanvasSessionContext);

    if (!context) {
        throw new Error(
            "useCanvasSession must be used inside CanvasSessionProvider.",
        );
    }

    return context;
}