import { useEffect, useEffectEvent } from "react";
import { createEventSource } from "../api/utils.ts";

export const REALTIME_EVENTS = {
    ParticipantJoined: "participant.joined",
    ParticipantStatusUpdated: "participant.status.updated",
    ParticipantEventReceived: "participant.event.received",
    SessionCreated: "session.created",
    SessionStatusUpdated: "session.status.updated",
} as const;

export const REALTIME_CHANNELS = {
    session: (sessionId: string) => `session.${sessionId}`,
    sessions: "sessions",
};

type RealtimeEventName =
    (typeof REALTIME_EVENTS)[keyof typeof REALTIME_EVENTS];

type EventHandlers = Partial<
    Record<RealtimeEventName, (data: unknown) => void>
>;

const realtimeEventNames = Object.values(REALTIME_EVENTS);

export function useEventListener(
    eventId: string | null,
    handlers: EventHandlers,
) {
    const handleEvent = useEffectEvent(
        (eventName: RealtimeEventName, event: MessageEvent<string>) => {
            const handler = handlers[eventName];

            if (!handler) {
                return;
            }

            handler(JSON.parse(event.data));
        },
    );

    useEffect(() => {
        if (!eventId) {
            return;
        }

        const eventSource = createEventSource(eventId);
        const listeners: Array<[RealtimeEventName, EventListener]> = [];

        for (const eventName of realtimeEventNames) {
            const listener: EventListener = (event) => {
                handleEvent(eventName, event as MessageEvent<string>);
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
    }, [eventId]);
}