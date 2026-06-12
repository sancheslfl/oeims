import { useEffect} from "react";
import {createEventSource} from "../api/utils.ts";

type EventHandlers = Record<string, (data: unknown) => void>;

export const REALTIME_EVENTS = {
    ParticipantJoined: "participant.joined",
    ParticipantStatusUpdated: "participant.status.updated",
    ParticipantEventReceived: "participant.event.received",
    SessionCreated: "session.created",
    SessionStatusUpdated: "session.status.updated",
};

export const REALTIME_CHANNELS = {
    session: (sessionId: string) => `session.${sessionId}`,
    sessions: "sessions",
};

export function useEventListener(
    eventId: string | null,
    handlers: EventHandlers,
) {
    useEffect(() => {
        if (!eventId) {
            return;
        }

        const eventSource = createEventSource(eventId);
        const listeners: Array<[string, EventListener]> = [];

        for (const [eventName, handler] of Object.entries(handlers)) {
            const listener: EventListener = (event) => {
                const message = event as MessageEvent<string>;
                handler(JSON.parse(message.data));
            };

            eventSource.addEventListener(eventName, listener);
            listeners.push([eventName, listener]);
        }

        return () => {
            for (const [eventName, listener] of listeners) {
                eventSource.removeEventListener(eventName, listener);
            }

            eventSource.close();
        };
    }, [eventId, handlers]);
}