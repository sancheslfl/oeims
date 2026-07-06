import type { StudentAuth } from "./types";

const LAST_SESSION_KEY_PREFIX = "oeims:last-session:";
const SETTINGS_KEY_PREFIX = "oeims:professor-settings:";
const STUDENT_AUTH_KEY = "oeims:student-auth";

export function saveLastSessionId(userId: string, sessionId: string) {
    localStorage.setItem(getLastSessionKey(userId), sessionId);
}

export function clearLastSessionId(userId: string) {
    localStorage.removeItem(getLastSessionKey(userId));
}

export function loadAllowedEmailDomain(professorId: string | undefined) {
    if (!professorId) return "";

    try {
        return localStorage.getItem(getSettingsKey(professorId)) ?? "";
    } catch {
        return "";
    }
}

export function saveAllowedEmailDomain(
    professorId: string,
    allowedEmailDomain: string,
) {
    localStorage.setItem(getSettingsKey(professorId), allowedEmailDomain);
}

export function saveStudentAuth(auth: StudentAuth) {
    sessionStorage.setItem(STUDENT_AUTH_KEY, JSON.stringify(auth));
}

function getLastSessionKey(userId: string) {
    return `${LAST_SESSION_KEY_PREFIX}${userId}`;
}

function getSettingsKey(professorId: string) {
    return `${SETTINGS_KEY_PREFIX}${professorId}`;
}