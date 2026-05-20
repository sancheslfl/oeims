import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import { LoginPage } from './pages/LoginPage';
import { HomePage } from './pages/HomePage';
import { SessionPage } from './pages/SessionPage';
import type { ReactNode } from 'react';

function ProtectedRoute({ children }: { children: ReactNode }) {
  const { auth } = useAuth();
  return auth ? <>{children}</> : <Navigate to="/login" replace />;
}

function AppRoutes() {
  const { auth } = useAuth();
  return (
    <Routes>
      <Route path="/login" element={auth ? <Navigate to="/" replace /> : <LoginPage />} />
      <Route path="/" element={<ProtectedRoute><HomePage /></ProtectedRoute>} />
      <Route path="/session/:id" element={<ProtectedRoute><SessionPage /></ProtectedRoute>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </BrowserRouter>
  );
}
