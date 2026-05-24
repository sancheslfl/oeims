import iselLogoUrl from "../assets/ISEL Logo.svg";

export function IselLogo({ className }: { className?: string }) {
    return (
        <img
            src={iselLogoUrl}
            alt="ISEL"
            className={className}
        />
    );
}