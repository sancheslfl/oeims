import { useEffect, useMemo, useReducer } from "react";
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

export type SeverityFlash = {
    animationKey: number;
    participantId: string;
    severity: EventResponse["severity"];
};

type CanvasRealtimeState = {
    participantsState: ParticipantsState | null;
    eventsState: EventsState | null;
    error: string;
    severityFlash: SeverityFlash | null;
};

type CanvasRealtimeAction =
    | { type: "participantJoined"; sessionId: string; participant: ParticipantResponse }
    | { type: "participantEventReceived"; sessionId: string; event: EventResponse }
    | { type: "participantStatusUpdated"; sessionId: string; update: ParticipantStatusResponse }
    | { type: "participantsLoaded"; sessionId: string; participants: ParticipantResponse[] }
    | { type: "eventsLoaded"; sessionId: string; events: EventResponse[] }
    | { type: "loadFailed"; error: string };

const initialCanvasRealtimeState: CanvasRealtimeState = {
    participantsState: null,
    eventsState: null,
    error: "",
    severityFlash: null,
};

function canvasRealtimeReducer(
    state: CanvasRealtimeState,
    action: CanvasRealtimeAction,
): CanvasRealtimeState {
    switch (action.type) {
        case "participantJoined": {
            const currentParticipants =
                state.participantsState?.sessionId === action.sessionId
                    ? state.participantsState.participants
                    : [];

            return {
                ...state,
                participantsState: {
                    sessionId: action.sessionId,
                    participants: addParticipantIfMissing(
                        currentParticipants,
                        action.participant,
                    ),
                },
            };
        }

        case "participantEventReceived": {
            const currentEventsByParticipantId =
                state.eventsState?.sessionId === action.sessionId
                    ? state.eventsState.eventsByParticipantId
                    : {};

            const currentParticipantEvents =
                currentEventsByParticipantId[action.event.participantId] ?? [];

            return {
                ...state,
                eventsState: {
                    sessionId: action.sessionId,
                    eventsByParticipantId: {
                        ...currentEventsByParticipantId,
                        [action.event.participantId]: addEventIfMissing(
                            currentParticipantEvents,
                            action.event,
                        ),
                    },
                },
                severityFlash: {
                    animationKey: state.severityFlash
                        ? state.severityFlash.animationKey + 1
                        : 1,
                    participantId: action.event.participantId,
                    severity: action.event.severity,
                },
            };
        }

        case "participantStatusUpdated": {
            if (!state.participantsState || state.participantsState.sessionId !== action.sessionId) {
                return state;
            }

            return {
                ...state,
                participantsState: {
                    sessionId: state.participantsState.sessionId,
                    participants: state.participantsState.participants.map((participant) =>
                        participant.id === action.update.participantId
                            ? {
                                ...participant,
                                connectionStatus: action.update.connectionStatus,
                            }
                            : participant,
                    ),
                },
            };
        }

        case "participantsLoaded": {
            const currentParticipants =
                state.participantsState?.sessionId === action.sessionId
                    ? state.participantsState.participants
                    : [];

            return {
                ...state,
                participantsState: {
                    sessionId: action.sessionId,
                    participants: mergeParticipants(
                        action.participants,
                        currentParticipants,
                    ),
                },
                error: "",
            };
        }

        case "eventsLoaded": {
            const currentEventsByParticipantId =
                state.eventsState?.sessionId === action.sessionId
                    ? state.eventsState.eventsByParticipantId
                    : {};

            return {
                ...state,
                eventsState: {
                    sessionId: action.sessionId,
                    eventsByParticipantId: mergeEventsByParticipantId(
                        groupEventsByParticipantId(action.events),
                        currentEventsByParticipantId,
                    ),
                },
                error: "",
            };
        }

        case "loadFailed":
            return { ...state, error: action.error };
    }
}

export function useCanvasRealtimeData(
    token: string | undefined,
    session: SessionResponse | undefined,
) {
    const [state, dispatch] = useReducer(
        canvasRealtimeReducer,
        initialCanvasRealtimeState,
    );

    const sessionId = session?.id;
    const sessionStatus = session?.status;

    const participants =
        state.participantsState && state.participantsState.sessionId === sessionId
            ? state.participantsState.participants
            : [];

    const eventsByParticipantId =
        state.eventsState && state.eventsState.sessionId === sessionId
            ? state.eventsState.eventsByParticipantId
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

                dispatch({
                    type: "participantJoined",
                    sessionId,
                    participant: data as ParticipantResponse,
                });
            },

            [REALTIME_EVENTS.ParticipantEventReceived]: (data: unknown) => {
                if (!sessionId) {
                    return;
                }

                dispatch({
                    type: "participantEventReceived",
                    sessionId,
                    event: data as EventResponse,
                });
            },

            [REALTIME_EVENTS.ParticipantStatusUpdated]: (data: unknown) => {
                if (!sessionId) {
                    return;
                }

                dispatch({
                    type: "participantStatusUpdated",
                    sessionId,
                    update: data as ParticipantStatusResponse,
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
                    dispatch({
                        type: "participantsLoaded",
                        sessionId,
                        participants: loadedParticipants,
                    });
                }
            } catch (error) {
                if (!ignore && error instanceof Error) {
                    dispatch({ type: "loadFailed", error: error.message });
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
                    dispatch({ type: "eventsLoaded", sessionId, events });
                }
            } catch (error) {
                if (!ignore && error instanceof Error) {
                    dispatch({ type: "loadFailed", error: error.message });
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
        error: state.error,
        severityFlash: state.severityFlash,
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
