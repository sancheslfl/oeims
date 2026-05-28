import { useEffect, useMemo, useState } from "react";
import type {EventResponse, ParticipantResponse, ParticipantStatusResponse, SessionResponse} from "../types";
import {REALTIME_CHANNELS, REALTIME_EVENTS, useEventListener} from "./useEventListener.ts";
import {getSessionEvents, getSessionParticipants} from "../api/sessions.ts";

const maxEventsPerStudent = 10;

type ParticipantsState = {
    sessionId: string;
    participants: ParticipantResponse[];
};

type EventsState = {
    sessionId: string;
    eventsByParticipantId: Record<string, EventResponse[]>;
};

export function useCanvasRealtimeData(
    token: string | undefined,
    session: SessionResponse | undefined,
) {
    const [participantsState, setParticipantsState] = useState<ParticipantsState | null>(null);
    const [eventsState, setEventsState] = useState<EventsState | null>(null);
    const [error, setError] = useState("");

    const sessionId = session?.id;
    const sessionStatus = session?.status;

    const participants =
        participantsState && participantsState.sessionId === sessionId
            ? participantsState.participants
            : [];

    const eventsByParticipantId =
        eventsState && eventsState.sessionId === sessionId
            ? eventsState.eventsByParticipantId
            : {};

    const eventId =
        sessionId && sessionStatus !== "ENDED"
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
        if (!token || !sessionId || sessionStatus === "ENDED") {
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
    }, [token, sessionId, sessionStatus]);

    useEffect(() => {
        if (!token || !sessionId || sessionStatus !== "ACTIVE") {
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
    }, [token, sessionId, sessionStatus]);

    return {
        participants,
        eventsByParticipantId,
        error,
    };
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

// SSE can receive participant.joined before REST finishes loading.
// This keeps both results and gives priority to the REST snapshot.
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