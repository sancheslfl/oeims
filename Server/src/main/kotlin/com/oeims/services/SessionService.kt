package com.oeims.services

import com.oeims.connections.SseBroadcaster
import com.oeims.connections.SseChannels
import com.oeims.connections.SseEvent
import com.oeims.models.AllowedEmailDomain
import com.oeims.models.ConflictException
import com.oeims.models.ForbiddenException
import com.oeims.models.NotFoundException
import com.oeims.models.SessionCode
import com.oeims.models.SessionStatus
import com.oeims.models.dto.SessionResponse
import com.oeims.models.ids.ExamId
import com.oeims.models.ids.ProfessorId
import com.oeims.models.ids.SessionId
import com.oeims.repositories.SessionRecord
import com.oeims.repositories.interfaces.IExamRepository
import com.oeims.repositories.interfaces.ISessionRepository
import kotlinx.serialization.json.Json
import java.time.Instant

class SessionService(
    private val sessionRepository: ISessionRepository,
    private val examRepository: IExamRepository,
    private val sseBroadcaster: SseBroadcaster,
) {
    suspend fun createSession(
        professorId: ProfessorId,
        examId: ExamId,
        allowedEmailDomain: AllowedEmailDomain,
    ): SessionResponse {
        examRepository.findById(examId.value)
            ?: throw NotFoundException("Exam not found")

        repeat(5) {
            val response = sessionRepository.create(
                examId = examId.value,
                supervisorId = professorId.value,
                code = generateSessionCode(),
                allowedEmailDomain = allowedEmailDomain.value,
            )?.toResponse()

            if (response != null) {
                sseBroadcaster.publish(
                    channel = SseChannels.sessions(),
                    event = SseEvent.SESSION_CREATED,
                    data = Json.encodeToString(response),
                )

                return response
            }
        }

        throw IllegalStateException("Failed to generate a unique session code after 5 attempts")
    }

    suspend fun startSession(
        sessionId: SessionId,
        professorId: ProfessorId,
    ): SessionResponse {
        val session = sessionRepository.findById(sessionId.value)
            ?: throw NotFoundException("Session not found")

        if (session.supervisorId != professorId.value)
            throw ForbiddenException("Only the session supervisor can start it")

        if (session.status != SessionStatus.PENDING)
            throw ConflictException("Only a pending session can be started")

        sessionRepository.updateStatus(sessionId.value, SessionStatus.ACTIVE)

        val response = session
            .copy(status = SessionStatus.ACTIVE, startedAt = Instant.now())
            .toResponse()

        sseBroadcaster.publish(
            channel = SseChannels.sessions(),
            event = SseEvent.SESSION_STATUS_UPDATED,
            data = Json.encodeToString(response),
        )

        return response
    }

    suspend fun endSession(
        sessionId: SessionId,
        professorId: ProfessorId,
    ): SessionResponse {
        val session = sessionRepository.findById(sessionId.value)
            ?: throw NotFoundException("Session not found")

        if (session.supervisorId != professorId.value)
            throw ForbiddenException("Only the session supervisor can end it")

        if (session.status != SessionStatus.ACTIVE)
            throw ConflictException("Only an active session can be ended")

        sessionRepository.updateStatus(sessionId.value, SessionStatus.ENDED)

        val response = session
            .copy(status = SessionStatus.ENDED, endedAt = Instant.now())
            .toResponse()

        sseBroadcaster.publish(
            channel = SseChannels.sessions(),
            event = SseEvent.SESSION_STATUS_UPDATED,
            data = Json.encodeToString(response),
        )

        return response
    }

    suspend fun getSession(sessionId: SessionId): SessionResponse =
        sessionRepository.findById(sessionId.value)?.toResponse()
            ?: throw NotFoundException("Session not found")

    suspend fun joinAsAdditionalSupervisor(
        code: SessionCode,
        professorId: ProfessorId,
    ): SessionResponse {
        val session = sessionRepository.findByCode(code.value)
            ?: throw NotFoundException("Session not found")

        if (session.status == SessionStatus.ENDED)
            throw ConflictException("Cannot join a session that has already ended")

        sessionRepository.addSupervisor(session.id, professorId.value)

        return session.toResponse()
    }

    suspend fun canSupervise(
        sessionId: SessionId,
        professorId: ProfessorId,
    ): Boolean =
        sessionRepository.isSupervisor(sessionId.value, professorId.value)

    suspend fun getActiveSessions(): List<SessionResponse> =
        sessionRepository.findAllActive().map { it.toResponse() }

    suspend fun getCurrentSession(professorId: ProfessorId): SessionResponse? =
        sessionRepository
            .findLatestOpenBySupervisor(professorId.value)
            ?.toResponse()

    private fun generateSessionCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

        return (1..6)
            .map { chars.random() }
            .joinToString("")
    }
}

private fun SessionRecord.toResponse() = SessionResponse(
    id = id.toString(),
    examId = examId.toString(),
    supervisorId = supervisorId.toString(),
    code = code,
    status = status.name,
    startedAt = startedAt?.toString(),
    endedAt = endedAt?.toString(),
)