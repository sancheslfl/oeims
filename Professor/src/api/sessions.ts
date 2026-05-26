import { apiFetch } from "./utils";
import type {ParticipantResponse, SessionResponse} from "../types";

export function createSession(
    examId: string,
    token: string,
): Promise<SessionResponse> {
    return apiFetch<SessionResponse>(
        "/sessions",
        {
            method: "POST",
            body: JSON.stringify({ examId }),
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