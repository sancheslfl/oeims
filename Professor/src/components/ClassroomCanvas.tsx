import { useState } from "react";
import type { OpenedSession, SessionResponse } from "../types";
import { useAuth } from "../AuthContext";
import { startSession } from "../api/sessions";

const seats = Array.from({ length: 12 }, (_, index) => index + 1);

type ClassroomCanvasProps = {
    openedSession: OpenedSession | null;
};

export function ClassroomCanvas({ openedSession }: ClassroomCanvasProps) {
    const { auth } = useAuth();

    const [startedSession, setStartedSession] = useState<SessionResponse | null>(null);
    const [isStarting, setIsStarting] = useState(false);
    const [error, setError] = useState("");

    const exam = openedSession?.exam;
    const session = startedSession ?? openedSession?.session;
    const canStartSession = session?.status === "PENDING";

    async function handleStartSession() {
        if (!auth || !openedSession) {
            return;
        }

        setError("");
        setIsStarting(true);

        try {
            const session = await startSession(
                openedSession.session.id,
                auth.token,
            );

            setStartedSession(session);
        } catch (error) {
            if (error instanceof Error) {
                setError(error.message);
            }
        } finally {
            setIsStarting(false);
        }
    }

    return (
        <div className="grid min-h-104 w-full min-w-3xl max-w-5xl gap-8 rounded-[3.5rem] border-3 border-isel-purple bg-isel-white p-8 shadow-[0_0.75rem_2rem_rgba(95,20,55,0.12)]">
            <div className="flex items-start justify-between gap-4">
                <div>
                    <h1 className="m-0 text-3xl font-bold text-isel-purple">
                        {exam ? exam.title : "Classroom"}
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

                {canStartSession && (
                    <button
                        type="button"
                        className="app-button"
                        disabled={isStarting}
                        onClick={() => void handleStartSession()}
                    >
                        {isStarting ? "Starting..." : "Begin session"}
                    </button>
                )}
            </div>

            <div className="grid place-items-center gap-8">
                <div className="w-full max-w-64 rounded-md border-2 border-isel-purple p-3 text-center font-bold">
                    Professor
                </div>

                <div
                    aria-label="Empty classroom seats"
                    className="grid w-full max-w-xl grid-cols-4 gap-4"
                >
                    {seats.map((seat) => (
                        <div key={seat} className="grid justify-items-center gap-1">
                            <div className="h-12 w-32 rounded-md border-2 border-isel-purple bg-isel-purple/5" />
                            <div className="h-6 w-14 rounded-b-md border-2 border-t-0 border-isel-purple bg-isel-white" />
                        </div>
                    ))}
                </div>
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