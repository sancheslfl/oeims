package com.oeims.models

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

enum class SessionStatus { PENDING, ACTIVE, ENDED }

object Sessions : UUIDTable("sessions") {
    val examId = reference("exam_id", Exams)
    val supervisorId = reference("supervisor_id", Users)
    val code = char("code", 6).uniqueIndex()
    val allowedEmailDomain = varchar("allowed_email_domain", 254)
    val status = enumerationByName("status", 16, SessionStatus::class)
    val createdAt = timestamp("created_at")
    val startedAt = timestamp("started_at").nullable()
    val endedAt = timestamp("ended_at").nullable()
}

/**
 *  Table SessionSupervisors
 *  - Additional professors granted access to a session by its creator.
 *  - The creator is always in Sessions.supervisorId. This table only holds extra supervisors.
 */
object SessionSupervisors : Table("session_supervisors") {
    val sessionId = reference("session_id", Sessions)
    val userId = reference("user_id", Users)
    override val primaryKey = PrimaryKey(sessionId, userId)
}

object SessionJoins : UUIDTable("session_joins") {
    val sessionId = reference("session_id", Sessions)
    val email = varchar("email", 254)

    val emailJwtId = varchar("jwt_id", 36).uniqueIndex()
    val emailExpiresAt = timestamp("expires_at")
    val emailVerifiedAt = timestamp("verified_at").nullable()

    val createdAt = timestamp("created_at")

    init {
        index("session_joins_session_email_index", false, sessionId, email)
        inde
    }
}
