import { createContext, useContext, useState, useCallback, type ReactNode } from 'react';
import type { AuthResponse } from './types';

interface AuthContextValue {
  auth: AuthResponse | null;
  setAuth: (a: AuthResponse | null) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [auth, setAuthState] = useState<AuthResponse | null>(() => {
    try {
      const stored = localStorage.getItem('oeims_auth');
      return stored ? (JSON.parse(stored) as AuthResponse) : null;
    } catch {
      return null;
    }
  });

  const setAuth = useCallback((a: AuthResponse | null) => {
    setAuthState(a);
    if (a) localStorage.setItem('oeims_auth', JSON.stringify(a));
    else localStorage.removeItem('oeims_auth');
  }, []);

  const logout = useCallback(() => setAuth(null), [setAuth]);

  return (
    <AuthContext.Provider value={{ auth, setAuth, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
