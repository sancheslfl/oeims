import { useEffect, useMemo, useState } from "react";
import type { OpenedSession, ParticipantResponse, SessionResponse } from "../types";
import { useAuth } from "../AuthContext";
import { getSessionParticipants, startSession } from "../api/sessions";
import {
    REALTIME_CHANNELS,
    REALTIME_EVENTS,
    useEventListener,
} from "../hooks/useEventListener";

const seats = Array.from({ length: 12 }, (_, index) => index + 1);

type ClassroomCanvasProps = {
    openedSession: OpenedSession | null;
};

type ParticipantsState = {
    sessionId: string;
    participants: ParticipantResponse[];
};

export function ClassroomCanvas({ openedSession }: ClassroomCanvasProps) {
    const { auth } = useAuth();

    const [startedSession, setStartedSession] = useState<SessionResponse | null>(null);
    const [participantsState, setParticipantsState] = useState<ParticipantsState | null>(null);
    const [isStarting, setIsStarting] = useState(false);
    const [error, setError] = useState("");

    const exam = openedSession?.exam;
    const openedSessionData = openedSession?.session;

    const canUseStartedSession =
        startedSession &&
        openedSessionData &&
        startedSession.id === openedSessionData.id &&
        openedSessionData.status === "PENDING";

    const session = canUseStartedSession ? startedSession : openedSessionData;
    const sessionId = session?.id;

    const participants =
        participantsState && participantsState.sessionId === sessionId
            ? participantsState.participants
            : [];

    const canStartSession = session?.status === "PENDING";

    const eventId =
        sessionId && session?.status !== "ENDED"
            ? REALTIME_CHANNELS.session(sessionId)
            : null;

    const sseMessageHandler = useMemo(
        () => ({
            [REALTIME_EVENTS.ParticipantJoined]: (data: unknown) => {
                if (!sessionId) {
                    return;
                }

                const participant = data as ParticipantResponse;

                setParticipantsState((current) => {
                    const currentParticipants =
                        current?.sessionId === sessionId ? current.participants : [];

                    return {
                        sessionId,
                        participants: addParticipantIfMissing(currentParticipants, participant),
                    };
                });
            },
        }),
        [sessionId],
    );

    useEventListener(eventId, sseMessageHandler);

    useEffect(() => {
        const token = auth?.token;

        if (!token || !sessionId || session?.status === "ENDED") {
            return;
        }

        let ignore = false;

        async function loadParticipants(token: string, sessionId: string) {
            try {
                const loadedParticipants = await getSessionParticipants(sessionId, token);

                if (!ignore) {
                    setParticipantsState((current) => {
                        const currentParticipants =
                            current?.sessionId === sessionId ? current.participants : [];

                        return {
                            sessionId,
                            participants: mergeParticipants(
                                loadedParticipants,
                                currentParticipants,
                            ),
                        };
                    });

                    setError("");
                }
            } catch (error) {
                if (!ignore && error instanceof Error) {
                    setError(error.message);
                }
            }
        }

        void loadParticipants(token, sessionId);

        return () => {
            ignore = true;
        };
    }, [auth?.token, sessionId, session?.status]);

    async function handleStartSession() {
        if (!auth || !session) {
            return;
        }

        setError("");
        setIsStarting(true);

        try {
            const started = await startSession(session.id, auth.token);
            setStartedSession(started);
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
                    aria-label="Classroom seats"
                    className="grid w-full max-w-xl grid-cols-4 gap-4"
                >
                    {seats.map((seat, index) => {
                        const participant = participants[index];

                        return (
                            <div key={seat} className="grid justify-items-center gap-1">
                                <div
                                    className={`grid h-12 w-32 place-items-center rounded-md border-2 border-isel-purple font-bold ${
                                        participant
                                            ? "bg-isel-pink text-isel-purple"
                                            : "bg-isel-purple/5"
                                    }`}
                                >
                                    {participant && getStudentNumber(participant.email)}
                                </div>

                                <div className="h-6 w-14 rounded-b-md border-2 border-t-0 border-isel-purple bg-isel-white" />
                            </div>
                        );
                    })}
                </div>
            </div>
        </div>
    );
}

function addParticipantIfMissing(
    participants: ParticipantResponse[],
    participant: ParticipantResponse,
) {
    const participantIds = new Set(participants.map((item) => item.id));

    if (participantIds.has(participant.id)) {
        return participants;
    }

    return [...participants, participant];
}

// SSE can receive event before REST finishes loading. REST result then can overwrite the SSE result,
// causing the joined participant to disappear from the list.
// This function ensures that all participants are included, giving priority to REST result.
function mergeParticipants(
    primaryParticipants: ParticipantResponse[],
    fallbackParticipants: ParticipantResponse[],
) {
    const participantsById = new Map<string, ParticipantResponse>();

    for (const participant of primaryParticipants) {
        participantsById.set(participant.id, participant);
    }

    for (const participant of fallbackParticipants) {
        if (!participantsById.has(participant.id)) {
            participantsById.set(participant.id, participant);
        }
    }

    return Array.from(participantsById.values());
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

function getStudentNumber(email: string) {
    const match = /^A(\d+)@alunos\.isel\.pt$/i.exec(email.trim());

    return match?.[1] ?? "—";
}