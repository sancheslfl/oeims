import { useNavigate } from "react-router-dom";
import { useAuth } from "../../AuthContext";
import { IselLogo } from "./IselLogo";

export function TopBar() {
    const { auth, clearAuth } = useAuth();
    const navigate = useNavigate();

    if (!auth) {
        return null;
    }

    const displayName = ""      // TODO: Add and retrieve the name from the server response
    const initial = displayName.trim().charAt(0).toUpperCase() || "P";

    function handleSignOut() {
        clearAuth();
        navigate("/");
    }

    return (
        <header className="flex h-18 items-stretch border-b-2 border-isel-purple bg-isel-white">
            <div className="flex w-fit shrink-0 items-center border-r-2 border-isel-purple px-4">
                <IselLogo className="h-10 w-auto shrink-0" />
            </div>

            <div className="ml-auto flex items-center gap-4 px-6">
                <div className="hidden text-right sm:grid">
                    <span className="font-semibold">{displayName}</span>
                    <span className="text-xs">{auth.email}</span>
                </div>

                <div
                    aria-hidden="true"
                    className="grid size-11 place-items-center rounded-full border-2 border-isel-purple font-bold"
                >
                    {initial}
                </div>

                <button
                    type="button"
                    onClick={handleSignOut}
                    aria-label="Sign out"
                    className="grid size-9 place-items-center rounded-md border-2 border-isel-red text-lg font-bold text-isel-red hover:bg-isel-pink"
                >
                    ⎋
                </button>
            </div>
        </header>
    );
}