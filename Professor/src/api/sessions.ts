import { apiFetch } from "./utils";
import type {
    EventResponse,
    ParticipantResponse,
    SessionResponse,
    StudentAuth,
} from "../types";

export function createSession(
    examId: string,
    allowedEmailDomain: string,
    token: string,
): Promise<SessionResponse> {
    return apiFetch<SessionResponse>(
        "/sessions",
        {
            method: "POST",
            body: JSON.stringify({ examId, allowedEmailDomain }),
        },
        token,
    );
}

export function getCurrentSession(
    token: string,
): Promise<SessionResponse | undefined> {
    return apiFetch<SessionResponse | undefined>(
        "/sessions/current",
        {
            method: "GET",
        },
        token,
    );
}

export function startSession(
    sessionId: string,
    token: string,
): Promise<SessionResponse> {
    return apiFetch<SessionResponse>(
        `/sessions/${sessionId}/start`,
        {
            method: "POST",
        },
        token,
    );
}

export function endSession(
    sessionId: string,
    token: string,
): Promise<SessionResponse> {
    return apiFetch<SessionResponse>(
        `/sessions/${sessionId}/end`,
        {
            method: "POST",
        },
        token,
    );
}

export function getSessionParticipants(
    sessionId: string,
    token: string,
): Promise<ParticipantResponse[]> {
    return apiFetch<ParticipantResponse[]>(
        `/sessions/${sessionId}/participants`,
        {
            method: "GET",
        },
        token,
    );
}

export function getActiveSessions(token: string): Promise<SessionResponse[]> {
    return apiFetch<SessionResponse[]>(
        "/sessions/active",
        { method: "GET" },
        token,
    );
}

export function joinSessionAsSupervisor(
    code: string,
    token: string,
): Promise<SessionResponse> {
    return apiFetch<SessionResponse>(
        "/sessions/join-as-supervisor",
        {
            method: "POST",
            body: JSON.stringify({ code }),
        },
        token,
    );
}

export function requestSessionJoin(
    code: string,
    email: string,
): Promise<void> {
    return apiFetch<void>(
        `/sessions/${encodeURIComponent(code)}/join`,
        {
            method: "POST",
            body: JSON.stringify({ email }),
        },
    );
}

export function verifyJoin(token: string): Promise<StudentAuth> {
    return apiFetch<StudentAuth>(
        "/sessions/join/verify",
        {
            method: "POST",
            body: JSON.stringify({ token }),
        },
    );
}

export function getSessionEvents(sessionId: string, token: string) {
    return apiFetch<EventResponse[]>(
        `/sessions/${sessionId}/events`,
        {
            method: "GET",
        },
        token,
    );
}