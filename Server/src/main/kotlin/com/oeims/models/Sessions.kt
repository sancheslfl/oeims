package com.oeims.models

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp

enum class SessionStatus { PENDING, ACTIVE, ENDED }

object Sessions : UUIDTable("sessions") {
    val examId       = reference("exam_id", Exams)
    val supervisorId = reference("supervisor_id", Users)
    val code         = char("code", 6).uniqueIndex()
    val status       = enumerationByName("status", 16, SessionStatus::class)
    val createdAt    = timestamp("created_at")
    val startedAt    = timestamp("started_at").nullable()
    val endedAt      = timestamp("ended_at").nullable()
}
