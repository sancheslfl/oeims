import { useState } from "react";
import type { OpenedSession } from "../types";

import { ClassroomCanvas } from "../components/ClassroomCanvas";
import {TopBar} from "../components/TopBar.tsx";
import {Sidebar} from "../components/Sidebar.tsx";

export function Dashboard() {
    const [openedSession, setOpenedSession] =
        useState<OpenedSession | null>(null);

    return (
        <div className="grid h-dvh grid-rows-[auto_1fr] overflow-hidden bg-isel-white text-isel-purple">
            <TopBar />

            <main className="flex min-h-0 overflow-hidden">
                <Sidebar onOpenSession={setOpenedSession} />

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