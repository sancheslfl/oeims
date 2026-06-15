export const API_URL = import.meta.env.VITE_API_URL;

let onAuthError: (() => void) | null = null;

export function registerAuthErrorHandler(fn: () => void) {
    onAuthError = fn;
}

type ApiErrorResponse = {
  error: string;
};

export async function apiFetch<T>(
    endpoint: string,
    options: RequestInit = {},
    token?: string,
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
    credentials: "include",
  });

  if (!res.ok) {
    if (res.status === 401 || res.status === 403) {
      onAuthError?.();
    }
    throw new Error(await getApiErrorMessage(res));
  }

  if (res.status === 204) {
    return undefined as T;
  }

  return await res.json() as T;
}

async function getApiErrorMessage(res: Response): Promise<string> {
  try {
    const body = await res.json() as ApiErrorResponse;

    if (typeof body.error === "string") {
      return body.error;
    }
  } catch {
    // invalid error body.
  }

  return `It seems a strange error occurred. Please try again later.`;
}

export function createEventSource(eventId: string): EventSource {
  return new EventSource(
      `${API_URL}/events/${encodeURIComponent(eventId)}/listen`,
      { withCredentials: true },
  );
}
