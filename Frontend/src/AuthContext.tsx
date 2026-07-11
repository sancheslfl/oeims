import { useEffect, useState } from "react";
import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { USER_ROLES, type UserRole } from "./types";
import { registerAuthErrorHandler } from "./api/utils.ts";
import {
  AUTH_STORAGE_KEY,
  AuthContext,
  type AuthUser,
  useAuth,
} from "./auth.ts";

export function AuthProvider({ children }: { children: ReactNode }) {
  const [auth, setAuthState] = useState<AuthUser | null>(() => {
    const storedAuth = sessionStorage.getItem(AUTH_STORAGE_KEY);

    if (!storedAuth) {
      return null;
    }

    try {
      return JSON.parse(storedAuth) as AuthUser;
    } catch {
      sessionStorage.removeItem(AUTH_STORAGE_KEY);
      return null;
    }
  });

  useEffect(() => {
    registerAuthErrorHandler(() => {
      sessionStorage.removeItem(AUTH_STORAGE_KEY);
      window.location.href = "/";
    });
  }, []);

  function setAuth(auth: AuthUser) {
    sessionStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(auth));
    setAuthState(auth);
  }

  function clearAuth() {
    sessionStorage.removeItem(AUTH_STORAGE_KEY);
    setAuthState(null);
  }

  return (
      <AuthContext.Provider value={{ auth, setAuth, clearAuth }}>
        {children}
      </AuthContext.Provider>
  );
}

export function AuthRequire({ children, allowedRole = USER_ROLES.Professor }: { children: ReactNode; allowedRole?: UserRole }) {
  const { auth } = useAuth();

  if (!auth || auth.role !== allowedRole) {
    return <Navigate to="/" replace />;
  }

  return children;
}
