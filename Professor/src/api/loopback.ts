const LOOPBACK_URL = "http://127.0.0.1:17653";
const SENTINEL_AUTHORIZE_URL = `${LOOPBACK_URL}/sentinel/authorize`;

export async function authorizeSentinel(emailJoinToken: string) {
    const response = await fetch(SENTINEL_AUTHORIZE_URL, {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
        },
        body: JSON.stringify({ emailJoinToken }),
    });

    if (!response.ok) {
        const body = await response.json().catch(() => undefined);

        throw new Error(body?.error
            ?? "Could not contact Sentinel. Make sure the OEIMS Sentinel is installed and running.");
    }
}