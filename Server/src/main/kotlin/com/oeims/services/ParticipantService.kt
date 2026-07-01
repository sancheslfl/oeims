package com.oeims.services

import com.auth0.jwt.JWT
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.exceptions.TokenExpiredException
import com.oeims.config.Environment
import com.oeims.connections.SseBroadcaster
import com.oeims.connections.SseChannels
import com.oeims.connections.SseEvent
import com.oeims.connections.WebSocketBroadcaster
import com.oeims.connections.WebSocketMessageTypes
import com.oeims.connections.WebSocketOutboundMessage
import com.oeims.models.*
import com.oeims.models.dto.EmailJoinResponse
import com.oeims.models.dto.ParticipantResponse
import com.oeims.models.dto.ParticipantStatusUpdate
import com.oeims.models.dto.VerifyJoinResponse
import com.oeims.models.ParticipantId
import com.oeims.models.SessionId
import com.oeims.models.toSessionId
import com.oeims.repositories.interfaces.IParticipantRepository
import com.oeims.repositories.interfaces.ISessionRepository
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant

class ParticipantService(
    private val participantRepository: IParticipantRepository,
    private val sessionRepository: ISessionRepository,
    private val jwtSettings: SessionJwtSettings,
    private val sseBroadcaster: SseBroadcaster,
    private val webSocketBroadcaster: WebSocketBroadcaster,
    private val emailSender: EmailSender,
) {

    suspend fun getParticipantById(id: ParticipantId): ParticipantResponse? =
        participantRepository.findById(id.value)?.toResponse()

    suspend fun requestJoin(
        code: SessionCode,
        email: Email,
    ): EmailJoinResponse {
        val session = sessionRepository.findByCode(code.value)
            ?: throw NotFoundException("Session not found")

        if (session.status == SessionStatus.ENDED) {
            throw ConflictException("Session has already ended")
        }

        val allowedDomain = session.allowedEmailDomain.toAllowedEmailDomain()
        if (!allowedDomain.allows(email)) {
            throw ForbiddenException("Email domain is not allowed for this session")
        }

        val existingParticipant = participantRepository.findByEmailAndSession(
            email = email.address,
            sessionId = session.id,
        )

        if (existingParticipant != null) {
            return EmailJoinResponse("This email has already joined this session")
        }

        val token = createJoinToken(
            session = session,
            email = email,
        )

        sessionRepository.createJoinRequest(
            sessionId = session.id,
            email = email.address,
            jwtId = token.jwtId,
            expiresAt = token.expiresAt,
        )

        val encodedToken = URLEncoder.encode(
            token.value,
            StandardCharsets.UTF_8,
        )

        val verificationLink =
            "${Environment.frontendBaseUrl}/student/join/verify?token=$encodedToken"

        emailSender.sendJoinVerification(
            to = email.address,
            verificationLink = verificationLink,
            expiresAt = token.expiresAt,
        )

        return EmailJoinResponse(message = "Verification email sent")
    }

    suspend fun verifyJoin(emailToken: JwtToken): VerifyJoinResponse {
        val verifiedToken = verifyEmailToken(emailToken)

        val joinAttempt = sessionRepository.findJoinRequestByJwtId(verifiedToken.jwtId)
            ?: throw NotFoundException("It seems the token may be invalid or expired. Please try again")

        if (joinAttempt.verifiedAt != null) {
            throw ConflictException("This token has already been used")
        }

        val session = sessionRepository.findById(joinAttempt.sessionId)
            ?: throw NotFoundException("The respective session does not exist anymore")

        if (session.status == SessionStatus.ENDED) {
            throw ConflictException("Session has already ended")
        }

        val isNotUpdated = !sessionRepository.updateJoinVerification(
            id = joinAttempt.id,
            verifiedAt = Instant.now(),
        )

        if (isNotUpdated) {
            throw ConflictException("This token has already been used")
        }

        val existingParticipant = participantRepository.findByEmailAndSession(
            email = joinAttempt.email,
            sessionId = session.id,
        )

        val participant = existingParticipant ?: participantRepository.create(
            sessionId = session.id,
            email = joinAttempt.email,
        )

        if (existingParticipant == null) {
            sseBroadcaster.publish(
                channel = SseChannels.session(session.id.toSessionId()),
                event = SseEvent.PARTICIPANT_JOINED,
                data = Json.encodeToString(participant.toResponse()),
            )
        }

        if (session.status == SessionStatus.ACTIVE) {
            sendExamIdentityCode(participant)
        }

        return VerifyJoinResponse(
            token = createSentinelToken(participant),
            participantId = participant.id.toString(),
        )
    }

    suspend fun getParticipants(sessionId: SessionId): List<ParticipantResponse> {
        sessionRepository.findById(sessionId.value)
            ?: throw NotFoundException("Session not found")

        return participantRepository.findBySession(sessionId.value).map { it.toResponse() }
    }

    suspend fun sendHeartbeat(
        pid: ParticipantId,
        authenticatedPid: ParticipantId,
    ) {
        val participant = participantRepository.findById(pid.value)
            ?: throw NotFoundException("Participant not found")

        if (participant.id != authenticatedPid.value) {
            throw ForbiddenException("Forbidden")
        }

        participantRepository.updateHeartbeat(pid.value)

        if (participant.connectionStatus != ConnectionStatus.CONNECTED) {
            sseBroadcaster.publish(
                channel = SseChannels.session(participant.sessionId.toSessionId()),
                event = SseEvent.PARTICIPANT_STATUS_UPDATED,
                data = Json.encodeToString(
                    ParticipantStatusUpdate(
                        participant.id.toString(),
                        "CONNECTED",
                    )
                ),
            )
        }
    }

    suspend fun sendExamIdentityCode(participant: ParticipantRecord) {
        val examIdentityCode = getOrCreateExamIdentityCode(participant)

        webSocketBroadcaster.send(
            participantId = participant.id,
            message = WebSocketOutboundMessage(
                type = WebSocketMessageTypes.EXAM_IDENTITY_CODE,
                data = examIdentityCode.value,
            ),
        )
    }

    suspend fun sendAllExamIdentityCodes(sessionId: SessionId) {
        val participants = participantRepository.findConnectedBySession(sessionId.value)

        for (participant in participants) {
            sendExamIdentityCode(participant)
        }
    }

    private suspend fun getOrCreateExamIdentityCode(
        participant: ParticipantRecord,
    ): ExamIdentityCode {

        if (participant.examIdentityCode != null) {
            return participant.examIdentityCode.toExamIdentityCode()
        }

        repeat(10) {
            val examIdentityCode = ExamIdentityCode.generate()

            if (
                participantRepository.updateExamIdentityCode(
                    participantId = participant.id,
                    examIdentityCode = examIdentityCode.value,
                )
            ) {
                return examIdentityCode
            }

            val updatedParticipant = participantRepository.findById(participant.id)
            if (updatedParticipant?.examIdentityCode != null) {
                return updatedParticipant.examIdentityCode.toExamIdentityCode()
            }
        }

        throw IllegalStateException("Failed to generate a unique exam identity code")
    }

    private fun createJoinToken(
        session: SessionRecord,
        email: Email,
    ): CreatedJoinToken {
        val settings = jwtSettings.emailVerification
        val jwtId = java.util.UUID.randomUUID().toString()
        val now = Instant.now()
        val expiresAt = now.plus(settings.expiration)

        val builder = JWT.create()
            .withIssuer(settings.issuer)
            .withAudience(settings.audience)
            .withJWTId(jwtId)
            .withSubject(email.address)
            .withIssuedAt(now)
            .withExpiresAt(expiresAt)
            .withClaim("sessionCode", session.code)

        val value = if (settings.purpose == null)
            builder.sign(settings.algorithm)
        else
            builder.withClaim("purpose", settings.purpose)
                .sign(settings.algorithm)

        return CreatedJoinToken(
            value = value,
            jwtId = jwtId,
            expiresAt = expiresAt,
        )
    }

    private fun verifyEmailToken(emailToken: JwtToken): VerifiedJoinToken {
        val jwt = try {
            jwtSettings.emailVerification.verifier.verify(emailToken.value)
        } catch (_: TokenExpiredException) {
            throw ConflictException("This token has expired. Please request another email verification and try again")
        } catch (_: JWTVerificationException) {
            throw UnauthorizedException("This token is invalid. Please use the latest verification link sent to your email")
        } catch (_: IllegalArgumentException) {
            throw ValidationException("Malformed verification token")
        }

        return VerifiedJoinToken(
            jwtId = jwt.id ?: throw ForbiddenException("Join token is missing id"),
            email = jwt.subject ?: throw ForbiddenException("Join token is missing email"),
            sessionCode = jwt.getClaim("sessionCode").asString()
                ?: throw ForbiddenException("Join token is missing session code"),
        )
    }

    private fun createSentinelToken(participant: ParticipantRecord): String {
        val settings = jwtSettings.sentinel
        val now = Instant.now()

        return JWT.create()
            .withIssuer(settings.issuer)
            .withAudience(settings.audience)
            .withIssuedAt(now)
            .withExpiresAt(now.plus(settings.expiration))
            .withClaim("role", "STUDENT")
            .withClaim("participantId", participant.id.toString())
            .sign(settings.algorithm)
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