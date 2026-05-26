const LAST_SESSION_KEY_PREFIX = "oeims:last-session:";

export function saveLastSessionId(userId: string, sessionId: string) {
    localStorage.setItem(getLastSessionKey(userId), sessionId);
}

export function clearLastSessionId(userId: string) {
    localStorage.removeItem(getLastSessionKey(userId));
}

function getLastSessionKey(userId: string) {
    return `${LAST_SESSION_KEY_PREFIX}${userId}`;
}