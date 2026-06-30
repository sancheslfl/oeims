import { type SubmitEventHandler, useState } from "react";
import { useParams, useSearchParams } from "react-router-dom";
import { authorizeSentinel } from "../../api/loopback.ts";
import { requestSessionJoin } from "../../api/sessions.ts";
import { IselLogo } from "../../components/topbar/IselLogo.tsx";

type Message = {
    type: "success" | "error";
    text: string;
};

export function SessionVerification() {
    const { code } = useParams<{ code: string }>();
    const [searchParams] = useSearchParams();

    const emailJoinToken = searchParams.get("token") ?? "";
    const isAuthorizationStep = Boolean(emailJoinToken);
    const isValidLink = Boolean(code || emailJoinToken);

    const [email, setEmail] = useState("");
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [message, setMessage] = useState<Message | null>(null);

    const isDone = message?.type === "success";
    const canSubmit =
        !isSubmitting &&
        !isDone &&
        (isAuthorizationStep || Boolean(code && email.trim()));

    const handleSubmit: SubmitEventHandler<HTMLFormElement> = async (event) => {
        event.preventDefault();

        if (!canSubmit) return;

        setMessage(null);
        setIsSubmitting(true);

        try {
            if (isAuthorizationStep) {
                await authorizeSentinel(emailJoinToken);

                setMessage({
                    type: "success",
                    text: "Sentinel authorized successfully.",
                });
            } else {
                await requestSessionJoin(code!, email.trim());

                setMessage({
                    type: "success",
                    text: "Verification email sent. Open the verification link sent to your email to authorize Sentinel.",
                });
            }
        } catch (error) {
            setMessage({
                type: "error",
                text:
                    error instanceof Error
                        ? error.message
                        : "Sentinel authorization failed.",
            });
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <main className="grid min-h-screen place-items-center bg-linear-to-br from-isel-white via-isel-pink to-isel-white px-6 py-10">
            <section className="w-full max-w-md rounded-md border-2 border-isel-purple bg-isel-white/95 p-6 shadow-sm">
                <div className="grid justify-items-center gap-4 text-center">
                    <IselLogo className="h-8 w-auto" />

                    <div className="grid gap-2">
                        <p className="text-xs font-bold uppercase tracking-widest text-isel-red">
                            OEIMS
                        </p>

                        <h1 className="text-2xl font-bold text-isel-purple">
                            {isAuthorizationStep
                                ? "Sentinel authorization"
                                : "Session join"}
                        </h1>

                        <p className="text-sm font-semibold text-isel-purple/70">
                            {isAuthorizationStep
                                ? "Authorize the local OEIMS Sentinel before joining the exam session."
                                : "Enter your institutional email to receive the Sentinel authorization link."}
                        </p>
                    </div>
                </div>

                {!isValidLink && (
                    <Notice
                        type="error"
                        text="Invalid join link. Open the link provided for this session."
                    />
                )}

                {message && <Notice {...message} />}

                {isValidLink && !isDone && (
                    <form className="mt-6 grid gap-4" onSubmit={handleSubmit}>
                        {!isAuthorizationStep && (
                            <>
                                <div>
                                    <label
                                        htmlFor="session-code"
                                        className="block text-xs font-bold uppercase tracking-wide text-isel-purple/70"
                                    >
                                        Session code
                                    </label>

                                    <input
                                        id="session-code"
                                        value={code}
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
                                        placeholder="student@alunos.isel.pt"
                                        autoCapitalize="none"
                                        autoCorrect="off"
                                        spellCheck={false}
                                        className="mt-1 w-full rounded-md border-2 border-isel-purple bg-isel-white px-3 py-2 text-sm font-semibold text-isel-purple outline-none placeholder:text-isel-purple/35 focus:border-isel-red"
                                    />
                                </div>
                            </>
                        )}

                        <button
                            type="submit"
                            className="app-button"
                            disabled={!canSubmit}
                        >
                            {isSubmitting
                                ? isAuthorizationStep
                                    ? "Authorizing..."
                                    : "Sending..."
                                : isAuthorizationStep
                                    ? "Authorize Sentinel"
                                    : "Send verification email"}
                        </button>
                    </form>
                )}
            </section>
        </main>
    );
}

function Notice({ type, text }: Message) {
    return (
        <p
            className={`mt-6 rounded-md border-2 px-3 py-2 text-sm font-semibold text-isel-purple ${
                type === "error"
                    ? "border-isel-red bg-isel-pink"
                    : "border-isel-purple bg-isel-purple/5"
            }`}
        >
            {text}
        </p>
    );
}