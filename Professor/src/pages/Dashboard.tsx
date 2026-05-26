import { useEffect, useState } from "react";
import type { OpenedSession } from "../types";
import { useAuth } from "../AuthContext";
import { getCurrentSession } from "../api/sessions";
import { getExam } from "../api/exams";
import { clearLastSessionId, saveLastSessionId } from "../localStorage";
import { TopBar } from "../components/TopBar";
import { Sidebar } from "../components/Sidebar";
import { ClassroomCanvas } from "../components/ClassroomCanvas";

export function Dashboard() {
    const { auth } = useAuth();

    const [openedSession, setOpenedSession] = useState<OpenedSession | null>(null);

    useEffect(() => {
        if (!auth) {
            return;
        }

        let ignore = false;
        const token = auth.token;
        const userId = auth.id;

        async function restoreCurrentSession() {
            try {
                const session = await getCurrentSession(token);

                if (!session) {
                    clearLastSessionId(userId);
                    return;
                }

                const exam = await getExam(session.examId, token);

                if (!ignore) {
                    saveLastSessionId(userId, session.id);
                    setOpenedSession({ exam, session });
                }
            } catch {
                if (!ignore) {
                    clearLastSessionId(userId);
                }
            }
        }

        void restoreCurrentSession();

        return () => {
            ignore = true;
        };
    }, [auth]);

    return (
        <div className="grid h-dvh grid-rows-[auto_1fr] overflow-hidden bg-isel-white text-isel-purple">
            <TopBar />

            <main className="flex min-h-0 overflow-hidden">
                <Sidebar
                    openedSession={openedSession}
                    onOpenSession={setOpenedSession}
                />

                <section
                    aria-label="Classroom layout"
                    className="flex min-w-0 flex-1 items-center justify-center overflow-hidden bg-linear-to-br from-isel-white from-60% to-isel-pink p-6"
                >
                    <ClassroomCanvas openedSession={openedSession} />
                </section>
            </main>
        </div>
    );
}