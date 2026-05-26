import type {ExamResponse, SessionResponse} from "../types";

const seats = Array.from({length: 12}, (_, index) => index + 1);

type ClassroomCanvasProps = {
    exam?: ExamResponse;
    session?: SessionResponse;
};

export function ClassroomCanvas({exam, session}: ClassroomCanvasProps) {
    const openExam = exam && session ? exam : undefined;

    return (
        <div
            className="grid min-h-104 w-full min-w-3xl max-w-5xl gap-8 rounded-[3.5rem] border-3 border-isel-purple bg-isel-white p-8 shadow-[0_0.75rem_2rem_rgba(95,20,55,0.12)]">
            <div className="flex items-start justify-between gap-4">
                <div>
                    <h1 className="m-0 text-3xl font-bold text-isel-purple">
                        {openExam ? openExam.title : "Classroom"}
                    </h1>

                    <p className="mt-1 font-semibold text-isel-red">
                        {openExam ? "Waiting for students" : "No active session"}
                    </p>
                </div>
            </div>

            <div className="grid place-items-center gap-8">
                <div className="w-full max-w-64 rounded-md border-2 border-isel-purple p-3 text-center font-bold">
                    Professor
                </div>

                <div
                    aria-label="Empty classroom seats"
                    className="grid w-full max-w-xl grid-cols-4 gap-4"
                >
                    {seats.map((seat) => (
                        <div key={seat} className="grid justify-items-center gap-1">
                            <div className="h-12 w-32 rounded-md border-2 border-isel-purple bg-isel-purple/5"/>
                            <div
                                className="h-6 w-14 rounded-b-md border-2 border-t-0 border-isel-purple bg-isel-white"/>
                        </div>
                    ))}
                </div>
            </div>
        </div>
    );
}