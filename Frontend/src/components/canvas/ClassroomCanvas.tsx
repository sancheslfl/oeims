import type { OpenedSession } from "../../types";
import {ClassroomSeats} from "./ClassroomSeats.tsx";
import {useCanvasRealtimeData} from "../../hooks/useCanvasRealtimeData.ts";
import {CanvasHeader} from "./CanvasHeader.tsx";
import {CanvasSessionProvider} from "./CanvasSessionProvider.tsx";
import {useCanvasSession} from "./CanvasSessionContext.ts";
import {useAuth} from "../../auth.ts";

type ClassroomCanvasProps = {
    openedSession: OpenedSession | null;
};

type ClassroomCanvasContentProps = {
    title: string;
};

export function ClassroomCanvas({ openedSession }: ClassroomCanvasProps) {
    return (
        <CanvasSessionProvider openedSession={openedSession?.session}>
            <ClassroomCanvasContent title={openedSession?.exam.title ?? "Classroom"} />
        </CanvasSessionProvider>
    );
}

function ClassroomCanvasContent({ title }: ClassroomCanvasContentProps) {
    const { auth } = useAuth();
    const { session } = useCanvasSession();

    const {
        participants,
        eventsByParticipantId,
        severityFlash,
        error: realtimeError,
    } = useCanvasRealtimeData(auth?.token, session);

    return (
        <div className="grid min-h-104 w-full min-w-3xl max-w-5xl gap-8 rounded-[3.5rem] border-3 border-isel-purple bg-isel-white p-8 shadow-[0_0.75rem_2rem_rgba(95,20,55,0.12)]">
            <CanvasHeader
                title={title}
                realtimeError={realtimeError}
            />

            <ClassroomSeats
                sessionId={session?.id}
                sessionStatus={session?.status}
                participants={participants}
                eventsByParticipantId={eventsByParticipantId}
                severityFlash={severityFlash}
            />
        </div>
    );
}