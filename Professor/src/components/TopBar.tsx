import type { Teacher } from "../types";
import {IselLogo} from "./IselLogo.tsx";

type TopBarProps = {
    teacher: Teacher;
    onSignOut: () => void;
};

export function TopBar({ teacher, onSignOut }: TopBarProps) {
    const initial = teacher.name.trim().charAt(0).toUpperCase() || "T";

    return (
        <header className="flex h-18 items-stretch border-b-2 border-isel-purple bg-isel-white">
            <div className="flex w-40 items-center border-r-2 border-isel-purple px-4">
                <IselLogo className="h-10 w-auto" />
            </div>

            <div className="ml-auto flex items-center gap-4 px-6">
                <div className="hidden text-right sm:grid">
                    <span className="font-semibold">{teacher.name}</span>
                    {teacher.email && (
                        <span className="text-xs">{teacher.email}</span>
                    )}
                </div>

                <div
                    aria-hidden="true"
                    className="grid size-11 place-items-center rounded-full border-2 border-isel-purple font-bold"
                >
                    {initial}
                </div>

                <button
                    type="button"
                    onClick={onSignOut}
                    aria-label="Sign out"
                    className="grid size-9 place-items-center rounded-md border-2 border-isel-red text-lg font-bold text-isel-red hover:bg-isel-pink"
                >
                    ⎋
                </button>
            </div>
        </header>
    );
}