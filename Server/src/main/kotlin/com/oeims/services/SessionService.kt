package com.oeims.services

import com.auth0.jwt.JWT
import com.auth0.jwt.exceptions.JWTVerificationException
import com.oeims.exceptions.ConflictException
import com.oeims.exceptions.ForbiddenException
import com.oeims.exceptions.NotFoundException
import com.oeims.models.*
import com.oeims.models.dto.*
import com.oeims.models.ids.*
import com.oeims.repositories.ParticipantRecord
import com.oeims.repositories.SessionRecord
import com.oeims.repositories.interfaces.IExamRepository
import com.oeims.repositories.interfaces.IParticipantRepository
import com.oeims.repositories.interfaces.ISessionRepository
import com.oeims.sse.SseBroadcaster
import com.oeims.sse.SseChannels
import com.oeims.sse.SseEvent
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.*

class SessionService(
    private val sessionRepository: ISessionRepository,
    private val examRepository: IExamRepository,
    private val participantRepository: IParticipantRepository,
    private val jwtSettings: JwtSettings,
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
                    data = Json.encodeToString(response)
                )

                return response
            }
        }

        throw IllegalStateException("Failed to generate a unique session code after 5 attempts")
    }

    suspend fun startSession(sessionId: SessionId, professorId: ProfessorId): SessionResponse {
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
            data = Json.encodeToString(response)
        )

        return response
    }

    suspend fun endSession(sessionId: SessionId, professorId: ProfessorId): SessionResponse {
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
            data = Json.encodeToString(response)
        )

        return response
    }

    suspend fun requestJoin(code: SessionCode, email: Email): EmailJoinResponse {
        val session = sessionRepository.findByCode(code.value)
            ?: throw NotFoundException("Session not found")

        if (session.status == SessionStatus.ENDED)
            throw ConflictException("Session has already ended")

        val allowedDomain = session.allowedEmailDomain.toAllowedEmailDomain()
        if (!allowedDomain.allows(email))
            throw ForbiddenException("Email domain is not allowed for this session")

        val existingParticipant = participantRepository.findByEmailAndSession(
            email = email.address,
            sessionId = session.id,
        )

        if (existingParticipant != null)
            return EmailJoinResponse("This email has already joined this session")

        val token = createJoinToken(
            session = session,
            email = email,
        )

        sessionRepository.createEmailJoin(
            sessionId = session.id,
            email = email.address,
            jwtId = token.jwtId,
            expiresAt = token.expiresAt,
        )

        // ponytail: development-only delivery; replace with real email delivery later.
        println("OEIMS join verification link: http://localhost:5173/verify-join?token=${token.value}")

        return EmailJoinResponse(
            message = "Verification email sent"
        )
    }

    suspend fun verifyJoin(token: EmailJoinToken): VerifyJoinResponse {
        val verifiedToken = verifyJoinToken(token)

        val emailJoin = sessionRepository.findEmailJoinByJwtId(verifiedToken.jwtId)
            ?: throw NotFoundException("Invalid or expired join token")

        if (emailJoin.verifiedAt != null)
            throw ConflictException("Join token has already been used")

        if (emailJoin.expiresAt < Instant.now())
            throw ConflictException("Join token has expired")

        if (!emailJoin.email.equals(verifiedToken.email, ignoreCase = true))
            throw ConflictException("Join token does not match email")

        val session = sessionRepository.findById(emailJoin.sessionId)
            ?: throw NotFoundException("Session not found")

        if (session.code != verifiedToken.sessionCode)
            throw ConflictException("Join token does not match session")

        if (session.status == SessionStatus.ENDED)
            throw ConflictException("Session has already ended")

        val existingParticipant = participantRepository.findByEmailAndSession(
            email = emailJoin.email,
            sessionId = session.id,
        )

        val participant = existingParticipant ?: participantRepository.create(
            sessionId = session.id,
            email = emailJoin.email,
        )

        sessionRepository.updateEmailJoinVerification(
            id = emailJoin.id,
            verifiedAt = Instant.now(),
        )

        if (existingParticipant == null) {
            sseBroadcaster.publish(
                channel = SseChannels.session(session.id.toSessionId()),
                event = SseEvent.PARTICIPANT_JOINED,
                data = Json.encodeToString(participant.toResponse())
            )
        }

        return VerifyJoinResponse(
            participantId = participant.id.toString(),
            sessionId = session.id.toString(),
            email = participant.email,
            status = participant.connectionStatus.name,
        )
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

    suspend fun getActiveSessions(): List<SessionResponse> =
        sessionRepository.findAllActive().map { it.toResponse() }

    suspend fun getCurrentSession(professorId: ProfessorId): SessionResponse? =
        sessionRepository
            .findLatestOpenBySupervisor(professorId.value)
            ?.toResponse()

    suspend fun getParticipants(sessionId: SessionId): List<ParticipantResponse> {
        sessionRepository.findById(sessionId.value)
            ?: throw NotFoundException("Session not found")

        return participantRepository.findBySession(sessionId.value).map { it.toResponse() }
    }

    suspend fun heartbeat(participantId: ParticipantId) {
        val participant = participantRepository.findById(participantId.value)
            ?: throw NotFoundException("Participant not found")

        participantRepository.updateHeartbeat(participantId.value)

        if (participant.connectionStatus != ConnectionStatus.CONNECTED) {
            sseBroadcaster.publish(
                channel = SseChannels.session(participant.sessionId.toSessionId()),
                event = SseEvent.PARTICIPANT_STATUS_UPDATED,
                data = Json.encodeToString(
                    ParticipantStatusUpdate(
                        participant.id.toString(),
                        "CONNECTED"
                    )
                )
            )
        }
    }

    private fun createJoinToken(
        session: SessionRecord,
        email: Email,
    ): CreatedJoinToken {
        val jwtId = UUID.randomUUID().toString()
        val now = Instant.now()
        val expiresAt = now.plus(jwtSettings.expiration)

        val builder = JWT.create()
            .withIssuer(jwtSettings.issuer)
            .withAudience(jwtSettings.audience)
            .withJWTId(jwtId)
            .withSubject(email.address)
            .withIssuedAt(now)
            .withExpiresAt(expiresAt)
            .withClaim("sessionCode", session.code)

        val value = if (jwtSettings.purpose == null)
            builder.sign(jwtSettings.algorithm)
        else
            builder.withClaim("purpose", jwtSettings.purpose)
                .sign(jwtSettings.algorithm)

        return CreatedJoinToken(
            value = value,
            jwtId = jwtId,
            expiresAt = expiresAt,
        )
    }

    private fun verifyJoinToken(token: EmailJoinToken): VerifiedJoinToken {
        val jwt = try {
            jwtSettings.verifier.verify(token.value)
        } catch (_: JWTVerificationException) {
            throw ForbiddenException("Invalid or expired join token")
        }

        return VerifiedJoinToken(
            jwtId = jwt.id ?: throw ForbiddenException("Join token is missing id"),
            email = jwt.subject ?: throw ForbiddenException("Join token is missing email"),
            sessionCode = jwt.getClaim("sessionCode").asString()
                ?: throw ForbiddenException("Join token is missing session code"),
        )
    }

    private fun generateSessionCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

        return (1..6)
            .map { chars.random() }
            .joinToString("")
    }

    private data class CreatedJoinToken(
        val value: String,
        val jwtId: String,
        val expiresAt: Instant,
    )

    private data class VerifiedJoinToken(
        val jwtId: String,
        val email: String,
        val sessionCode: String,
    )
}

private fun SessionRecord.toResponse() = SessionResponse(
    id = id.toString(),
    examId = examId.toString(),
    supervisorId = supervisorId.toString(),
    code = code,
    status = status.name,
    startedAt = startedAt?.toString(),
    endedAt = endedAt?.toString()
)

private fun ParticipantRecord.toResponse() = ParticipantResponse(
    id = id.toString(),
    sessionId = sessionId.toString(),
    userId = userId.toString(),
    email = email,
    connectionStatus = connectionStatus.name,
    lastHeartbeat = lastHeartbeat?.toString(),
    joinedAt = joinedAt.toString()
)