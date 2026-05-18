package com.oeims.models

import com.oeims.exceptions.ValidationException

@JvmInline
value class ExamTitle(val value: String) {

    init {
        if (value.isBlank()) throw ValidationException("Exam title cannot be blank")
        if (!REGEX.matches(value)) throw ValidationException("Exam title must follow the format: {Course abbreviation}-{Subject abbreviation} {Exam type} {Exam room number}")
    }

    companion object {
        // {Course abbreviation}-{Subject abbreviation} {Exam type} {Exam room number}
        // e.g. "LEIC-AED T1 C.3.07"
        val REGEX = Regex("^[A-Z]{4}-[A-Z]{3} (T1|T2|T3|T4|T5) C\\.\\d\\.\\d{2}$")
    }
}

fun String.toExamTitle() = ExamTitle(this)
