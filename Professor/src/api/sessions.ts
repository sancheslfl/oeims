import { apiFetch } from "./utils";
import type {SessionResponse} from "../types";

export function createSession(
    examId: string,
    token: string,
): Promise<SessionResponse> {
    return apiFetch<SessionResponse>("/sessions", {
        method: "POST",
        headers: {
            Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ examId }),
    });
}

export function getSession(id: string, token: string): Promise<SessionResponse> {
    return apiFetch<SessionResponse>(`/sessions/${id}`, {
        method: "GET",
        headers: {
            Authorization: `Bearer ${token}`,
        },
    });
}

export function startSession(id: string, token: string): Promise<SessionResponse> {
    return apiFetch<SessionResponse>(`/sessions/${id}/start`, {
        method: "POST",
        headers: {
            Authorization: `Bearer ${token}`,
        },
    });
}

export function endSession(id: string, token: string): Promise<SessionResponse> {
    return apiFetch<SessionResponse>(`/sessions/${id}/end`, {
        method: "POST",
        headers: {
            Authorization: `Bearer ${token}`,
        },
    });
}