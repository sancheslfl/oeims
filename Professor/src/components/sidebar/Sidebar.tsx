import { useEffect, useMemo, useState } from "react";
import type { ExamResponse, OpenedSession, SessionResponse } from "../../types";
import { useAuth } from "../../AuthContext";
import { getExams } from "../../api/exams";
import { getActiveSessions } from "../../api/sessions";
import { CreateExamForm } from "./CreateExamForm";
import { SessionList } from "./SessionList";
import {
    REALTIME_CHANNELS,
    REALTIME_EVENTS,
    useEventListener,
} from "../../hooks/useEventListener";

type SidebarProps = {
    openedSession: OpenedSession | null;
    onOpenSession: (openedSession: OpenedSession) => void;
    onCloseSession: () => void;
};

type SidebarView = "exams" | "settings";

type SettingsMessage = {
    type: "success" | "error";
    text: string;
};

type Auth = ReturnType<typeof useAuth>["auth"];

function professorSettingsKey(professorId: string) {
    return `oeims:professor-settings:${professorId}`;
}

function normalizeAllowedEmailDomain(value: string) {
    return value.trim().replace(/^@+/, "").toLowerCase();
}

function loadAllowedEmailDomain(professorId: string | undefined) {
    if (!professorId) return "";

    try {
        return localStorage.getItem(professorSettingsKey(professorId)) ?? "";
    } catch {
        return "";
    }
}

function saveAllowedEmailDomain(
    professorId: string,
    allowedEmailDomain: string,
) {
    localStorage.setItem(
        professorSettingsKey(professorId),
        allowedEmailDomain,
    );
}

export function Sidebar(props: SidebarProps) {
    const { auth } = useAuth();

    return (
        <SidebarContent
            key={auth?.id ?? "anonymous"}
            auth={auth}
            {...props}
        />
    );
}

type SidebarContentProps = SidebarProps & {
    auth: Auth;
};

function SidebarContent({
                            auth,
                            openedSession,
                            onOpenSession,
                            onCloseSession,
                        }: SidebarContentProps) {
    const professorId = auth?.id;

    const [isOpen, setIsOpen] = useState(true);
    const [sidebarView, setSidebarView] = useState<SidebarView>("exams");
    const [allowedEmailDomain, setAllowedEmailDomain] = useState(() =>
        loadAllowedEmailDomain(professorId),
    );
    const [settingsMessage, setSettingsMessage] =
        useState<SettingsMessage | null>(null);
    const [exams, setExams] = useState<ExamResponse[]>([]);
    const [activeSessions, setActiveSessions] = useState<SessionResponse[]>([]);
    const [isLoadingExams, setIsLoadingExams] = useState(false);
    const [error, setError] = useState("");

    useEffect(() => {
        const token = auth?.token;

        if (!token) return;

        let ignore = false;

        async function loadData(token: string) {
            setError("");
            setIsLoadingExams(true);

            try {
                const [loadedExams, loadedActiveSessions] = await Promise.all([
                    getExams(token),
                    getActiveSessions(token),
                ]);

                if (!ignore) {
                    setExams(loadedExams);
                    setActiveSessions(loadedActiveSessions);
                }
            } catch (error) {
                if (!ignore && error instanceof Error) {
                    setError(error.message);
                }
            } finally {
                if (!ignore) {
                    setIsLoadingExams(false);
                }
            }
        }

        void loadData(token);

        return () => {
            ignore = true;
        };
    }, [auth?.token]);

    const sseHandlers = useMemo(
        () => ({
            [REALTIME_EVENTS.SessionCreated]: (data: unknown) => {
                const session = data as SessionResponse;

                setActiveSessions((current) =>
                    current.some((s) => s.id === session.id)
                        ? current
                        : [...current, session],
                );
            },

            [REALTIME_EVENTS.SessionStatusUpdated]: (data: unknown) => {
                const session = data as SessionResponse;

                setActiveSessions((current) => {
                    if (session.status === "ENDED") {
                        return current.filter((s) => s.id !== session.id);
                    }

                    return current.map((s) =>
                        s.id === session.id ? session : s,
                    );
                });

                if (openedSession?.session.id !== session.id) {
                    return;
                }

                if (session.status === "ENDED") {
                    onCloseSession();
                    return;
                }

                onOpenSession({ ...openedSession, session });
            },
        }),
        [openedSession, onOpenSession, onCloseSession],
    );

    useEventListener(auth ? REALTIME_CHANNELS.sessions : null, sseHandlers);

    function handleExamCreated(exam: ExamResponse) {
        setExams((current) => [exam, ...current]);
    }

    function handleAllowedEmailDomainChange(value: string) {
        const normalizedValue = normalizeAllowedEmailDomain(value);
        setAllowedEmailDomain(normalizedValue);

        if (!professorId) {
            setSettingsMessage({
                type: "error",
                text: "Login is required before saving settings.",
            });
            return;
        }

        try {
            saveAllowedEmailDomain(professorId, normalizedValue);

            setSettingsMessage(
                normalizedValue
                    ? {
                        type: "success",
                        text: "Settings saved.",
                    }
                    : {
                        type: "error",
                        text: "Allowed email domain is empty.",
                    },
            );
        } catch {
            setSettingsMessage({
                type: "error",
                text: "Could not save settings in this browser.",
            });
        }
    }

    function handleSidebarViewChange(view: SidebarView) {
        setSidebarView(view);
        setSettingsMessage(null);
    }

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
                        onChange={handleSidebarViewChange}
                    />

                    <div className="sidebar-scroll h-full min-h-0 overflow-y-auto">
                        <div className="grid gap-8 p-6">
                            {error && (
                                <p className="rounded-md border-2 border-isel-red bg-isel-pink px-3 py-2 text-sm font-semibold text-isel-purple">
                                    {error}
                                </p>
                            )}

                            {sidebarView === "exams" ? (
                                <>
                                    <CreateExamForm
                                        onExamCreated={handleExamCreated}
                                    />

                                    <SessionList
                                        exams={exams}
                                        activeSessions={activeSessions}
                                        isLoading={isLoadingExams}
                                        openedSession={openedSession}
                                        allowedEmailDomain={allowedEmailDomain}
                                        onOpenSession={onOpenSession}
                                    />
                                </>
                            ) : (
                                <SidebarSettings
                                    allowedEmailDomain={allowedEmailDomain}
                                    message={settingsMessage}
                                    onAllowedEmailDomainChange={
                                        handleAllowedEmailDomainChange
                                    }
                                />
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

type SidebarSettingsProps = {
    allowedEmailDomain: string;
    message: SettingsMessage | null;
    onAllowedEmailDomainChange: (value: string) => void;
};

function SidebarSettings({
                             allowedEmailDomain,
                             message,
                             onAllowedEmailDomainChange,
                         }: SidebarSettingsProps) {
    return (
        <section>
            <h2 className="app-section-title">Settings</h2>

            {message && (
                <p
                    aria-live="polite"
                    className={`mt-2 rounded-md border-2 px-3 py-2 text-sm font-semibold ${
                        message.type === "success"
                            ? "border-isel-purple bg-isel-purple/5 text-isel-purple"
                            : "border-isel-red bg-isel-pink text-isel-purple"
                    }`}
                >
                    {message.text}
                </p>
            )}

            <section className="mt-6 rounded-md border-2 border-isel-purple bg-isel-white p-4">
                <h3 className="text-sm font-bold uppercase tracking-wide text-isel-purple">
                    Sessions
                </h3>

                <label
                    htmlFor="allowed-email-domain"
                    className="mt-4 block text-xs font-bold uppercase tracking-wide text-isel-purple/70"
                >
                    Allowed Email Domain
                </label>

                <input
                    id="allowed-email-domain"
                    value={allowedEmailDomain}
                    onChange={(event) =>
                        onAllowedEmailDomainChange(event.currentTarget.value)
                    }
                    placeholder="isel.pt"
                    autoCapitalize="none"
                    autoCorrect="off"
                    spellCheck={false}
                    className="mt-1 w-full rounded-md border-2 border-isel-purple bg-isel-white px-3 py-2 text-sm font-semibold text-isel-purple outline-none placeholder:text-isel-purple/35 focus:border-isel-red"
                />

                <p className="mt-2 text-xs font-semibold text-isel-purple/60">
                    Students must use this email domain to join newly created
                    sessions.
                </p>
            </section>
        </section>
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