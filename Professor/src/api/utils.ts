export const API_URL = import.meta.env.VITE_API_URL;
const WEBSOCKETS_URL = import.meta.env.VITE_WS_URL;

export async function apiFetch<T>(
    endpoint: string,
    options: RequestInit = {},
    token?: string
): Promise<T> {
  const headers = new Headers(options.headers);

  if (!headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  const res = await fetch(`${API_URL}${endpoint}`, {
    ...options,
    headers,
    credentials: "same-origin",
  });

  if (!res.ok) {
    const message = await res.text().catch(() => "");
    throw new Error(message || `HTTP ${res.status}`);
  }

  if (res.status === 204) return undefined as T;

  return await res.json() as T;
}

export function wsUrl(path: string, token: string): string {
  return `${WEBSOCKETS_URL}${path}?token=${encodeURIComponent(token)}`;
}
