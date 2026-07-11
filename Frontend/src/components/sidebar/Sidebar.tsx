import { useState } from "react";
import { useAuth } from "../../auth.ts";
import type { OpenedSession } from "../../types";
import { ExamsView } from "./ExamsView";
import { SettingsView } from "./SettingsView";

type SidebarProps = {
    openedSession: OpenedSession | null;
    onOpenSession: (openedSession: OpenedSession) => void;
    onCloseSession: () => void;
};

type SidebarView = "exams" | "settings";

export function Sidebar(props: SidebarProps) {
    const { auth } = useAuth();

    if (!auth) {
        return null;
    }

    return <SidebarContent key={auth.id} {...props} />;
}

function SidebarContent({
                            openedSession,
                            onOpenSession,
                            onCloseSession,
                        }: SidebarProps) {
    const [isOpen, setIsOpen] = useState(true);
    const [sidebarView, setSidebarView] = useState<SidebarView>("exams");

    return (
        <aside
            className={`relative h-full min-h-0 shrink-0 overflow-visible border-r-2 border-isel-purple bg-isel-white transition-[width] duration-150 ${
                isOpen ? "w-80" : "w-14"
            }`}
        >
            <button
                type="button"
                onClick={() => setIsOpen((value) => !value)}
                aria-label={isOpen ? "Collapse sidebar" : "Expand sidebar"}
                className="absolute top-4 -right-4 z-10 grid size-8 place-items-center rounded-full border-2 border-isel-purple bg-isel-white font-bold text-isel-purple shadow-sm hover:bg-isel-pink"
            >
                {isOpen ? "‹" : "›"}
            </button>

            {isOpen && (
                <div className="grid h-full min-h-0 grid-rows-[auto_1fr]">
                    <div className="grid grid-cols-2 border-b-2 border-isel-purple">
                        <button
                            type="button"
                            onClick={() => setSidebarView("exams")}
                            className={`py-3 text-sm font-bold ${
                                sidebarView === "exams"
                                    ? "bg-isel-red text-isel-white"
                                    : "bg-isel-white text-isel-purple"
                            }`}
                        >
                            Exams
                        </button>
                        <button
                            type="button"
                            onClick={() => setSidebarView("settings")}
                            className={`py-3 text-sm font-bold ${
                                sidebarView === "settings"
                                    ? "bg-isel-red text-isel-white"
                                    : "bg-isel-white text-isel-purple"
                            }`}
                        >
                            Settings
                        </button>
                    </div>

                    <div className="min-h-0 overflow-y-auto p-4">
                        {sidebarView === "exams" ? (
                            <ExamsView
                                openedSession={openedSession}
                                onOpenSession={onOpenSession}
                                onCloseSession={onCloseSession}
                            />
                        ) : (
                            <SettingsView />
                        )}
                    </div>
                </div>
            )}
        </aside>
    );
}
