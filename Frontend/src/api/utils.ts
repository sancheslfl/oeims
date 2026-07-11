export const API_URL = import.meta.env.VITE_API_URL;

type ApiErrorResponse = {
  error: string;
};

type ApiResponseType = "json" | "blob";
type AuthErrorHandler = () => void;

let authErrorHandler: AuthErrorHandler | undefined;

export function registerAuthErrorHandler(handler: AuthErrorHandler) {
  authErrorHandler = handler;
}

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

  const hasAuthorization = Boolean(token || headers.has("Authorization"));

  const res = await fetch(`${API_URL}${endpoint}`, {
    ...options,
    headers,
    credentials: "include",
  });

  if (!res.ok) {
    const message = await getApiErrorMessage(res);

    if (hasAuthorization && isAuthErrorStatus(res.status)) {
      authErrorHandler?.();
    }

    throw new Error(message);
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

function isAuthErrorStatus(status: number) {
  return status === 401 || status === 403;
}

export function createEventSource(eventId: string): EventSource {
  return new EventSource(
      `${API_URL}/events/${encodeURIComponent(eventId)}/listen`,
      { withCredentials: true },
  );
}
