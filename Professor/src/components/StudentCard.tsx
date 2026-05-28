import type { EventResponse, ParticipantResponse } from "../types";

type StudentCardProps = {
    participant: ParticipantResponse;
    events: EventResponse[];
    isPinned: boolean;
    onClose: () => void;
};

const studentName = "";

export function StudentCard({
                                participant,
                                events,
                                isPinned,
                                onClose,
                            }: StudentCardProps) {
    return (
        <article
            role="dialog"
    aria-label={`Details for ${participant.email}`}
    className="absolute left-1/2 top-16 z-20 w-80 -translate-x-1/2 rounded-2xl border-2 border-isel-purple bg-isel-white p-4 text-left shadow-[0_0.75rem_2rem_rgba(95,20,55,0.18)]"
    >
    <div className="flex items-start justify-between gap-3">
    <div className="flex min-w-0 items-center gap-3">
    <div className="grid h-12 w-12 shrink-0 place-items-center rounded-full bg-isel-purple font-bold text-isel-white">
        {getStudentInitials(participant.email)}
    </div>

    <div className="min-w-0">
    <p className="min-h-5 font-bold text-isel-purple">
        {studentName}
        </p>

        <p className="truncate text-sm font-semibold text-isel-red">
        {participant.email}
        </p>
        </div>
        </div>

    {isPinned && (
        <button
            type="button"
        className="text-sm font-bold text-isel-red"
        onClick={onClose}
            >
            Close
            </button>
    )}
    </div>

    <div className="mt-4 grid gap-2">
    <h2 className="text-sm font-bold text-isel-purple">Events</h2>

    {events.length === 0 ? (
        <p className="rounded-md border-2 border-isel-purple/20 px-3 py-2 text-sm font-semibold text-isel-purple/70">
            No events received yet.
    </p>
    ) : (
        events.map((event, index) => (
            <EventItem
                key={event.id}
        event={event}
        isMostRecent={index === 0}
        />
    ))
    )}
    </div>
    </article>
);
}

type EventItemProps = {
    event: EventResponse;
    isMostRecent: boolean;
};

function EventItem({ event, isMostRecent }: EventItemProps) {
    return (
        <div
            className={`flex gap-3 rounded-md border-2 px-3 py-2 ${
            isMostRecent
                ? "border-isel-purple bg-isel-pink"
                : "border-isel-purple/20 bg-isel-white"
        }`}
>
    <div
        className={`mt-1 h-3 w-3 shrink-0 rounded-full ${getSeverityColorClass(
        event.severity,
    )}`}
    aria-label={event.severity}
    />

    <div className="min-w-0">
    <div className="flex items-start justify-between gap-2">
    <p className="font-bold text-isel-purple">
        {event.monitorName}
        </p>

        <time className="shrink-0 text-xs font-semibold text-isel-purple/60">
        {formatEventTime(event.occurredAt)}
    </time>
    </div>

    <p className="mt-1 text-sm font-semibold text-isel-purple/80">
        {event.message}
        </p>
        </div>
        </div>
);
}

function getSeverityColorClass(severity: EventResponse["severity"]) {
    switch (severity) {
        case "INFO":
            return "bg-isel-purple/30";
        case "WARNING":
            return "bg-yellow-400";
        case "CRITICAL":
            return "bg-red-600";
    }
}

function getStudentInitials(email: string) {
    return email.trim().slice(0, 2).toUpperCase();
}

function formatEventTime(value: string) {
    return new Intl.DateTimeFormat(undefined, {
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
    }).format(new Date(value));
}