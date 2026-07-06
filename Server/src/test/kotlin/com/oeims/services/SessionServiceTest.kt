package com.oeims.services

import com.oeims.models.AllowedEmailDomain
import com.oeims.models.ConflictException
import com.oeims.models.ForbiddenException
import com.oeims.models.NotFoundException
import com.oeims.models.SessionStatus
import com.oeims.models.ids.toExamId
import com.oeims.models.ids.toProfessorId
import com.oeims.models.ids.toSessionId
import com.oeims.models.toSessionCode
import com.oeims.repositories.EmailJoinRecord
import com.oeims.repositories.ExamRecord
import com.oeims.repositories.SessionRecord
import com.oeims.repositories.interfaces.IExamRepository
import com.oeims.repositories.interfaces.ISessionRepository
import com.oeims.connections.SseBroadcaster
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SessionServiceTest {

    // ── Fakes ─────────────────────────────────────────────────────────────────

    private class FakeExamRepository : IExamRepository {
        val exams = mutableListOf<ExamRecord>()

        override suspend fun findById(id: UUID): ExamRecord? = exams.find { it.id == id }
        override suspend fun findAll(): List<ExamRecord> = exams.toList()
        override suspend fun findByTitle(title: String): List<ExamRecord> = exams.filter { it.title == title }
        override suspend fun findByProfessor(professorId: UUID): List<ExamRecord> =
            exams.filter { it.createdBy == professorId }

        override suspend fun create(
            createdBy: UUID,
            title: String,
            description: String?,
            durationMins: Int
        ): ExamRecord {
            val record = ExamRecord(UUID.randomUUID(), createdBy, title, description, durationMins, Instant.now())
            exams.add(record)
            return record
        }
    }

    private class FakeSessionRepository : ISessionRepository {
        val sessions = mutableMapOf<UUID, SessionRecord>()
        val supervisors = mutableMapOf<UUID, MutableSet<UUID>>()

        override suspend fun findById(id: UUID): SessionRecord? = sessions[id]
        override suspend fun findByCode(code: String): SessionRecord? = sessions.values.find { it.code == code }
        override suspend fun findBySupervisor(supervisorId: UUID): List<SessionRecord> =
            sessions.values.filter { it.supervisorId == supervisorId }

        override suspend fun findLatestOpenBySupervisor(supervisorId: UUID): SessionRecord? =
            sessions.values.filter {
                it.supervisorId == supervisorId && it.status in listOf(SessionStatus.PENDING, SessionStatus.ACTIVE)
            }.maxByOrNull { it.createdAt }

        override suspend fun create(
            examId: UUID,
            supervisorId: UUID,
            code: String,
            allowedEmailDomain: String,
        ): SessionRecord? {
            if (sessions.values.any { it.code == code }) return null
            val record = SessionRecord(
                id = UUID.randomUUID(),
                examId = examId,
                supervisorId = supervisorId,
                code = code,
                allowedEmailDomain = allowedEmailDomain,
                status = SessionStatus.PENDING,
                createdAt = Instant.now(),
                startedAt = null,
                endedAt = null,
            )
            sessions[record.id] = record
            return record
        }

        override suspend fun updateStatus(id: UUID, status: SessionStatus): Boolean {
            val session = sessions[id] ?: return false
            val now = Instant.now()
            sessions[id] = session.copy(
                status = status,
                startedAt = if (status == SessionStatus.ACTIVE) now else session.startedAt,
                endedAt = if (status == SessionStatus.ENDED) now else session.endedAt
            )
            return true
        }

        override suspend fun addSupervisor(sessionId: UUID, userId: UUID) {
            supervisors.getOrPut(sessionId) { mutableSetOf() }.add(userId)
        }

        override suspend fun isSupervisor(sessionId: UUID, userId: UUID): Boolean {
            val session = sessions[sessionId] ?: return false
            return session.supervisorId == userId || supervisors[sessionId]?.contains(userId) == true
        }

        override suspend fun findAllActive(): List<SessionRecord> {
            val open = listOf(SessionStatus.PENDING, SessionStatus.ACTIVE)
            return sessions.values.filter { it.status in open }
        }

        override suspend fun createEmailJoin(
            sessionId: UUID,
            email: String,
            jwtId: String,
            expiresAt: Instant,
        ): EmailJoinRecord = throw UnsupportedOperationException()

        override suspend fun findEmailJoinByJwtId(jwtId: String): EmailJoinRecord? = null
        override suspend fun updateEmailJoinVerification(id: UUID, verifiedAt: Instant): Boolean = false
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private lateinit var fakeExams: FakeExamRepository
    private lateinit var fakeSessions: FakeSessionRepository
    private lateinit var service: SessionService

    private lateinit var professorId: UUID
    private lateinit var otherProfessorId: UUID
    private lateinit var examId: UUID

    private val allowedDomain = AllowedEmailDomain("alunos.isel.pt")

    @BeforeEach
    fun setup() = runBlocking {
        fakeExams = FakeExamRepository()
        fakeSessions = FakeSessionRepository()
        service = SessionService(fakeSessions, fakeExams, SseBroadcaster())

        professorId = UUID.randomUUID()
        otherProfessorId = UUID.randomUUID()
        examId = fakeExams.create(professorId, "Networks", null, 90).id
    }

    private suspend fun createSession() =
        service.createSession(professorId.toProfessorId(), examId.toExamId(), allowedDomain)

    // ── createSession ─────────────────────────────────────────────────────────

    @Test
    fun `createSession returns PENDING session with a 6-char code`() = runBlocking<Unit> {
        val response = createSession()

        assertEquals("PENDING", response.status)
        assertEquals(examId.toString(), response.examId)
        assertEquals(professorId.toString(), response.supervisorId)
        assertEquals(6, response.code.length)
        assertNotNull(response.id)
    }

    @Test
    fun `createSession throws NotFoundException when exam does not exist`() {
        assertThrows<NotFoundException> {
            runBlocking {
                service.createSession(professorId.toProfessorId(), UUID.randomUUID().toExamId(), allowedDomain)
            }
        }
    }

    // ── startSession ──────────────────────────────────────────────────────────

    @Test
    fun `startSession transitions session to ACTIVE`() = runBlocking<Unit> {
        val session = createSession()

        val result = service.startSession(UUID.fromString(session.id).toSessionId(), professorId.toProfessorId())

        assertEquals("ACTIVE", result.status)
        assertNotNull(result.startedAt)
    }

    @Test
    fun `startSession throws NotFoundException when session does not exist`() {
        assertThrows<NotFoundException> {
            runBlocking { service.startSession(UUID.randomUUID().toSessionId(), professorId.toProfessorId()) }
        }
    }

    @Test
    fun `startSession throws ForbiddenException when called by a different professor`() = runBlocking<Unit> {
        val session = createSession()

        assertThrows<ForbiddenException> {
            service.startSession(UUID.fromString(session.id).toSessionId(), otherProfessorId.toProfessorId())
        }
    }

    @Test
    fun `startSession throws ConflictException when session is already ACTIVE`() = runBlocking<Unit> {
        val session = createSession()
        service.startSession(UUID.fromString(session.id).toSessionId(), professorId.toProfessorId())

        assertThrows<ConflictException> {
            service.startSession(UUID.fromString(session.id).toSessionId(), professorId.toProfessorId())
        }
    }

    @Test
    fun `startSession throws ConflictException when session is ENDED`() = runBlocking<Unit> {
        val session = createSession()
        val sessionId = UUID.fromString(session.id)
        service.startSession(sessionId.toSessionId(), professorId.toProfessorId())
        service.endSession(sessionId.toSessionId(), professorId.toProfessorId())

        assertThrows<ConflictException> {
            service.startSession(sessionId.toSessionId(), professorId.toProfessorId())
        }
    }

    // ── endSession ────────────────────────────────────────────────────────────

    @Test
    fun `endSession transitions session to ENDED`() = runBlocking<Unit> {
        val session = createSession()
        val sessionId = UUID.fromString(session.id)
        service.startSession(sessionId.toSessionId(), professorId.toProfessorId())

        val result = service.endSession(sessionId.toSessionId(), professorId.toProfessorId())

        assertEquals("ENDED", result.status)
        assertNotNull(result.endedAt)
    }

    @Test
    fun `endSession throws NotFoundException when session does not exist`() {
        assertThrows<NotFoundException> {
            runBlocking { service.endSession(UUID.randomUUID().toSessionId(), professorId.toProfessorId()) }
        }
    }

    @Test
    fun `endSession throws ForbiddenException when called by a different professor`() = runBlocking<Unit> {
        val session = createSession()
        val sessionId = UUID.fromString(session.id)
        service.startSession(sessionId.toSessionId(), professorId.toProfessorId())

        assertThrows<ForbiddenException> {
            service.endSession(sessionId.toSessionId(), otherProfessorId.toProfessorId())
        }
    }

    @Test
    fun `endSession throws ConflictException when session is still PENDING`() = runBlocking<Unit> {
        val session = createSession()

        assertThrows<ConflictException> {
            service.endSession(UUID.fromString(session.id).toSessionId(), professorId.toProfessorId())
        }
    }

    // ── getSession ────────────────────────────────────────────────────────────

    @Test
    fun `getSession returns session when it exists`() = runBlocking {
        val created = createSession()

        val result = service.getSession(UUID.fromString(created.id).toSessionId())

        assertEquals(created.id, result.id)
        assertEquals("PENDING", result.status)
    }

    @Test
    fun `getSession throws NotFoundException when session does not exist`() {
        assertThrows<NotFoundException> {
            runBlocking { service.getSession(UUID.randomUUID().toSessionId()) }
        }
    }

    // ── joinAsAdditionalSupervisor ────────────────────────────────────────────

    @Test
    fun `joinAsAdditionalSupervisor grants access to an active session`() = runBlocking<Unit> {
        val session = createSession()
        val sessionId = UUID.fromString(session.id)
        service.startSession(sessionId.toSessionId(), professorId.toProfessorId())

        service.joinAsAdditionalSupervisor(session.code.toSessionCode(), otherProfessorId.toProfessorId())

        assertTrue(fakeSessions.isSupervisor(sessionId, otherProfessorId))
    }

    @Test
    fun `joinAsAdditionalSupervisor returns the session response`() = runBlocking {
        val session = createSession()
        val sessionId = UUID.fromString(session.id)
        service.startSession(sessionId.toSessionId(), professorId.toProfessorId())

        val result = service.joinAsAdditionalSupervisor(session.code.toSessionCode(), otherProfessorId.toProfessorId())

        assertEquals(session.id, result.id)
    }

    @Test
    fun `joinAsAdditionalSupervisor throws NotFoundException when code does not exist`() {
        assertThrows<NotFoundException> {
            runBlocking {
                service.joinAsAdditionalSupervisor("XXXXXX".toSessionCode(), otherProfessorId.toProfessorId())
            }
        }
    }

    @Test
    fun `joinAsAdditionalSupervisor grants access to a pending session`() = runBlocking<Unit> {
        val session = createSession()
        val sessionId = UUID.fromString(session.id)

        service.joinAsAdditionalSupervisor(session.code.toSessionCode(), otherProfessorId.toProfessorId())

        assertTrue(fakeSessions.isSupervisor(sessionId, otherProfessorId))
    }

    @Test
    fun `joinAsAdditionalSupervisor throws ConflictException when session is ENDED`() = runBlocking<Unit> {
        val session = createSession()
        val sessionId = UUID.fromString(session.id)
        service.startSession(sessionId.toSessionId(), professorId.toProfessorId())
        service.endSession(sessionId.toSessionId(), professorId.toProfessorId())

        assertThrows<ConflictException> {
            service.joinAsAdditionalSupervisor(session.code.toSessionCode(), otherProfessorId.toProfessorId())
        }
    }

    // ── canSupervise ──────────────────────────────────────────────────────────

    @Test
    fun `canSupervise returns true for the session creator`() = runBlocking {
        val session = createSession()

        assertTrue(service.canSupervise(UUID.fromString(session.id).toSessionId(), professorId.toProfessorId()))
    }

    @Test
    fun `canSupervise returns false for an unrelated professor`() = runBlocking {
        val session = createSession()

        assertFalse(service.canSupervise(UUID.fromString(session.id).toSessionId(), otherProfessorId.toProfessorId()))
    }

    @Test
    fun `canSupervise returns true after joinAsAdditionalSupervisor`() = runBlocking<Unit> {
        val session = createSession()
        val sessionId = UUID.fromString(session.id)
        service.startSession(sessionId.toSessionId(), professorId.toProfessorId())
        service.joinAsAdditionalSupervisor(session.code.toSessionCode(), otherProfessorId.toProfessorId())

        assertTrue(service.canSupervise(sessionId.toSessionId(), otherProfessorId.toProfessorId()))
    }

    // ── getActiveSessions ─────────────────────────────────────────────────────

    @Test
    fun `getActiveSessions returns PENDING and ACTIVE sessions`() = runBlocking {
        val s1 = createSession()
        val s2 = createSession()
        service.startSession(UUID.fromString(s2.id).toSessionId(), professorId.toProfessorId())

        val result = service.getActiveSessions()

        assertEquals(2, result.size)
        assertTrue(result.any { it.id == s1.id })
        assertTrue(result.any { it.id == s2.id })
    }

    @Test
    fun `getActiveSessions excludes ENDED sessions`() = runBlocking {
        val session = createSession()
        val sessionId = UUID.fromString(session.id).toSessionId()
        service.startSession(sessionId, professorId.toProfessorId())
        service.endSession(sessionId, professorId.toProfessorId())

        val result = service.getActiveSessions()

        assertTrue(result.isEmpty())
    }
}
