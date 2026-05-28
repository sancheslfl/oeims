import { useEffect, useMemo, useState } from "react";
import type {
    EventResponse,
    OpenedSession,
    ParticipantResponse, ParticipantStatusResponse,
    SessionResponse,
} from "../types";
import { useAuth } from "../AuthContext";
import {
    endSession,
    getSessionEvents,
    getSessionParticipants,
    startSession,
} from "../api/sessions";
import {
    REALTIME_CHANNELS,
    REALTIME_EVENTS,
    useEventListener,
} from "../hooks/useEventListener";
import { StudentCard } from "./StudentCard";

const seats = Array.from({ length: 12 }, (_, index) => index + 1);
const maxEventsPerStudent = 10;

type ClassroomCanvasProps = {
    openedSession: OpenedSession | null;
};

type ParticipantsState = {
    sessionId: string;
    participants: ParticipantResponse[];
};

type EventsState = {
    sessionId: string;
    eventsByParticipantId: Record<string, EventResponse[]>;
};

type SelectedParticipantState = {
    sessionId: string;
    participantId: string;
};

export function ClassroomCanvas({ openedSession }: ClassroomCanvasProps) {
    const { auth } = useAuth();

    const [sessionOverride, setSessionOverride] = useState<SessionResponse | null>(null);
    const [participantsState, setParticipantsState] = useState<ParticipantsState | null>(null);
    const [eventsState, setEventsState] = useState<EventsState | null>(null);
    const [hoveredParticipant, setHoveredParticipant] =
        useState<SelectedParticipantState | null>(null);
    const [openedParticipant, setOpenedParticipant] =
        useState<SelectedParticipantState | null>(null);
    const [isStarting, setIsStarting] = useState(false);
    const [isEnding, setIsEnding] = useState(false);
    const [error, setError] = useState("");

    const exam = openedSession?.exam;
    const openedSessionData = openedSession?.session;

    const canUseSessionOverride =
        sessionOverride &&
        openedSessionData &&
        sessionOverride.id === openedSessionData.id &&
        openedSessionData.status !== "ENDED";

    const session = canUseSessionOverride ? sessionOverride : openedSessionData;
    const sessionId = session?.id;

    const participants =
        participantsState && participantsState.sessionId === sessionId
            ? participantsState.participants
            : [];

    const visibleParticipantId = getVisibleParticipantId(
        sessionId,
        openedParticipant,
        hoveredParticipant,
    );

    const canStartSession = session?.status === "PENDING";
    const canEndSession = session?.status === "ACTIVE";

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
                        participants: addParticipantIfMissing(
                            currentParticipants,
                            participant,
                        ),
                    };
                });
            },

            [REALTIME_EVENTS.ParticipantEventReceived]: (data: unknown) => {
                if (!sessionId) {
                    return;
                }

                const event = data as EventResponse;

                setEventsState((current) => {
                    const currentEventsByParticipantId =
                        current?.sessionId === sessionId
                            ? current.eventsByParticipantId
                            : {};

                    const currentParticipantEvents =
                        currentEventsByParticipantId[event.participantId] ?? [];

                    return {
                        sessionId,
                        eventsByParticipantId: {
                            ...currentEventsByParticipantId,
                            [event.participantId]: addEventIfMissing(
                                currentParticipantEvents,
                                event,
                            ),
                        },
                    };
                });
            },

            [REALTIME_EVENTS.ParticipantStatusUpdated]: (data: unknown) => {
                if (!sessionId) {
                    return;
                }

                const update = data as ParticipantStatusResponse;

                setParticipantsState((current) => {
                    if (!current || current.sessionId !== sessionId) {
                        return current;
                    }

                    return {
                        sessionId: current.sessionId,
                        participants: current.participants.map((participant) =>
                            participant.id === update.participantId
                                ? {
                                    ...participant,
                                    connectionStatus: update.connectionStatus,
                                }
                                : participant,
                        ),
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

    useEffect(() => {
        const token = auth?.token;

        if (!token || !sessionId || session?.status === "ENDED") {
            return;
        }

        let ignore = false;

        async function loadSessionEvents(token: string, sessionId: string) {
            try {
                const events = await getSessionEvents(sessionId, token);

                if (!ignore) {
                    setEventsState((current) => {
                        const currentEventsByParticipantId =
                            current?.sessionId === sessionId
                                ? current.eventsByParticipantId
                                : {};

                        return {
                            sessionId,
                            eventsByParticipantId: mergeEventsByParticipantId(
                                groupEventsByParticipantId(events),
                                currentEventsByParticipantId,
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

        void loadSessionEvents(token, sessionId);

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
            setSessionOverride(started);
        } catch (error) {
            setError(error instanceof Error ? error.message : "Unexpected error.");
        } finally {
            setIsStarting(false);
        }
    }

    async function handleEndSession() {
        if (!auth || !session) {
            return;
        }

        setError("");
        setIsEnding(true);

        try {
            const ended = await endSession(session.id, auth.token);
            setSessionOverride(ended);
            setHoveredParticipant(null);
            setOpenedParticipant(null);
        } catch (error) {
            if (error instanceof Error) {
                setError(error.message);
            }
        } finally {
            setIsEnding(false);
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

                <div className="flex gap-3">
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

                    {canEndSession && (
                        <button
                            type="button"
                            className="app-button"
                            disabled={isEnding}
                            onClick={() => void handleEndSession()}
                        >
                            {isEnding ? "Ending..." : "End session"}
                        </button>
                    )}
                </div>
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
                        const isCardVisible =
                            participant !== undefined &&
                            visibleParticipantId === participant.id;
                        const isPinned = isParticipantPinned(
                            participant,
                            sessionId,
                            openedParticipant,
                        );
                        const participantEvents = getParticipantEvents(
                            participant,
                            sessionId,
                            eventsState,
                        );

                        return (
                            <div
                                key={seat}
                                className="relative grid justify-items-center gap-1"
                                onMouseEnter={() => {
                                    if (!participant || !sessionId) {
                                        return;
                                    }

                                    setHoveredParticipant({
                                        sessionId,
                                        participantId: participant.id,
                                    });
                                }}
                                onMouseLeave={() => setHoveredParticipant(null)}
                            >
                                <button
                                    type="button"
                                    disabled={!participant}
                                    aria-haspopup={participant ? "dialog" : undefined}
                                    aria-expanded={
                                        participant ? isCardVisible : undefined
                                    }
                                    aria-label={
                                        participant
                                            ? `Open details for ${participant.email}`
                                            : `Empty seat ${seat}`
                                    }
                                    className={`grid h-12 w-32 place-items-center rounded-md border-2 border-isel-purple font-bold ${
                                        participant
                                            ? "bg-isel-pink text-isel-purple"
                                            : "bg-isel-purple/5"
                                    }`}
                                    onClick={() => {
                                        if (!participant || !sessionId) {
                                            return;
                                        }

                                        setOpenedParticipant((current) =>
                                            current?.sessionId === sessionId &&
                                            current.participantId === participant.id
                                                ? null
                                                : {
                                                    sessionId,
                                                    participantId: participant.id,
                                                },
                                        );
                                    }}
                                >
                                    {participant && getStudentNumber(participant.email)}
                                </button>

                                <div className="h-6 w-14 rounded-b-md border-2 border-t-0 border-isel-purple bg-isel-white" />

                                {participant && isCardVisible && (
                                    <StudentCard
                                        participant={participant}
                                        events={participantEvents}
                                        isPinned={isPinned}
                                        onClose={() => setOpenedParticipant(null)}
                                    />
                                )}
                            </div>
                        );
                    })}
                </div>
            </div>
        </div>
    );
}

function getVisibleParticipantId(
    sessionId: string | undefined,
    openedParticipant: SelectedParticipantState | null,
    hoveredParticipant: SelectedParticipantState | null,
) {
    if (!sessionId) {
        return null;
    }

    if (openedParticipant && openedParticipant.sessionId === sessionId) {
        return openedParticipant.participantId;
    }

    if (hoveredParticipant && hoveredParticipant.sessionId === sessionId) {
        return hoveredParticipant.participantId;
    }

    return null;
}

function isParticipantPinned(
    participant: ParticipantResponse | undefined,
    sessionId: string | undefined,
    openedParticipant: SelectedParticipantState | null,
) {
    if (!participant || !sessionId || !openedParticipant) {
        return false;
    }

    return (
        openedParticipant.sessionId === sessionId &&
        openedParticipant.participantId === participant.id
    );
}

function getParticipantEvents(
    participant: ParticipantResponse | undefined,
    sessionId: string | undefined,
    eventsState: EventsState | null,
) {
    if (!participant || !sessionId || !eventsState) {
        return [];
    }

    if (eventsState.sessionId !== sessionId) {
        return [];
    }

    return eventsState.eventsByParticipantId[participant.id] ?? [];
}

function addParticipantIfMissing(
    participants: ParticipantResponse[],
    participant: ParticipantResponse,
) {
    const participantsById = new Map(
        participants.map((participant) => [participant.id, participant]),
    );

    if (participantsById.has(participant.id)) {
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

function addEventIfMissing(events: EventResponse[], event: EventResponse) {
    const eventsById = new Map(events.map((event) => [event.id, event]));

    if (eventsById.has(event.id)) {
        return events;
    }

    return sortEventsByNewest([event, ...events]).slice(0, maxEventsPerStudent);
}

function groupEventsByParticipantId(events: EventResponse[]) {
    const eventsByParticipantId: Record<string, EventResponse[]> = {};

    for (const event of sortEventsByNewest(events)) {
        eventsByParticipantId[event.participantId] = [
            ...(eventsByParticipantId[event.participantId] ?? []),
            event,
        ].slice(0, maxEventsPerStudent);
    }

    return eventsByParticipantId;
}

function mergeEventsByParticipantId(
    primaryEventsByParticipantId: Record<string, EventResponse[]>,
    fallbackEventsByParticipantId: Record<string, EventResponse[]>,
) {
    const eventsByParticipantId = { ...primaryEventsByParticipantId };

    for (const [participantId, fallbackEvents] of Object.entries(
        fallbackEventsByParticipantId,
    )) {
        const primaryEvents = eventsByParticipantId[participantId] ?? [];

        eventsByParticipantId[participantId] = mergeEvents(
            primaryEvents,
            fallbackEvents,
        );
    }

    return eventsByParticipantId;
}

function mergeEvents(
    primaryEvents: EventResponse[],
    fallbackEvents: EventResponse[],
) {
    const eventsById = new Map<string, EventResponse>();

    for (const event of primaryEvents) {
        eventsById.set(event.id, event);
    }

    for (const event of fallbackEvents) {
        if (!eventsById.has(event.id)) {
            eventsById.set(event.id, event);
        }
    }

    return sortEventsByNewest(Array.from(eventsById.values())).slice(
        0,
        maxEventsPerStudent,
    );
}

function sortEventsByNewest(events: EventResponse[]) {
    return [...events].sort(
        (left, right) =>
            new Date(right.occurredAt).getTime() -
            new Date(left.occurredAt).getTime(),
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

function getStudentNumber(email: string) {
    const match = /^A(\d+)@alunos\.isel\.pt$/i.exec(email.trim());

    return match?.[1] ?? "—";
}