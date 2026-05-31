package com.oeims.services

import com.oeims.exceptions.ConflictException
import com.oeims.exceptions.ForbiddenException
import com.oeims.exceptions.NotFoundException
import com.oeims.models.SessionCode
import com.oeims.models.SessionStatus
import com.oeims.models.dto.JoinSessionResponse
import com.oeims.models.dto.ParticipantResponse
import com.oeims.models.dto.SessionResponse
import com.oeims.models.ids.*
import com.oeims.models.toSessionCode
import com.oeims.repositories.ParticipantRecord
import com.oeims.repositories.SessionRecord
import com.oeims.repositories.interfaces.IExamRepository
import com.oeims.repositories.interfaces.IParticipantRepository
import com.oeims.repositories.interfaces.ISessionRepository
import com.oeims.repositories.interfaces.IUserRepository
import com.oeims.sse.SseBroadcaster
import com.oeims.sse.SseChannels
import com.oeims.sse.SseEvent
import kotlinx.serialization.json.Json
import java.time.Instant

class SessionService(
    private val sessionRepository: ISessionRepository,
    private val examRepository: IExamRepository,
    private val participantRepository: IParticipantRepository,
    private val userRepository: IUserRepository,
    private val sseBroadcaster: SseBroadcaster,
) {

    suspend fun createSession(professorId: ProfessorId, examId: ExamId): SessionResponse {
        examRepository.findById(examId.value)
            ?: throw NotFoundException("Exam not found")

        val code = generateUniqueCode()
        return sessionRepository
            .create(examId.value, professorId.value, code.value)
            .toResponse()
    }

    suspend fun startSession(sessionId: SessionId, professorId: ProfessorId): SessionResponse {
        val session = sessionRepository.findById(sessionId.value)
            ?: throw NotFoundException("Session not found")

        if (session.supervisorId != professorId.value)
            throw ForbiddenException("Only the session supervisor can start it")

        if (session.status != SessionStatus.PENDING)
            throw ConflictException("Only a pending session can be started")

        sessionRepository.updateStatus(sessionId.value, SessionStatus.ACTIVE)
        return session.copy(status = SessionStatus.ACTIVE, startedAt = Instant.now()).toResponse()
    }

    suspend fun endSession(sessionId: SessionId, professorId: ProfessorId): SessionResponse {
        val session = sessionRepository.findById(sessionId.value)
            ?: throw NotFoundException("Session not found")

        if (session.supervisorId != professorId.value)
            throw ForbiddenException("Only the session supervisor can end it")

        if (session.status != SessionStatus.ACTIVE)
            throw ConflictException("Only an active session can be ended")

        sessionRepository.updateStatus(sessionId.value, SessionStatus.ENDED)
        return session.copy(status = SessionStatus.ENDED, endedAt = Instant.now()).toResponse()
    }

    suspend fun joinSession(code: SessionCode, studentId: StudentId): JoinSessionResponse {
        val session = sessionRepository.findByCode(code.value)
            ?: throw NotFoundException("Session not found")

        if (session.status == SessionStatus.ENDED)
            throw ConflictException("Session has already ended")

        userRepository.findById(studentId.value)
            ?: throw NotFoundException("User not found")

        val existing = participantRepository.findByUserAndSession(studentId.value, session.id)
        if (existing != null)
            return buildJoinResponse(existing, session)

        val participant = participantRepository.create(session.id, studentId.value)

        sseBroadcaster.publish(
            channel = SseChannels.session(session.id.toSessionId()),
            event = SseEvent.PARTICIPANT_JOINED,
            data = Json.encodeToString(participant.toResponse())
        )

        return buildJoinResponse(participant, session)
    }

    suspend fun getSession(sessionId: SessionId): SessionResponse =
        sessionRepository.findById(sessionId.value)?.toResponse()
            ?: throw NotFoundException("Session not found")

    suspend fun joinAsAdditionalSupervisor(code: SessionCode, professorId: ProfessorId): SessionResponse {
        val session = sessionRepository.findByCode(code.value)
            ?: throw NotFoundException("Session not found")

        if (session.status == SessionStatus.ENDED)
            throw ConflictException("Cannot join a session that has already ended")

        sessionRepository.addSupervisor(session.id, professorId.value)
        return session.toResponse()
    }

    suspend fun canSupervise(sessionId: SessionId, professorId: ProfessorId): Boolean =
        sessionRepository.isSupervisor(sessionId.value, professorId.value)

    suspend fun getCurrentSession(professorId: ProfessorId): SessionResponse? =
        sessionRepository
            .findLatestOpenBySupervisor(professorId.value)
            ?.toResponse()

    suspend fun getParticipants(sessionId: SessionId): List<ParticipantResponse> {
        sessionRepository.findById(sessionId.value)
            ?: throw NotFoundException("Session not found")

        return participantRepository.findBySession(sessionId.value).map { it.toResponse() }
    }

    suspend fun heartbeat(participantId: ParticipantId, userId: UserId) {
        val participant = participantRepository.findById(participantId.value)
            ?: throw NotFoundException("Participant not found")
        if (participant.userId != userId.value)
            throw ForbiddenException("You do not own this participant")
        participantRepository.updateHeartbeat(participantId.value)
    }

    private suspend fun generateUniqueCode(): SessionCode {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        repeat(5) {
            val code = (1..6).map { chars.random() }.joinToString("")
            if (sessionRepository.findByCode(code) == null)
                return code.toSessionCode()
        }
        throw IllegalStateException("Failed to generate a unique session code after 5 attempts")
    }

    private suspend fun buildJoinResponse(participant: ParticipantRecord, session: SessionRecord): JoinSessionResponse {
        val exam = examRepository.findById(session.examId)
            ?: throw NotFoundException("Exam not found")

        return JoinSessionResponse(
            participantId = participant.id.toString(),
            sessionId     = session.id.toString(),
            examTitle     = exam.title,
            durationMins  = exam.durationMins
        )
    }
}

private fun SessionRecord.toResponse() = SessionResponse(
    id           = id.toString(),
    examId       = examId.toString(),
    supervisorId = supervisorId.toString(),
    code         = code,
    status       = status.name,
    startedAt    = startedAt?.toString(),
    endedAt      = endedAt?.toString()
)

private fun ParticipantRecord.toResponse() = ParticipantResponse(
    id               = id.toString(),
    sessionId        = sessionId.toString(),
    userId           = userId.toString(),
    email            = email,
    connectionStatus = connectionStatus.name,
    lastHeartbeat    = lastHeartbeat?.toString(),
    joinedAt         = joinedAt.toString()
)
