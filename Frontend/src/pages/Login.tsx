import { useState } from "react";
import type { SubmitEventHandler } from "react";
import { useNavigate } from "react-router-dom";
import { login } from "../api/auth";
import { useAuth } from "../auth.ts";
import { USER_ROLES } from "../types";
import {IselLogo} from "../components/topbar/IselLogo.tsx";

export function Login() {
  const { setAuth } = useAuth();
  const navigate = useNavigate();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const handleSubmit: SubmitEventHandler<HTMLFormElement> = async (event) => {
    event.preventDefault();

    setError("");
    setLoading(true);

    try {
      const user = await login(email, password);

      if (user.role !== USER_ROLES.Professor) {
        setError("This console is for professors only.");
        return;
      }

      setAuth({
        id: user.userId,
        email: user.email,
        role: user.role,
        token: user.token,
      });

      navigate("/dashboard");
    } catch (error) {
      setError(
          error instanceof Error
              ? error.message
              : "Login failed. Check your credentials.",
      );
    } finally {
      setLoading(false);
    }
  };

  return (
      <main className="grid min-h-dvh place-items-center bg-linear-to-br from-isel-white from-55% to-isel-pink px-6 text-isel-purple">
        <section className="w-full max-w-md rounded-4xl border-[3px] border-isel-purple bg-isel-white p-8 shadow-[0_0.75rem_2rem_rgba(95,20,55,0.12)]">
          <header className="mb-8 border-b-2 border-isel-purple pb-6">
            <div className="inline-flex flex-col items-start">
              <IselLogo className="block h-auto w-28" />

              <h1 className="mt-3 text-4xl font-bold leading-none text-isel-purple">
                OEIMS
              </h1>
            </div>

            <p className="mt-2 font-semibold text-isel-red">
              Professor Dashboard
            </p>
          </header>

          <form onSubmit={handleSubmit} className="grid gap-5">
            <div className="grid gap-2">
              <label htmlFor="email" className="text-sm font-bold">
                Email
              </label>

              <input
                  id="email"
                  type="email"
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  placeholder="professor@isel.pt"
                  required
                  autoFocus
                  className="w-full rounded-md border-2 border-isel-purple bg-isel-white px-3 py-2 text-isel-purple outline-none placeholder:text-isel-purple/45 focus:ring-2 focus:ring-isel-red"
              />
            </div>

            <div className="grid gap-2">
              <label htmlFor="password" className="text-sm font-bold">
                Password
              </label>

              <input
                  id="password"
                  type="password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  placeholder="********"
                  required
                  className="w-full rounded-md border-2 border-isel-purple bg-isel-white px-3 py-2 text-isel-purple outline-none placeholder:text-isel-purple/45 focus:ring-2 focus:ring-isel-red"
              />
            </div>

            {error && (
                <p className="rounded-md border-2 border-isel-red bg-isel-pink px-3 py-2 text-sm font-semibold text-isel-purple">
                  {error}
                </p>
            )}

            <button
                type="submit"
                disabled={loading}
                className="mt-2 rounded-md border-2 border-isel-purple bg-isel-red px-4 py-3 font-bold text-isel-white transition hover:brightness-95 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {loading ? "Signing in…" : "Sign in"}
            </button>
          </form>
        </section>
      </main>
  );
}
