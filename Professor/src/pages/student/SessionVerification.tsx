import { type SubmitEventHandler, useState } from "react";
import type {StudentAuth} from "../../types";
import {verifySessionParticipant} from "../../api/sessions.ts";
import {saveStudentAuth} from "../../localStorage.ts";

const SESSION_CODE_QUERY_PARAM = "code";

function getSessionCodeFromUrl() {
    return (
        new URLSearchParams(window.location.search).get(
            SESSION_CODE_QUERY_PARAM,
        ) ?? ""
    );
}

export function SessionVerification() {
    const [sessionCode] = useState(getSessionCodeFromUrl);
    const [email, setEmail] = useState("");
    const [studentAuth, setStudentAuth] = useState<StudentAuth | null>(null);
    const [isVerifying, setIsVerifying] = useState(false);
    const [error, setError] = useState("");

    const canVerify =
        Boolean(sessionCode) && Boolean(email.trim()) && !isVerifying;

    const handleSubmit: SubmitEventHandler<HTMLFormElement> = async (event) => {
        event.preventDefault();

        if (!canVerify) return;

        setError("");
        setIsVerifying(true);

        try {
            const verifiedStudentAuth = await verifySessionParticipant(
                sessionCode,
                email.trim(),
            );

            saveStudentAuth(verifiedStudentAuth);
            setStudentAuth(verifiedStudentAuth);
        } catch (error) {
            if (error instanceof Error) {
                setError(error.message);
            }
        } finally {
            setIsVerifying(false);
        }
    };

    return (
        <main className="grid min-h-screen place-items-center bg-isel-white px-6 py-10">
            <section className="w-full max-w-md rounded-md border-2 border-isel-purple bg-isel-white p-6 shadow-sm">
                <div className="grid gap-2">
                    <p className="text-xs font-bold uppercase tracking-widest text-isel-red">
                        OEIMS
                    </p>

                    <h1 className="text-2xl font-bold text-isel-purple">
                        Session verification
                    </h1>

                    <p className="text-sm font-semibold text-isel-purple/70">
                        Verify your institutional email before joining the exam
                        session.
                    </p>
                </div>

                {!sessionCode && (
                    <p className="mt-6 rounded-md border-2 border-isel-red bg-isel-pink px-3 py-2 text-sm font-semibold text-isel-purple">
                        Missing session code. Open the link provided by your
                        professor.
                    </p>
                )}

                {studentAuth ? (
                    <div className="mt-6 rounded-md border-2 border-isel-purple bg-isel-purple/5 px-3 py-3">
                        <p className="text-sm font-bold text-isel-purple">
                            Verification completed.
                        </p>

                        <p className="mt-1 text-sm font-semibold text-isel-purple/70">
                            You can now start the exam client.
                        </p>
                    </div>
                ) : (
                    <form className="mt-6 grid gap-4" onSubmit={handleSubmit}>
                        <div>
                            <label
                                htmlFor="session-code"
                                className="block text-xs font-bold uppercase tracking-wide text-isel-purple/70"
                            >
                                Session code
                            </label>

                            <input
                                id="session-code"
                                value={sessionCode}
                                readOnly
                                className="mt-1 w-full rounded-md border-2 border-isel-purple bg-isel-purple/5 px-3 py-2 text-sm font-bold uppercase tracking-widest text-isel-purple outline-none"
                            />
                        </div>

                        <div>
                            <label
                                htmlFor="student-email"
                                className="block text-xs font-bold uppercase tracking-wide text-isel-purple/70"
                            >
                                Student email
                            </label>

                            <input
                                id="student-email"
                                type="email"
                                value={email}
                                onChange={(event) =>
                                    setEmail(event.currentTarget.value)
                                }
                                placeholder="student@isel.pt"
                                autoCapitalize="none"
                                autoCorrect="off"
                                spellCheck={false}
                                className="mt-1 w-full rounded-md border-2 border-isel-purple bg-isel-white px-3 py-2 text-sm font-semibold text-isel-purple outline-none placeholder:text-isel-purple/35 focus:border-isel-red"
                            />
                        </div>

                        {error && (
                            <p className="rounded-md border-2 border-isel-red bg-isel-pink px-3 py-2 text-sm font-semibold text-isel-purple">
                                {error}
                            </p>
                        )}

                        <button
                            type="submit"
                            className="app-button"
                            disabled={!canVerify}
                        >
                            {isVerifying ? "Verifying..." : "Verify session"}
                        </button>
                    </form>
                )}
            </section>
        </main>
    );
}