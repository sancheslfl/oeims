package com.oeims.services

import com.oeims.dto.JoinSessionResponse
import com.oeims.dto.ParticipantResponse
import com.oeims.dto.SessionResponse
import com.oeims.exceptions.ConflictException
import com.oeims.exceptions.ForbiddenException
import com.oeims.exceptions.NotFoundException
import com.oeims.models.SessionStatus
import com.oeims.repositories.ParticipantRecord
import com.oeims.repositories.SessionRecord
import com.oeims.repositories.interfaces.IExamRepository
import com.oeims.repositories.interfaces.IParticipantRepository
import com.oeims.repositories.interfaces.ISessionRepository
import com.oeims.repositories.interfaces.IUserRepository
import java.util.UUID

class SessionService(
    private val sessionRepository: ISessionRepository,
    private val examRepository: IExamRepository,
    private val participantRepository: IParticipantRepository,
    private val userRepository: IUserRepository
) {

    suspend fun createSession(professorId: UUID, examId: UUID): SessionResponse {
        examRepository.findById(examId)
            ?: throw NotFoundException("Exam not found")

        val code = generateUniqueCode()
        return sessionRepository.create(examId, professorId, code).toResponse()
    }

    suspend fun startSession(sessionId: UUID, professorId: UUID): SessionResponse {
        val session = sessionRepository.findById(sessionId)
            ?: throw NotFoundException("Session not found")

        if (session.supervisorId != professorId)
            throw ForbiddenException("Only the session supervisor can start it")

        if (session.status != SessionStatus.PENDING)
            throw ConflictException("Only a pending session can be started")

        sessionRepository.updateStatus(sessionId, SessionStatus.ACTIVE)
        return sessionRepository.findById(sessionId)!!.toResponse()
    }

    suspend fun endSession(sessionId: UUID, professorId: UUID): SessionResponse {
        val session = sessionRepository.findById(sessionId)
            ?: throw NotFoundException("Session not found")

        if (session.supervisorId != professorId)
            throw ForbiddenException("Only the session supervisor can end it")

        if (session.status != SessionStatus.ACTIVE)
            throw ConflictException("Only an active session can be ended")

        sessionRepository.updateStatus(sessionId, SessionStatus.ENDED)
        return sessionRepository.findById(sessionId)!!.toResponse()
    }

    suspend fun joinSession(code: String, studentId: UUID): JoinSessionResponse {
        val session = sessionRepository.findByCode(code)
            ?: throw NotFoundException("Session not found")

        if (session.status == SessionStatus.ENDED)
            throw ConflictException("Session has already ended")

        userRepository.findById(studentId)
            ?: throw NotFoundException("User not found")

        // Idempotent — return existing participant if already joined
        val existing = participantRepository.findByUserAndSession(studentId, session.id)
        if (existing != null)
            return buildJoinResponse(existing, session)

        val participant = participantRepository.create(session.id, studentId)
        return buildJoinResponse(participant, session)
    }

    suspend fun getSession(sessionId: UUID): SessionResponse =
        sessionRepository.findById(sessionId)?.toResponse()
            ?: throw NotFoundException("Session not found")

    suspend fun getParticipants(sessionId: UUID): List<ParticipantResponse> {
        sessionRepository.findById(sessionId)
            ?: throw NotFoundException("Session not found")

        return participantRepository.findBySession(sessionId).map { it.toResponse() }
    }

    suspend fun heartbeat(participantId: UUID, userId: UUID) {
        val participant = participantRepository.findById(participantId)
            ?: throw NotFoundException("Participant not found")
        if (participant.userId != userId)
            throw ForbiddenException("You do not own this participant")
        participantRepository.updateHeartbeat(participantId)
    }

    private suspend fun generateUniqueCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        repeat(5) {
            val code = (1..6).map { chars.random() }.joinToString("")
            if (sessionRepository.findByCode(code) == null)
                return code
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
}
