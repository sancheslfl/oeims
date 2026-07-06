package com.oeims.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.oeims.config.Environment
import com.oeims.connections.SseBroadcaster
import com.oeims.models.ConnectionStatus
import com.oeims.models.EmailSender
import com.oeims.models.ParticipantRecord
import com.oeims.models.SessionJoinRecord
import com.oeims.models.SessionRecord
import com.oeims.models.SessionStatus
import com.oeims.repositories.interfaces.IParticipantRepository
import com.oeims.repositories.interfaces.ISessionRepository
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.NotFoundException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParticipantServiceTest {

    // ── Fakes ─────────────────────────────────────────────────────────────────

    private class FakeParticipantRepository : IParticipantRepository {
        val participants = mutableListOf<ParticipantRecord>()

        override suspend fun findById(id: UUID): ParticipantRecord? = participants.find { it.id == id }
        override suspend fun findBySession(sessionId: UUID): List<ParticipantRecord> =
            participants.filter { it.sessionId == sessionId }

        override suspend fun findByExamIdentityCode(examIdentityCode: String): ParticipantRecord? =
            participants.find { it.examIdentityCode == examIdentityCode }

        override suspend fun findByEmailAndSession(email: String, sessionId: UUID): ParticipantRecord? =
            participants.find { it.email == email && it.sessionId == sessionId }

        override suspend fun findConnectedBySession(sessionId: UUID): List<ParticipantRecord> =
            participants.filter { it.sessionId == sessionId && it.connectionStatus == ConnectionStatus.CONNECTED }

        override suspend fun create(sessionId: UUID, email: String): ParticipantRecord {
            val record = ParticipantRecord(
                id = UUID.randomUUID(),
                sessionId = sessionId,
                email = email,
                examIdentityCode = null,
                connectionStatus = ConnectionStatus.DISCONNECTED,
                lastHeartbeat = null,
                joinedAt = Instant.now()
            )
            participants.add(record)
            return record
        }

        override suspend fun updateHeartbeat(id: UUID): Boolean = participants.any { it.id == id }
        override suspend fun updateConnectionStatus(id: UUID, status: ConnectionStatus): Boolean =
            participants.any { it.id == id }

        override suspend fun updateTimedOut(threshold: Instant): List<ParticipantRecord> = emptyList()

        override suspend fun updateExamIdentityCode(participantId: UUID, examIdentityCode: String): Boolean {
            val idx = participants.indexOfFirst { it.id == participantId }
            if (idx == -1 || participants[idx].examIdentityCode != null) return false
            participants[idx] = participants[idx].copy(examIdentityCode = examIdentityCode)
            return true
        }
    }

    private class FakeSessionRepository : ISessionRepository {
        val sessions = mutableMapOf<UUID, SessionRecord>()
        val emailJoins = mutableListOf<SessionJoinRecord>()

        override suspend fun findById(id: UUID): SessionRecord? = sessions[id]
        override suspend fun findByCode(code: String): SessionRecord? = sessions.values.find { it.code == code }
        override suspend fun findBySupervisor(supervisorId: UUID): List<SessionRecord> = emptyList()
        override suspend fun findLatestOpenBySupervisor(supervisorId: UUID): SessionRecord? = null
        override suspend fun create(
            examId: UUID,
            supervisorId: UUID,
            code: String,
            allowedEmailDomain: String,
        ): SessionRecord = throw UnsupportedOperationException()

        override suspend fun updateStatus(id: UUID, status: SessionStatus): Boolean = false
        override suspend fun addSupervisor(sessionId: UUID, userId: UUID) {}
        override suspend fun isSupervisor(sessionId: UUID, userId: UUID): Boolean = false
        override suspend fun findAllActive(): List<SessionRecord> = emptyList()

        override suspend fun createEmailJoin(
            sessionId: UUID,
            email: String,
            jwtId: String,
            expiresAt: Instant,
        ): SessionJoinRecord {
            val record = SessionJoinRecord(
                id = UUID.randomUUID(),
                sessionId = sessionId,
                email = email,
                jwtId = jwtId,
                expiresAt = expiresAt,
                verifiedAt = null,
                createdAt = Instant.now(),
            )
            emailJoins.add(record)
            return record
        }

        override suspend fun findEmailJoinByJwtId(jwtId: String): SessionJoinRecord? =
            emailJoins.find { it.jwtId == jwtId }

        override suspend fun updateEmailJoinVerification(id: UUID, verifiedAt: Instant): Boolean {
            val idx = emailJoins.indexOfFirst { it.id == id }
            if (idx == -1 || emailJoins[idx].verifiedAt != null) return false
            emailJoins[idx] = emailJoins[idx].copy(verifiedAt = verifiedAt)
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

    // ── Setup ─────────────────────────────────────────────────────────────────

    private lateinit var fakeParticipants: FakeParticipantRepository
    private lateinit var fakeSessions: FakeSessionRepository
    private lateinit var fakeEmailSender: FakeEmailSender
    private lateinit var service: ParticipantService

    private lateinit var sessionId: UUID
    private val code = "ABC123"
    private val domain = "alunos.isel.pt"
    private val studentEmail = "student1@alunos.isel.pt"

    private val algorithm = Algorithm.HMAC256("test-secret-key")

    private val jwtSettings = SessionJwtSettings(
        emailJoin = JwtSettings(
            issuer = "oeims-test",
            audience = "email-join",
            realm = "email-join-realm",
            expiration = Duration.ofMinutes(15),
            algorithm = algorithm,
            purpose = "email_join",
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
        Environment.configure(MapApplicationConfig("app.frontend.base-url" to "http://localhost:5173"))

        fakeParticipants = FakeParticipantRepository()
        fakeSessions = FakeSessionRepository()
        fakeEmailSender = FakeEmailSender()

        service = ParticipantService(
            participantRepository = fakeParticipants,
            sessionRepository = fakeSessions,
            jwtSettings = jwtSettings,
            sseBroadcaster = SseBroadcaster(),
            webSocketBroadcaster = WebSocketBroadcaster(),
            emailSender = fakeEmailSender,
        )

        sessionId = UUID.randomUUID()
        putSession(SessionStatus.PENDING)
    }

    private fun putSession(status: SessionStatus) {
        sessions()[sessionId] = SessionRecord(
            id = sessionId,
            examId = UUID.randomUUID(),
            supervisorId = UUID.randomUUID(),
            code = code,
            allowedEmailDomain = domain,
            status = status,
            createdAt = Instant.now(),
            startedAt = if (status != SessionStatus.PENDING) Instant.now() else null,
            endedAt = if (status == SessionStatus.ENDED) Instant.now() else null,
        )
    }

    private fun sessions() = fakeSessions.sessions

    private fun capturedToken(): EmailJoinToken {
        val link = fakeEmailSender.lastLink ?: error("No verification email was sent")
        val encoded = link.substringAfter("token=")
        return URLDecoder.decode(encoded, StandardCharsets.UTF_8).toEmailJoinToken()
    }

    // ── requestJoin ────────────────────────────────────────────────────────────

    @Test
    fun `requestJoin sends a verification email and records the pending join`() = runBlocking {
        val response = service.requestJoin(code.toSessionCode(), Email(studentEmail))

        assertEquals("Verification email sent", response.message)
        assertEquals(1, fakeEmailSender.sentCount)
        assertEquals(1, fakeSessions.emailJoins.size)
        assertEquals(studentEmail, fakeSessions.emailJoins[0].email)
        assertNull(fakeSessions.emailJoins[0].verifiedAt)
    }

    @Test
    fun `requestJoin returns already-joined message and sends no email when participant exists`() = runBlocking {
        fakeParticipants.create(sessionId, studentEmail)

        val response = service.requestJoin(code.toSessionCode(), Email(studentEmail))

        assertEquals("This email has already joined this session", response.message)
        assertEquals(0, fakeEmailSender.sentCount)
        assertTrue(fakeSessions.emailJoins.isEmpty())
    }

    @Test
    fun `requestJoin throws NotFoundException when the code does not exist`() {
        assertThrows<NotFoundException> {
            runBlocking { service.requestJoin("ZZZZZZ".toSessionCode(), Email(studentEmail)) }
        }
    }

    @Test
    fun `requestJoin throws ConflictException when the session has ended`() {
        putSession(SessionStatus.ENDED)

        assertThrows<ConflictException> {
            runBlocking { service.requestJoin(code.toSessionCode(), Email(studentEmail)) }
        }
    }

    @Test
    fun `requestJoin throws ForbiddenException when the email domain is not allowed`() {
        assertThrows<ForbiddenException> {
            runBlocking { service.requestJoin(code.toSessionCode(), Email("intruder@gmail.com")) }
        }
    }

    // ── verifyJoin ───────────────────────────────────────────────────────────

    @Test
    fun `verifyJoin creates the participant and returns a sentinel token`() = runBlocking {
        service.requestJoin(code.toSessionCode(), Email(studentEmail))

        val response = service.verifyJoin(capturedToken())

        val created = fakeParticipants.findByEmailAndSession(studentEmail, sessionId)
        assertNotNull(created)
        assertEquals(created.id.toString(), response.participantId)

        val decoded = JWT.decode(response.token)
        assertEquals(created.id.toString(), decoded.getClaim("participantId").asString())
        assertEquals("STUDENT", decoded.getClaim("role").asString())
    }

    @Test
    fun `verifyJoin marks the email join as verified`(): Unit = runBlocking {
        service.requestJoin(code.toSessionCode(), Email(studentEmail))

        service.verifyJoin(capturedToken())

        assertNotNull(fakeSessions.emailJoins[0].verifiedAt)
    }

    @Test
    fun `verifyJoin issues an exam identity code when the session is ACTIVE`(): Unit = runBlocking {
        putSession(SessionStatus.ACTIVE)
        service.requestJoin(code.toSessionCode(), Email(studentEmail))

        val response = service.verifyJoin(capturedToken())

        val participant = fakeParticipants.findById(UUID.fromString(response.participantId))!!
        assertNotNull(participant.examIdentityCode)
    }

    @Test
    fun `verifyJoin throws ConflictException when the token was already used`() = runBlocking<Unit> {
        service.requestJoin(code.toSessionCode(), Email(studentEmail))
        val token = capturedToken()
        service.verifyJoin(token)

        assertThrows<ConflictException> {
            service.verifyJoin(token)
        }
    }

    @Test
    fun `verifyJoin throws NotFoundException when the join record is unknown`() = runBlocking<Unit> {
        service.requestJoin(code.toSessionCode(), Email(studentEmail))
        val token = capturedToken()
        fakeSessions.emailJoins.clear()

        assertThrows<NotFoundException> {
            service.verifyJoin(token)
        }
    }

    @Test
    fun `verifyJoin throws ConflictException when the session ended before verification`() = runBlocking<Unit> {
        service.requestJoin(code.toSessionCode(), Email(studentEmail))
        val token = capturedToken()
        putSession(SessionStatus.ENDED)

        assertThrows<ConflictException> {
            service.verifyJoin(token)
        }
    }

    @Test
    fun `verifyJoin throws UnauthorizedException when the token signature is invalid`() {
        val forged = JWT.create()
            .withIssuer("oeims-test")
            .withAudience("email-join")
            .withJWTId(UUID.randomUUID().toString())
            .withSubject(studentEmail)
            .withClaim("purpose", "email_join")
            .withClaim("sessionCode", code)
            .sign(Algorithm.HMAC256("a-different-secret"))

        assertThrows<UnauthorizedException> {
            runBlocking { service.verifyJoin(forged.toEmailJoinToken()) }
        }
    }

    // ── getParticipants ─────────────────────────────────────────────────────

    @Test
    fun `getParticipants returns all participants in the session`() = runBlocking {
        fakeParticipants.create(sessionId, studentEmail)
        fakeParticipants.create(sessionId, "student2@alunos.isel.pt")

        val results = service.getParticipants(sessionId.toSessionId())

        assertEquals(2, results.size)
        assertTrue(results.all { it.sessionId == sessionId.toString() })
    }

    @Test
    fun `getParticipants throws NotFoundException when the session does not exist`() {
        assertThrows<NotFoundException> {
            runBlocking { service.getParticipants(UUID.randomUUID().toSessionId()) }
        }
    }

    // ── getParticipantById ───────────────────────────────────────────────────

    @Test
    fun `getParticipantById returns the participant when it exists`() = runBlocking {
        val created = fakeParticipants.create(sessionId, studentEmail)

        val result = service.getParticipantById(created.id.toParticipantId())

        assertNotNull(result)
        assertEquals(created.id.toString(), result.id)
        assertEquals(studentEmail, result.email)
    }

    @Test
    fun `getParticipantById returns null when the participant does not exist`() = runBlocking {
        val result = service.getParticipantById(UUID.randomUUID().toParticipantId())

        assertNull(result)
    }
}
