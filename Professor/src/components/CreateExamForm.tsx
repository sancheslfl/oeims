import {type SubmitEventHandler, useState} from "react";
import type {CreateExamRequest} from "../types";

type CreateExamFormProps = {
    isCreating: boolean;
    onCreateExam: (draft: CreateExamRequest) => Promise<void>;
}


export function CreateExamForm({isCreating, onCreateExam}: CreateExamFormProps) {
    const [title, setTitle] = useState("");
    const [description, setDescription] = useState("");
    const [durationMins, setDurationMins] = useState("");

    const handleSubmit: SubmitEventHandler<HTMLFormElement> = async (event) => {
        event.preventDefault();

        const trimmedTitle = title.trim();
        const trimmedDescription = description.trim();
        const parsedDuration = Number(durationMins);

        if (!title || !description || !Number.isInteger(parsedDuration) || parsedDuration <= 0) {
            return;
        }

        try {
            await onCreateExam({
                title: trimmedTitle,
                description: trimmedDescription,
                durationMins: parsedDuration,
            });

            setTitle("");
            setDescription("");
            setDurationMins("");
        } catch {
            // error displayed by the parent.
        }
    };

    return (
        <section className="grid gap-4">
            <h2 className="app-section-title">Create exam</h2>

            <form className="grid gap-4" onSubmit={handleSubmit}>
                <div className="grid gap-1">
                    <label htmlFor="exam-title" className="app-label">
                        Title
                    </label>

                    <input
                        id="exam-title"
                        className="app-input"
                        value={title}
                        onChange={(event) => setTitle(event.target.value)}
                        placeholder="LEIC-AED T1 C.3.07"
                        aria-describedby="exam-title-help"
                        required
                    />

                    <p id="exam-title-help" className="text-sm text-isel-purple">
                        Format: &lt;Course abbreviation&gt;-&lt;Subject abbreviation&gt; &lt;Exam type&gt; &lt;Exam room number&gt;.
                        Example: <strong>LEIC-AED T1 C.3.07</strong>.
                    </p>
                </div>

                <div className="grid gap-1">
                    <label htmlFor="exam-description" className="app-label">
                        Description <span className="text-sm font-normal text-gray-400">(Optional)</span>
                    </label>

                    <textarea
                        id="exam-description"
                        className="app-input min-h-24 resize-none"
                        value={description}
                        onChange={(event) => setDescription(event.target.value)}
                        placeholder="First assessment for the AED module"
                        required
                    />
                </div>

                <div className="grid gap-1">
                    <label htmlFor="exam-duration" className="app-label">
                        Duration in minutes
                    </label>

                    <input
                        id="exam-duration"
                        className="app-input"
                        type="number"
                        min={1}
                        value={durationMins}
                        onChange={(event) => setDurationMins(event.target.value)}
                        placeholder="90"
                        required
                    />
                </div>

                <button
                    type="submit"
                    className="app-button"
                    disabled={isCreating}
                >
                    {isCreating ? "Creating..." : "Create exam"}
                </button>
            </form>
        </section>
    );
}