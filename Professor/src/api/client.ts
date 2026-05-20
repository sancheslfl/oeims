const BASE_URL = import.meta.env.VITE_API_URL ?? '';

export async function apiFetch<T>(
  path: string,
  options: RequestInit = {},
  token?: string
): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string> | undefined ?? {}),
  };
  if (token) headers['Authorization'] = `Bearer ${token}`;

  const res = await fetch(`${BASE_URL}${path}`, { ...options, headers });

  if (!res.ok) {
    const text = await res.text().catch(() => '');
    let message = text;
    try {
      const json = JSON.parse(text) as Record<string, unknown>;
      if (typeof json.error === 'string') message = json.error;
    } catch { /* not JSON — use raw text */ }
    throw new Error(message || `HTTP ${res.status}`);
  }
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

export function wsUrl(path: string, token: string): string {
  const base = import.meta.env.VITE_WS_URL ?? `ws://${window.location.host}`;
  return `${base}${path}?token=${encodeURIComponent(token)}`;
}
