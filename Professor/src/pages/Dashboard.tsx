import {useEffect, useState} from "react";
import {useNavigate} from "react-router-dom";
import type {CreateExamRequest, ExamResponse, SessionResponse} from "../types";
import {useAuth} from "../AuthContext";
import {createExam, getExams} from "../api/exams";
import {createSession} from "../api/sessions";
import {TopBar} from "../components/TopBar";
import {Sidebar} from "../components/Sidebar";
import {CreateExamForm} from "../components/CreateExamForm";
import {ClassroomCanvas} from "../components/ClassroomCanvas";
import {SessionList} from "../components/SessionList.tsx";

type SessionsByExamId = Record<string, SessionResponse>;

export function Dashboard() {
    const {auth, clearAuth} = useAuth();
    const navigate = useNavigate();

    const [isSidebarOpen, setIsSidebarOpen] = useState(true);
    const [exams, setExams] = useState<ExamResponse[]>([]);
    const [sessionsByExamId, setSessionsByExamId] = useState<SessionsByExamId>({});
    const [isLoadingExams, setIsLoadingExams] = useState(false);
    const [isCreatingExam, setIsCreatingExam] = useState(false);
    const [creatingSessionExamId, setCreatingSessionExamId] = useState<string | null>(null);
    const [error, setError] = useState("");

    useEffect(() => {
        if (!auth) return;

        let ignore = false;
        const token = auth.token;

        async function loadExams() {
            setError("");
            setIsLoadingExams(true);

            try {
                const exams = await getExams(token);
                if (!ignore) setExams(exams);
            } catch (error) {
                if (!ignore) setError(getErrorMessage(error));
            } finally {
                if (!ignore) setIsLoadingExams(false);
            }
        }

        void loadExams();
        return () => {
            ignore = true;
        };
    }, [auth]);

    if (!auth) {
        return null;
    }

    const currentAuth = auth;

    async function handleCreateExam(draft: CreateExamRequest) {
        setError("");
        setIsCreatingExam(true);

        try {
            const createdExam = await createExam(draft, currentAuth.token);

            setExams((current) => [
                createdExam,
                ...current,
            ]);
        } catch (error) {
            setError(getErrorMessage(error));
            throw error;
        } finally {
            setIsCreatingExam(false);
        }
    }

    async function handleGenerateSession(examId: string) {
        if (sessionsByExamId[examId]) {
            return;
        }

        setError("");
        setCreatingSessionExamId(examId);

        try {
            const session = await createSession(examId, currentAuth.token);

            setSessionsByExamId((current) => ({
                ...current,
                [examId]: session,
            }));
        } catch (error) {
            setError(getErrorMessage(error));
            throw error;
        } finally {
            setCreatingSessionExamId(null);
        }
    }

    function handleSignOut() {
        clearAuth();
        navigate("/");
    }

    return (
        <div className="grid min-h-dvh grid-rows-[auto_1fr] bg-isel-white text-isel-purple">
            <TopBar
                teacher={{
                    name: "",   // TODO: Retrieve name from server response
                    email: currentAuth.email,
                }}
                onSignOut={handleSignOut}
            />

            <main className="flex min-h-0">
                <Sidebar
                    isOpen={isSidebarOpen}
                    onToggle={() => setIsSidebarOpen((current) => !current)}
                >
                    {error && (
                        <p className="rounded-md border-2 border-isel-red bg-isel-pink px-3 py-2 text-sm font-semibold text-isel-purple">
                            {error}
                        </p>
                    )}

                    <CreateExamForm
                        isCreating={isCreatingExam}
                        onCreateExam={handleCreateExam}
                    />

                    <SessionList
                        exams={exams}
                        sessionsByExamId={sessionsByExamId}
                        isLoading={isLoadingExams}
                        creatingSessionExamId={creatingSessionExamId}
                        onGenerateSession={handleGenerateSession}
                    />
                </Sidebar>

                <section
                    aria-label="Classroom layout"
                    className="flex min-w-0 flex-1 items-center justify-center overflow-x-auto bg-linear-to-br from-isel-white from-60% to-isel-pink p-6"
                >
                    <ClassroomCanvas/>
                </section>
            </main>
        </div>
    );
}

function getErrorMessage(error: unknown) {
    return error instanceof Error ? error.message : "Unexpected error.";
}