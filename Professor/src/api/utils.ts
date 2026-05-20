const { VITE_BASE_URL } = import.meta.env;

const API_URI = `${VITE_BASE_URL}/api`;

export async function apiFetch(endpoint: string, options: RequestInit = {}): Promise<Response> {
    const defaultOptions: RequestInit = {
        headers: {
            "Content-Type": "application/json",
        },
        credentials: "include",
    };

    const res = await fetch(`${API_URI}${endpoint}`, {
        ...defaultOptions,
        ...options,
    });

    // TODO: We intend to pass the API response error message in the body to here
    if (!res.ok) throw new Error();

    try {
        return await res.json();
    } catch {
        return res;
    }
}