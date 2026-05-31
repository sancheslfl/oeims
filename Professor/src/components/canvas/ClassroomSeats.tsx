import { useRef, useState } from "react";
import type {
    EventResponse,
    ParticipantResponse,
    SessionResponse,
} from "../../types";
import { StudentCard } from "./StudentCard";
import type { SeverityFlash } from "../../hooks/useCanvasRealtimeData";

const seats = Array.from({ length: 12 }, (_, index) => index + 1);

type ClassroomSeatsProps = {
    sessionId: string | undefined;
    sessionStatus: SessionResponse["status"] | undefined;
    participants: ParticipantResponse[];
    eventsByParticipantId: Record<string, EventResponse[]>;
    severityFlash: SeverityFlash | null;
};

type PopoverElement = HTMLDivElement & {
    showPopover: (options?: { source?: HTMLElement }) => void;
    hidePopover: () => void;
};

export function ClassroomSeats({
                                   sessionId,
                                   sessionStatus,
                                   participants,
                                   eventsByParticipantId,
                                   severityFlash,
                               }: ClassroomSeatsProps) {
    const popoverRefs = useRef<Record<string, PopoverElement | null>>({});
    const closePreviewTimeoutRef = useRef<number | null>(null);

    const [pinnedParticipantId, setPinnedParticipantId] = useState<string | null>(null);

    const cardsEnabled = sessionStatus !== "ENDED";

    const severityRank = {
        INFO: 1,
        WARNING: 2,
        CRITICAL: 3,
    } satisfies Record<EventResponse["severity"], number>;

    const severityClassBySeverity = {
        INFO: "severity-info",
        WARNING: "severity-warning",
        CRITICAL: "severity-critical",
    } satisfies Record<EventResponse["severity"], string>;

    const tableClassBySeverity = {
        INFO: "severity-info severity-surface",
        WARNING: "severity-warning severity-surface",
        CRITICAL: "severity-critical severity-surface",
    } satisfies Record<EventResponse["severity"], string>;

    function showStudentCard(participant: ParticipantResponse, source: HTMLElement) {
        const popover = popoverRefs.current[participant.id];

        if (!popover) {
            return;
        }

        if (popover.matches(":popover-open")) {
            popover.hidePopover();
        }

        requestAnimationFrame(() => {
            popover.showPopover({ source });
        });
    }

    function hideStudentCard(participant: ParticipantResponse) {
        const popover = popoverRefs.current[participant.id];

        if (popover?.matches(":popover-open")) {
            popover.hidePopover();
        }
    }

    function cancelPreviewClose() {
        if (closePreviewTimeoutRef.current === null) {
            return;
        }

        window.clearTimeout(closePreviewTimeoutRef.current);
        closePreviewTimeoutRef.current = null;
    }

    function schedulePreviewClose(participant: ParticipantResponse) {
        if (pinnedParticipantId) {
            return;
        }

        cancelPreviewClose();

        closePreviewTimeoutRef.current = window.setTimeout(() => {
            hideStudentCard(participant);
            closePreviewTimeoutRef.current = null;
        }, 120);
    }

    return (
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

                    const participantEvents = participant
                        ? eventsByParticipantId[participant.id] ?? []
                        : [];

                    const tableSeverity = participantEvents.reduce<
                        EventResponse["severity"] | null
                    >((currentSeverity, event) => {
                        if (!currentSeverity) {
                            return event.severity;
                        }

                        return severityRank[event.severity] >
                        severityRank[currentSeverity]
                            ? event.severity
                            : currentSeverity;
                    }, null);

                    const tableClassName =
                        participant?.connectionStatus === "DISCONNECTED"
                            ? "border-gray-300 bg-gray-100"
                            : tableSeverity
                                ? tableClassBySeverity[tableSeverity]
                                : "border-isel-purple bg-isel-pink";

                    const pulse =
                        participant &&
                        severityFlash?.participantId === participant.id
                            ? severityFlash
                            : null;

                    return (
                        <div key={seat} className="grid justify-items-center gap-1">
                            <div className="relative">
                                {pulse && (
                                    <span
                                        key={pulse.animationKey}
                                        aria-hidden="true"
                                        className={`student-table-pulse ${severityClassBySeverity[pulse.severity]}`}
                                    />
                                )}

                                <button
                                    type="button"
                                    disabled={!participant || !cardsEnabled}
                                    aria-haspopup={participant ? "dialog" : undefined}
                                    aria-label={
                                        participant
                                            ? `Open details for ${participant.email}`
                                            : `Empty seat ${seat}`
                                    }
                                    className={`relative z-10 grid h-12 w-32 place-items-center rounded-md border-2 font-bold transition-colors duration-300 ${
                                        participant?.connectionStatus === "DISCONNECTED"
                                            ? "text-gray-400"
                                            : "text-isel-purple"
                                    } ${
                                        participant
                                            ? tableClassName
                                            : "border-isel-purple bg-isel-purple/5"
                                    }`}
                                    onMouseEnter={(event) => {
                                        if (
                                            !participant ||
                                            !sessionId ||
                                            !cardsEnabled ||
                                            pinnedParticipantId
                                        ) {
                                            return;
                                        }

                                        cancelPreviewClose();
                                        showStudentCard(participant, event.currentTarget);
                                    }}
                                    onMouseLeave={() => {
                                        if (!participant) {
                                            return;
                                        }

                                        schedulePreviewClose(participant);
                                    }}
                                    onClick={(event) => {
                                        if (!participant || !sessionId || !cardsEnabled) {
                                            return;
                                        }

                                        cancelPreviewClose();
                                        setPinnedParticipantId(participant.id);
                                        showStudentCard(participant, event.currentTarget);
                                    }}
                                >
                                    {participant && getStudentNumber(participant.email)}
                                </button>
                            </div>

                            <div className="h-6 w-14 rounded-b-md border-2 border-t-0 border-isel-purple bg-isel-white" />

                            {participant && (
                                <div
                                    ref={(element) => {
                                        popoverRefs.current[participant.id] =
                                            element as PopoverElement | null;
                                    }}
                                    popover="auto"
                                    role="dialog"
                                    aria-label={`Details for ${participant.email}`}
                                    className="m-0 w-80 max-w-[calc(100vw-1rem)] border-0 bg-transparent p-0 inset-auto [position-area:block-start_center]"
                                    onMouseEnter={cancelPreviewClose}
                                    onMouseLeave={() => {
                                        if (!pinnedParticipantId) {
                                            hideStudentCard(participant);
                                        }
                                    }}
                                    onToggle={(event) => {
                                        const toggleEvent = event.nativeEvent as Event & {
                                            newState?: string;
                                        };

                                        if (
                                            toggleEvent.newState === "closed" &&
                                            pinnedParticipantId === participant.id
                                        ) {
                                            setPinnedParticipantId(null);
                                        }
                                    }}
                                >
                                    <StudentCard
                                        participant={participant}
                                        events={participantEvents}
                                    />
                                </div>
                            )}
                        </div>
                    );
                })}
            </div>
        </div>
    );
}

function getStudentNumber(email: string) {
    const match = /^A(\d+)@alunos\.isel\.pt$/i.exec(email.trim());

    return match?.[1] ?? "—";
}