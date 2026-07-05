export const API_URL = import.meta.env.VITE_API_URL;

let onAuthError: (() => void) | null = null;

export function registerAuthErrorHandler(fn: () => void) {
    onAuthError = fn;
}

type ApiErrorResponse = {
  error: string;
};

type ApiResponseType = "json" | "blob";

export async function apiFetch<T>(
    endpoint: string,
    options: RequestInit = {},
    token?: string,
): Promise<T> {
  const headers = new Headers(options.headers);

  const isJsonRequestWithoutContentType =
      typeof options.body === "string" && !headers.has("Content-Type");

  if (isJsonRequestWithoutContentType) {
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

  switch (getApiResponseType(res)) {
    case "json":
      return await res.json() as T;
    case "blob":
      return await res.blob() as T;
  }
}

async function getApiErrorMessage(res: Response): Promise<string> {
  if (getApiResponseType(res) === "json") {
    try {
      const body = await res.json() as ApiErrorResponse;

      if (typeof body.error === "string") {
        return body.error;
      }
    } catch {
      // invalid JSON error body.
    }
  }

  try {
    const text = await res.text();

    if (text) {
      return text;
    }
  } catch {
    // unreadable error body.
  }

  return "It seems a strange error occurred. Please try again later.";
}

function getApiResponseType(res: Response): ApiResponseType {
  const contentType = res.headers.get("Content-Type") ?? "";

  if (contentType.includes("application/json") || contentType.includes("+json")) {
    return "json";
  }

  // in our current API non-JSON responses are downloadable files
  return "blob";
}

export function createEventSource(eventId: string): EventSource {
  return new EventSource(
      `${API_URL}/events/${encodeURIComponent(eventId)}/listen`,
      { withCredentials: true },
  );
}