import type { EventResponse, ParticipantResponse } from "../../types";

type StudentCardProps = {
    participant: ParticipantResponse;
    events: EventResponse[];
};

const studentName = "";

export function StudentCard({ participant, events }: StudentCardProps) {
    return (
        <article
            role="dialog"
            aria-label={`Details for ${participant.email}`}
            className="flex max-h-full flex-col rounded-2xl border-2 border-isel-purple bg-isel-white p-3 text-left shadow-[0_0.75rem_2rem_rgba(95,20,55,0.22)]"
        >
            <StudentCardHeader participant={participant} />
            <EventList events={events} />
        </article>
    );
}

type StudentCardHeaderProps = {
    participant: ParticipantResponse;
};

function StudentCardHeader({ participant }: StudentCardHeaderProps) {
    return (
        <div className="flex shrink-0 items-center gap-2">
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
    );
}

type StudentAvatarProps = {
    email: string;
};

function StudentAvatar({ email }: StudentAvatarProps) {
    return (
        <div className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-isel-purple text-xs font-bold text-isel-white">
            {getStudentInitials(email)}
        </div>
    );
}

type EventListProps = {
    events: EventResponse[];
};

function EventList({ events }: EventListProps) {
    return (
        <section className="mt-3 flex min-h-0 flex-1 flex-col">
            <h2 className="mb-1 shrink-0 text-xs font-bold text-isel-purple">
                Events
            </h2>

            {events.length === 0 ? (
                <p className="py-2 text-xs font-semibold text-isel-purple/60">
                    No events received yet.
                </p>
            ) : (
                <div className="max-h-52 min-h-0 overflow-y-auto overflow-x-hidden overscroll-contain pr-1 scrollbar-none [-ms-overflow-style:none] [&::-webkit-scrollbar]:hidden">
                    {events.map((event) => (
                        <EventItem key={event.id} event={event} />
                    ))}
                </div>
            )}
        </section>
    );
}

type EventItemProps = {
    event: EventResponse;
};

function EventItem({ event }: EventItemProps) {
    return (
        <div className="flex min-w-0 gap-2 border-b border-isel-brown/10 py-1.5 last:border-b-0">
            <SeverityDot severity={event.severity} />

            <div className="min-w-0 flex-1 overflow-x-hidden">
                <div className="flex min-w-0 items-baseline justify-between gap-2">
                    <p className="min-w-0 text-xs font-semibold leading-snug text-isel-purple wrap-anywhere">
                        {event.monitorName}
                    </p>

                    <time className="shrink-0 text-[0.62rem] font-semibold text-isel-purple/45">
                        {formatEventTime(event.occurredAt)}
                    </time>
                </div>

                <p className="mt-0.5 text-[0.7rem] font-medium leading-snug text-isel-purple/75 wrap-anywhere">
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
    const severityClassBySeverity = {
        INFO: "severity-info",
        WARNING: "severity-warning",
        CRITICAL: "severity-critical",
    } satisfies Record<EventResponse["severity"], string>;

    const severityClass = severityClassBySeverity[severity];

    return (
        <span
            className={`mt-1.5 h-2 w-2 shrink-0 rounded-full bg-(--severity-color) ${severityClass}`}
            aria-label={severity}
        />
    );
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