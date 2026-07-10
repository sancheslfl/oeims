package com.oeims.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.oeims.config.TestEnvironment
import com.oeims.connections.SentinelWebSocketManager
import com.oeims.connections.SseBroadcaster
import com.oeims.models.*
import com.oeims.repositories.interfaces.IParticipantRepository
import com.oeims.repositories.interfaces.ISessionRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class ParticipantServiceTest {
    private class FakeParticipantRepository : IParticipantRepository {
        val participants = mutableListOf<ParticipantRecord>()

        override suspend fun findById(id: UUID): ParticipantRecord? = participants.find { it.id == id }
        override suspend fun findBySession(sessionId: UUID): List<ParticipantRecord> = participants.filter { it.sessionId == sessionId }
        override suspend fun findByExamIdentityCode(examIdentityCode: String): ParticipantRecord? = participants.find { it.examIdentityCode == examIdentityCode }
        override suspend fun findByEmailAndSession(email: String, sessionId: UUID): ParticipantRecord? = participants.find { it.email == email && it.sessionId == sessionId }
        override suspend fun findConnectedBySession(sessionId: UUID): List<ParticipantRecord> = participants.filter { it.sessionId == sessionId && it.connectionStatus == ConnectionStatus.CONNECTED }

        override suspend fun create(sessionId: UUID, email: String): ParticipantRecord {
            val record = ParticipantRecord(UUID.randomUUID(), sessionId, email, null, ConnectionStatus.DISCONNECTED, null, Instant.now())
            participants.add(record)
            return record
        }

        override suspend fun updateHeartbeat(id: UUID): Boolean = update(id) {
            it.copy(connectionStatus = ConnectionStatus.CONNECTED, lastHeartbeat = Instant.now())
        }

        override suspend fun updateConnectionStatus(id: UUID, status: ConnectionStatus): Boolean = update(id) {
            it.copy(connectionStatus = status)
        }

        override suspend fun updateTimedOut(threshold: Instant): List<ParticipantRecord> = emptyList()

        override suspend fun updateExamIdentityCode(participantId: UUID, examIdentityCode: String): Boolean = update(participantId) {
            if (it.examIdentityCode != null) it else it.copy(examIdentityCode = examIdentityCode)
        }

        private fun update(id: UUID, transform: (ParticipantRecord) -> ParticipantRecord): Boolean {
            val index = participants.indexOfFirst { it.id == id }
            if (index == -1) return false
            participants[index] = transform(participants[index])
            return true
        }
    }

    private class FakeSessionRepository : ISessionRepository {
        val sessions = mutableMapOf<UUID, SessionRecord>()
        val joins = mutableListOf<SessionJoinRecord>()

        override suspend fun findById(id: UUID): SessionRecord? = sessions[id]
        override suspend fun findByCode(code: String): SessionRecord? = sessions.values.find { it.code == code }
        override suspend fun findBySupervisor(supervisorId: UUID): List<SessionRecord> = emptyList()
        override suspend fun findLatestOpenBySupervisor(supervisorId: UUID): SessionRecord? = null
        override suspend fun create(examId: UUID, supervisorId: UUID, code: String, allowedEmailDomain: String): SessionRecord? = null
        override suspend fun updateStatus(id: UUID, status: SessionStatus): Boolean = false
        override suspend fun addSupervisor(sessionId: UUID, userId: UUID) = Unit
        override suspend fun isSupervisor(sessionId: UUID, userId: UUID): Boolean = false
        override suspend fun findAllActive(): List<SessionRecord> = emptyList()

        override suspend fun createJoinRequest(sessionId: UUID, email: String, jwtId: String, expiresAt: Instant): SessionJoinRecord {
            val record = SessionJoinRecord(UUID.randomUUID(), sessionId, email, jwtId, expiresAt, null, Instant.now())
            joins.add(record)
            return record
        }

        override suspend fun findJoinRequestByJwtId(jwtId: String): SessionJoinRecord? = joins.find { it.jwtId == jwtId }

        override suspend fun updateJoinVerification(id: UUID, verifiedAt: Instant): Boolean {
            val index = joins.indexOfFirst { it.id == id }
            if (index == -1 || joins[index].verifiedAt != null) return false
            joins[index] = joins[index].copy(verifiedAt = verifiedAt)
            return true
        }
    }

    private class FakeEmailSender : EmailSender {
        var sentCount = 0
        var lastLink: String? = null

        override suspend fun sendJoinVerification(to: String, verificationLink: String, expiresAt: Instant) {
            sentCount++
            lastLink = verificationLink
        }
    }

    private lateinit var participants: FakeParticipantRepository
    private lateinit var sessions: FakeSessionRepository
    private lateinit var emailSender: FakeEmailSender
    private lateinit var service: ParticipantService
    private lateinit var sessionId: UUID

    private val code = "ABC123"
    private val studentEmail = "student1@alunos.isel.pt"
    private val algorithm = Algorithm.HMAC256("test-secret-key")
    private val jwtSettings = SessionJwtSettings(
        emailVerification = JwtSettings(
            issuer = "oeims-test",
            audience = "email-verification",
            realm = "email-verification-realm",
            expiration = Duration.ofMinutes(15),
            algorithm = algorithm,
            purpose = "email_verification",
        ),
        sentinel = JwtSettings(
            issuer = "oeims-test",
            audience = "sentinel",
            realm = "sentinel-realm",
            expiration = Duration.ofHours(4),
            algorithm = algorithm,
        ),
    )

    @BeforeEach
    fun setup() {
        TestEnvironment.configure()
        participants = FakeParticipantRepository()
        sessions = FakeSessionRepository()
        emailSender = FakeEmailSender()
        service = ParticipantService(
            participantRepository = participants,
            sessionRepository = sessions,
            jwtSettings = jwtSettings,
            sseBroadcaster = SseBroadcaster(),
            webSocketManager = SentinelWebSocketManager(json = Json { encodeDefaults = true }),
            emailSender = emailSender,
        )
        sessionId = UUID.randomUUID()
        putSession(SessionStatus.PENDING)
    }

    private fun putSession(status: SessionStatus) {
        sessions.sessions[sessionId] = SessionRecord(
            id = sessionId,
            examId = UUID.randomUUID(),
            supervisorId = UUID.randomUUID(),
            code = code,
            allowedEmailDomain = "alunos.isel.pt",
            status = status,
            createdAt = Instant.now(),
            startedAt = if (status != SessionStatus.PENDING) Instant.now() else null,
            endedAt = if (status == SessionStatus.ENDED) Instant.now() else null,
        )
    }

    private fun capturedToken(): JwtToken {
        val link = emailSender.lastLink ?: error("No verification email was sent")
        val encoded = link.substringAfter("token=")
        return JwtToken(URLDecoder.decode(encoded, StandardCharsets.UTF_8))
    }

    @Test
    fun `requestJoin sends a verification email and records the pending join`() = runBlocking {
        val response = service.requestJoin(code.toSessionCode(), Email(studentEmail))

        assertEquals("Verification email sent", response.message)
        assertEquals(1, emailSender.sentCount)
        assertEquals(studentEmail, sessions.joins.single().email)
        assertNull(sessions.joins.single().verifiedAt)
    }

    @Test
    fun `requestJoin returns already joined when participant exists`() = runBlocking {
        participants.create(sessionId, studentEmail)

        val response = service.requestJoin(code.toSessionCode(), Email(studentEmail))

        assertEquals("This email has already joined this session", response.message)
        assertEquals(0, emailSender.sentCount)
    }

    @Test
    fun `requestJoin validates session and email domain`() {
        assertThrows<NotFoundException> {
            runBlocking { service.requestJoin("ZZZZZZ".toSessionCode(), Email(studentEmail)) }
        }
        assertThrows<ForbiddenException> {
            runBlocking { service.requestJoin(code.toSessionCode(), Email("intruder@gmail.com")) }
        }
    }

    @Test
    fun `requestJoin rejects ended sessions`() {
        putSession(SessionStatus.ENDED)

        assertThrows<ConflictException> {
            runBlocking { service.requestJoin(code.toSessionCode(), Email(studentEmail)) }
        }
    }

    @Test
    fun `verifyJoin creates the participant marks token used and returns a sentinel token`() = runBlocking {
        service.requestJoin(code.toSessionCode(), Email(studentEmail))

        val response = service.verifyJoin(capturedToken())
        val created = participants.findByEmailAndSession(studentEmail, sessionId)
        val decoded = JWT.decode(response.token)

        assertNotNull(created)
        assertEquals(created.id.toString(), response.participantId)
        assertEquals(created.id.toString(), decoded.getClaim("participantId").asString())
        assertEquals("STUDENT", decoded.getClaim("role").asString())
        assertNotNull(sessions.joins.single().verifiedAt)
    }

    @Test
    fun `verifyJoin issues an exam identity code when session is active`() = runBlocking {
        putSession(SessionStatus.ACTIVE)
        service.requestJoin(code.toSessionCode(), Email(studentEmail))

        val response = service.verifyJoin(capturedToken())
        val participant = participants.findById(UUID.fromString(response.participantId))

        assertNotNull(participant?.examIdentityCode)
    }

    @Test
    fun `verifyJoin rejects reused unknown ended and invalid tokens`() = runBlocking {
        service.requestJoin(code.toSessionCode(), Email(studentEmail))
        val token = capturedToken()
        service.verifyJoin(token)

        assertThrows<ConflictException> { service.verifyJoin(token) }

        sessions.joins.clear()
        assertThrows<NotFoundException> { service.verifyJoin(token) }

        service.requestJoin(code.toSessionCode(), Email("student2@alunos.isel.pt"))
        val endedToken = capturedToken()
        putSession(SessionStatus.ENDED)
        assertThrows<ConflictException> { service.verifyJoin(endedToken) }

        val forged = JWT.create()
            .withIssuer("oeims-test")
            .withAudience("email-verification")
            .withJWTId(UUID.randomUUID().toString())
            .withSubject(studentEmail)
            .withClaim("purpose", "email_verification")
            .withClaim("sessionCode", code)
            .sign(Algorithm.HMAC256("different-secret"))

        assertThrows<UnauthorizedException> { service.verifyJoin(JwtToken(forged)) }
    }

    @Test
    fun `getParticipants returns all participants in a session`() = runBlocking {
        participants.create(sessionId, studentEmail)
        participants.create(sessionId, "student2@alunos.isel.pt")

        val results = service.getParticipants(sessionId.toSessionId())

        assertEquals(2, results.size)
        assertTrue(results.all { it.sessionId == sessionId.toString() })
    }

    @Test
    fun `getParticipantById returns participant or null`() = runBlocking {
        val created = participants.create(sessionId, studentEmail)

        val found = service.getParticipantById(created.id.toParticipantId())
        val missing = service.getParticipantById(UUID.randomUUID().toParticipantId())

        assertEquals(studentEmail, found?.email)
        assertNull(missing)
    }
}
