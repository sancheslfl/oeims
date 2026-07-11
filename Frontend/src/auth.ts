import { createContext, useContext } from "react";
import type { UserRole } from "./types";

export type AuthUser = {
  id: string;
  email: string;
  role: UserRole;
  token: string;
};

export type AuthContextValue = {
  auth: AuthUser | null;
  setAuth: (auth: AuthUser) => void;
  clearAuth: () => void;
};

export const AUTH_STORAGE_KEY = "oeims:auth";

export const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth() {
  const context = useContext(AuthContext);

  if (!context) {
    throw new Error("useAuth must be used inside AuthProvider.");
  }

  return context;
}
