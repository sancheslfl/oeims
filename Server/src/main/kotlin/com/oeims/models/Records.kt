package com.oeims.models

import com.oeims.models.dto.EventResponse
import com.oeims.models.dto.ExamResponse
import com.oeims.models.dto.ParticipantResponse
import com.oeims.models.dto.SessionResponse
import java.time.Instant
import java.util.*

data class ExamRecord(
    val id: UUID,
    val createdBy: UUID,
    val title: String,
    val description: String?,
    val durationMins: Int,
    val createdAt: Instant
) {
    fun toResponse() = ExamResponse(
        id = id.toString(),
        createdBy = createdBy.toString(),
        title = title,
        description = description,
        durationMins = durationMins,
        createdAt = createdAt.toString()
    )
}

data class EventRecord(
    val id: UUID,
    val participantId: UUID,
    val monitorName: String,
    val message: String,
    val severity: Severity,
    val occurredAt: Instant
) {
    fun toResponse() = EventResponse(
        id = id.toString(),
        participantId = participantId.toString(),
        monitorName = monitorName,
        message = message,
        severity = severity.name,
        occurredAt = occurredAt.toString()
    )
}

data class ParticipantRecord(
    val id: UUID,
    val sessionId: UUID,
    val email: String,
    val examIdentityCode: String?,
    val connectionStatus: ConnectionStatus,
    val lastHeartbeat: Instant?,
    val joinedAt: Instant
) {
    fun toResponse() = ParticipantResponse(
        id = id.toString(),
        sessionId = sessionId.toString(),
        email = email,
        connectionStatus = connectionStatus.name,
        lastHeartbeat = lastHeartbeat?.toString(),
        joinedAt = joinedAt.toString(),
    )
}

data class SessionRecord(
    val id: UUID,
    val examId: UUID,
    val supervisorId: UUID,
    val code: String,
    val allowedEmailDomain: String,
    val status: SessionStatus,
    val createdAt: Instant,
    val startedAt: Instant?,
    val endedAt: Instant?
) {
    fun toResponse() = SessionResponse(
        id = id.toString(),
        examId = examId.toString(),
        supervisorId = supervisorId.toString(),
        code = code,
        status = status.name,
        startedAt = startedAt?.toString(),
        endedAt = endedAt?.toString(),
    )
}

data class SessionJoinRecord(
    val id: UUID,
    val sessionId: UUID,
    val email: String,
    val jwtId: String,
    val expiresAt: Instant,
    val verifiedAt: Instant?,
    val createdAt: Instant,
)

data class UserRecord(
    val id: UUID,
    val email: String,
    val role: UserRole,
    val passwordHash: String,
    val createdAt: Instant
)