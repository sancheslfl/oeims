import {apiFetch} from "./utils.ts";

export async function login(email: string, password: string): Promise<Response> {
    return apiFetch("", {
        method: "POST",
        body: JSON.stringify({ email, password }),
    })
}