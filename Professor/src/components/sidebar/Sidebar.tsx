import { useState } from "react";
import { useAuth } from "../../AuthContext";
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
                onClick={() => setIsOpen((current) => !current)}
                aria-expanded={isOpen}
                className="absolute top-1/2 -right-5 z-20 grid h-12 w-8 -translate-y-1/2 place-items-center rounded-md border-2 border-isel-purple bg-isel-white text-2xl font-bold text-isel-purple"
            >
                {isOpen ? "‹" : "›"}
            </button>

            {isOpen ? (
                <>
                    <SidebarViewToggle
                        value={sidebarView}
                        onChange={setSidebarView}
                    />

                    <div className="sidebar-scroll h-full min-h-0 overflow-y-auto">
                        <div className="grid gap-8 p-6">
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
                </>
            ) : (
                <span className="absolute top-6 left-1/2 -translate-x-1/2 text-xs font-bold uppercase tracking-widest text-isel-purple [writing-mode:vertical-rl]">
                    Menu
                </span>
            )}
        </aside>
    );
}

type SidebarViewToggleProps = {
    value: SidebarView;
    onChange: (value: SidebarView) => void;
};

function SidebarViewToggle({ value, onChange }: SidebarViewToggleProps) {
    const isSettings = value === "settings";

    return (
        <div
            key={value}
            className="sidebar-view-toggle absolute top-4.5 right-6 z-10 rounded-md border-2 border-isel-purple bg-isel-white p-0.5 shadow-sm"
        >
            <div className="relative grid grid-cols-2">
                <span
                    aria-hidden="true"
                    className={`absolute top-0 h-full w-7 rounded-sm bg-isel-red transition-transform duration-150 ${
                        isSettings ? "translate-x-7" : "translate-x-0"
                    }`}
                />

                <button
                    type="button"
                    aria-label="Show exams and sessions"
                    aria-pressed={!isSettings}
                    onClick={() => onChange("exams")}
                    className={`relative grid h-7 w-7 place-items-center rounded-sm transition-colors ${
                        isSettings ? "text-isel-purple" : "text-isel-white"
                    }`}
                >
                    <ExamsIcon />
                </button>

                <button
                    type="button"
                    aria-label="Show settings"
                    aria-pressed={isSettings}
                    onClick={() => onChange("settings")}
                    className={`relative grid h-7 w-7 place-items-center rounded-sm transition-colors ${
                        isSettings ? "text-isel-white" : "text-isel-purple"
                    }`}
                >
                    <SettingsIcon />
                </button>
            </div>
        </div>
    );
}

function ExamsIcon() {
    return (
        <svg
            aria-hidden="true"
            viewBox="0 0 24 24"
            className="h-4 w-4"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
        >
            <path d="M6 3h9l3 3v15H6z" />
            <path d="M14 3v4h4" />
            <path d="M9 11h6" />
            <path d="M9 15h6" />
        </svg>
    );
}

function SettingsIcon() {
    return (
        <svg
            aria-hidden="true"
            viewBox="0 0 24 24"
            className="h-4 w-4"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
        >
            <path d="M12 15.5A3.5 3.5 0 1 0 12 8a3.5 3.5 0 0 0 0 7.5z" />
            <path d="M19.4 15a1.7 1.7 0 0 0 .34 1.88l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.7 1.7 0 0 0-1.88-.34 1.7 1.7 0 0 0-1.03 1.56V21a2 2 0 1 1-4 0v-.09A1.7 1.7 0 0 0 8.97 19.35a1.7 1.7 0 0 0-1.88.34l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.7 1.7 0 0 0 4.6 15a1.7 1.7 0 0 0-1.56-1.03H3a2 2 0 1 1 0-4h.09A1.7 1.7 0 0 0 4.65 8.94a1.7 1.7 0 0 0-.34-1.88l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.7 1.7 0 0 0 1.88.34A1.7 1.7 0 0 0 10.03 3H10a2 2 0 1 1 4 0v.09a1.7 1.7 0 0 0 1.03 1.56 1.7 1.7 0 0 0 1.88-.34l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.7 1.7 0 0 0-.34 1.88A1.7 1.7 0 0 0 21 10.03H21a2 2 0 1 1 0 4h-.09A1.7 1.7 0 0 0 19.4 15z" />
        </svg>
    );
}