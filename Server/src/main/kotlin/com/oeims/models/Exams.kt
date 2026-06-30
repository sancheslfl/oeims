package com.oeims.models

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp

object Exams : UUIDTable("exams") {
    val createdBy = reference("created_by", Users)
    val title = varchar("title", 255) // TODO: Tighten the length
    val description = text("description").nullable()
    val durationMins = integer("duration_mins")
    val createdAt = timestamp("created_at")
}

@JvmInline
value class ExamTitle(val value: String) {

    init {
        if (value.isBlank()) throw ValidationException("Exam title cannot be blank")
        if (!REGEX.matches(value)) throw ValidationException("Exam title must follow the format: {Course abbreviation}-{Subject abbreviation} {Exam type} {Exam room number}")
    }

    companion object {
        // {Course abbreviation}-{Subject abbreviation} {Exam type} {Exam room number}
        // e.g. "LEIC-AED T1 C.3.07"
        val REGEX = Regex("""^[A-Z]{4}-[A-Z]{2,4} (T1|T2|EN|ER|EE) [ACEFGMP]\.\d{1,2}\.\d{2}$""")
    }
}

fun String.toExamTitle() = ExamTitle(this)