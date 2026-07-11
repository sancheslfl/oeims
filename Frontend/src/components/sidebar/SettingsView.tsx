import { useState } from "react";
import { useAuth } from "../../auth.ts";
import {
    loadAllowedEmailDomain,
    saveAllowedEmailDomain,
} from "../../localStorage.ts";

type SettingsMessage = {
    type: "success" | "error";
    text: string;
};

function normalizeAllowedEmailDomain(value: string) {
    return value.trim().replace(/^@+/, "").toLowerCase();
}

export function SettingsView() {
    const { auth } = useAuth();
    const professorId = auth?.id;

    const [allowedEmailDomain, setAllowedEmailDomain] = useState(
        () => loadAllowedEmailDomain(professorId) ?? "",
    );
    const [message, setMessage] = useState<SettingsMessage | null>(null);

    function handleAllowedEmailDomainChange(value: string) {
        const normalizedValue = normalizeAllowedEmailDomain(value);
        setAllowedEmailDomain(normalizedValue);

        if (!professorId) {
            setMessage({
                type: "error",
                text: "Login is required before saving settings.",
            });
            return;
        }

        try {
            saveAllowedEmailDomain(professorId, normalizedValue);

            setMessage(
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
            setMessage({
                type: "error",
                text: "Could not save settings in this browser.",
            });
        }
    }

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
                        handleAllowedEmailDomainChange(
                            event.currentTarget.value,
                        )
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
