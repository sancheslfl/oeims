import { useState } from "react";
import { useNavigate } from "react-router-dom";
import {useAuth} from "../AuthContext.tsx";
import {TopBar} from "../components/TopBar.tsx";
import {Sidebar} from "../components/Sidebar.tsx";
import {ClassroomCanvas} from "../components/ClassroomCanvas.tsx";


export function Dashboard() {
    const { auth, clearAuth } = useAuth();
    const navigate = useNavigate();

    const [isSidebarOpen, setIsSidebarOpen] = useState(true);

    if (!auth) {
        return null;
    }

    return (
        <div className="grid min-h-dvh grid-rows-[auto_1fr] bg-isel-white text-isel-purple">
            <TopBar
                teacher={{
                    name: "", // TODO: Add name in the response
                    email: auth.email,
                }}
                onSignOut={() => {
                    clearAuth();
                    navigate("/");
                }}
            />

            <main className="flex min-h-0">
                <Sidebar
                    isOpen={isSidebarOpen}
                    onToggle={() => setIsSidebarOpen((current) => !current)}
                    onCreateExam={(draft) => {
                        console.log("Create exam", draft);
                    }}
                    onGenerateSession={(draft) => {
                        console.log("Generate session", draft);
                    }}
                />

                <section
                    aria-label="Classroom layout"
                    className="flex min-w-0 flex-1 items-center justify-center overflow-x-auto bg-linear-to-br from-isel-white from-60% to-isel-pink p-6"
                >
                    <ClassroomCanvas />
                </section>
            </main>
        </div>
    );
}