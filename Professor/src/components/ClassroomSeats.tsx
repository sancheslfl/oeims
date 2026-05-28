import { useState } from "react";
import type {
    EventResponse,
    ParticipantResponse,
    SessionResponse,
} from "../types";
import { StudentCard } from "./StudentCard";

const seats = Array.from({ length: 12 }, (_, index) => index + 1);

type ClassroomSeatsProps = {
    sessionId: string | undefined;
    sessionStatus: SessionResponse["status"] | undefined;
    participants: ParticipantResponse[];
    eventsByParticipantId: Record<string, EventResponse[]>;
};

type SelectedParticipantState = {
    sessionId: string;
    participantId: string;
};

export function ClassroomSeats({
                                   sessionId,
                                   sessionStatus,
                                   participants,
                                   eventsByParticipantId,
                               }: ClassroomSeatsProps) {
    const [hoveredParticipant, setHoveredParticipant] =
        useState<SelectedParticipantState | null>(null);
    const [openedParticipant, setOpenedParticipant] =
        useState<SelectedParticipantState | null>(null);

    const cardsEnabled = sessionStatus !== "ENDED";

    const visibleParticipantId = cardsEnabled
        ? getVisibleParticipantId(sessionId, openedParticipant, hoveredParticipant)
        : null;

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

                    const isCardVisible =
                        participant !== undefined &&
                        visibleParticipantId === participant.id;

                    const isPinned = isParticipantPinned(
                        participant,
                        sessionId,
                        openedParticipant,
                    );

                    const participantEvents = participant
                        ? eventsByParticipantId[participant.id] ?? []
                        : [];

                    return (
                        <div
                            key={seat}
                            className="relative grid justify-items-center gap-1"
                            onMouseEnter={() => {
                                if (!participant || !sessionId || !cardsEnabled) {
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
                                disabled={!participant || !cardsEnabled}
                                aria-haspopup={participant ? "dialog" : undefined}
                                aria-expanded={participant ? isCardVisible : undefined}
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
                                    if (!participant || !sessionId || !cardsEnabled) {
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

function getStudentNumber(email: string) {
    const match = /^A(\d+)@alunos\.isel\.pt$/i.exec(email.trim());

    return match?.[1] ?? "—";
}