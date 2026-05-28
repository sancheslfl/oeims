import type { EventResponse, ParticipantResponse } from "../../types";

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
            className="absolute left-1/2 top-16 z-20 w-80 -translate-x-1/2 rounded-2xl border-2 border-isel-purple bg-isel-white p-3 text-left shadow-[0_0.75rem_2rem_rgba(95,20,55,0.18)]"
        >
            <StudentCardHeader
                participant={participant}
                isPinned={isPinned}
                onClose={onClose}
            />

            <EventList events={events} />
        </article>
    );
}

type StudentCardHeaderProps = {
    participant: ParticipantResponse;
    isPinned: boolean;
    onClose: () => void;
};

function StudentCardHeader({
                               participant,
                               isPinned,
                               onClose,
                           }: StudentCardHeaderProps) {
    return (
        <div className="flex items-start justify-between gap-3">
            <div className="flex min-w-0 items-center gap-2">
                <StudentAvatar email={participant.email} />

                <div className="min-w-0">
                    {studentName && (
                        <p className="truncate text-sm font-bold text-isel-purple">
                            {studentName}
                        </p>
                    )}

                    <p className="truncate text-xs font-semibold text-isel-red">
                        {participant.email}
                    </p>
                </div>
            </div>

            {isPinned && (
                <button
                    type="button"
                    className="text-xs font-bold text-isel-red"
                    onClick={onClose}
                >
                    Close
                </button>
            )}
        </div>
    );
}

type StudentAvatarProps = {
    email: string;
};

function StudentAvatar({ email }: StudentAvatarProps) {
    return (
        <div className="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-isel-purple text-sm font-bold text-isel-white">
            {getStudentInitials(email)}
        </div>
    );
}

type EventListProps = {
    events: EventResponse[];
};

function EventList({ events }: EventListProps) {
    return (
        <section className="mt-3">
            <h2 className="mb-1 text-xs font-bold text-isel-purple">Events</h2>

            {events.length === 0 ? (
                <p className="py-2 text-xs font-semibold text-isel-purple/60">
                    No events received yet.
                </p>
            ) : (
                <div className="max-h-64 overflow-y-auto pr-1">
                    {events.map((event, index) => (
                        <EventItem
                            key={event.id}
                            event={event}
                            isMostRecent={index === 0}
                        />
                    ))}
                </div>
            )}
        </section>
    );
}

type EventItemProps = {
    event: EventResponse;
    isMostRecent: boolean;
};

function EventItem({ event, isMostRecent }: EventItemProps) {
    return (
        <div className="flex gap-2 border-b border-isel-purple/10 py-1.5 last:border-b-0">
            <SeverityDot severity={event.severity} />

            <div className="min-w-0 flex-1">
                <div className="flex items-baseline justify-between gap-2">
                    <p
                        className={
                            isMostRecent
                                ? "truncate text-xs font-bold text-isel-purple"
                                : "truncate text-[0.68rem] font-bold text-isel-purple/75"
                        }
                    >
                        {event.monitorName}
                    </p>

                    <time className="shrink-0 text-[0.62rem] font-semibold text-isel-purple/45">
                        {formatEventTime(event.occurredAt)}
                    </time>
                </div>

                <p
                    className={
                        isMostRecent
                            ? "mt-0.5 text-xs font-semibold leading-snug text-isel-purple/85"
                            : "mt-0.5 text-[0.68rem] font-medium leading-snug text-isel-purple/60"
                    }
                >
                    {event.message}
                </p>
            </div>
        </div>
    );
}

type SeverityDotProps = {
    severity: EventResponse["severity"];
};

function SeverityDot({ severity }: SeverityDotProps) {
    return (
        <span
            className={`mt-1.5 h-2 w-2 shrink-0 rounded-full ${getSeverityColorClass(
                severity,
            )}`}
            aria-label={severity}
        />
    );
}

function getSeverityColorClass(severity: EventResponse["severity"]) {
    switch (severity) {
        case "INFO":
            return "bg-isel-purple/25";
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