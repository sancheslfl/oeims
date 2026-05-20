import {useReducer} from "react";
import {login} from "./api/auth.ts";


type State = {
    email: string;
    password: string;
    error: string | null;
    isSubmitting: boolean;
};

type Action =
    | { type: "email_changed"; value: string }
    | { type: "password_changed"; value: string }
    | { type: "submit" }
    | { type: "success" }
    | { type: "error"; error: string };

const initialState: State = {
    email: "",
    password: "",
    error: null,
    isSubmitting: false,
};

function reducer(state: State, action: Action): State {
    switch (action.type) {
        case "email_changed":
            return { ...state, email: action.value };
        case "password_changed":
            return { ...state, password: action.value };
        case "submit":
            return { ...state, isSubmitting: true, error: null };
        case "success":
            return { ...state, isSubmitting: false };
        case "error":
            return { ...state, isSubmitting: false, error: action.error };
        default:
            return state;
    }
}

export function Login() {
    const [state, dispatch] = useReducer(reducer, initialState);
    const { email, password, error, isSubmitting } = state;

    async function handleSubmit() {

        try {
            await login(email, password);
            dispatch({ type: "success" });
        } catch (e: unknown) {
            if (e instanceof Error) {
                dispatch({type: "error", error: e.message});
            }
        }
    }

    return (
        <>
            <input
                value={email}
                onChange={(e) => dispatch({ type: "email_changed", value: e.target.value })}
                placeholder="Email"
            />
            <input
                type="password"
                value={password}
                onChange={(e) => dispatch({ type: "password_changed", value: e.target.value })}
                placeholder="Password"
            />
            <button onClick={handleSubmit} disabled={isSubmitting}>
                {isSubmitting ? "Logging in..." : "Login"}
            </button>
            {error && <div>{error}</div>}
        </>
    );
}