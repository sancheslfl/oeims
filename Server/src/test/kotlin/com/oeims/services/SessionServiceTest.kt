package com.oeims.services

import com.oeims.models.ConnectionStatus
import com.oeims.models.SessionStatus
import com.oeims.models.UserRole
import com.oeims.repositories.ExamRecord
import com.oeims.repositories.ParticipantRecord
import com.oeims.repositories.SessionRecord
import com.oeims.repositories.UserRecord
import com.oeims.repositories.interfaces.IExamRepository
import com.oeims.repositories.interfaces.IParticipantRepository
import com.oeims.repositories.interfaces.ISessionRepository
import com.oeims.repositories.interfaces.IUserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SessionServiceTest {

    // ── Fakes ─────────────────────────────────────────────────────────────────

    private inner class FakeUserRepository : IUserRepository {
        val users = mutableListOf<UserRecord>()

        override fun findById(id: UUID): UserRecord? = users.find { it.id == id }
        override fun findByEmail(email: String): UserRecord? = users.find { it.email == email }
        override fun existsByEmail(email: String): Boolean = users.any { it.email == email }
        override fun create(email: String, role: UserRole, passwordHash: String): UserRecord {
            val record = UserRecord(UUID.randomUUID(), email, role, passwordHash, Instant.now())
            users.add(record)
            return record
        }
    }

    private inner class FakeExamRepository : IExamRepository {
        val exams = mutableListOf<ExamRecord>()

        override fun findById(id: UUID): ExamRecord? = exams.find { it.id == id }
        override fun findAll(): List<ExamRecord> = exams.toList()
        override fun findByTitle(title: String): List<ExamRecord> = exams.filter { it.title == title }
        override fun findByProfessor(professorId: UUID): List<ExamRecord> = exams.filter { it.createdBy == professorId }
        override fun create(createdBy: UUID, title: String, description: String?, durationMins: Int): ExamRecord {
            val record = ExamRecord(UUID.randomUUID(), createdBy, title, description, durationMins, Instant.now())
            exams.add(record)
            return record
        }
    }

    private inner class FakeSessionRepository : ISessionRepository {
        val sessions = mutableMapOf<UUID, SessionRecord>()

        override fun findById(id: UUID): SessionRecord? = sessions[id]
        override fun findByCode(code: String): SessionRecord? = sessions.values.find { it.code == code }
        override fun findBySupervisor(supervisorId: UUID): List<SessionRecord> =
            sessions.values.filter { it.supervisorId == supervisorId }

        override fun create(examId: UUID, supervisorId: UUID, code: String): SessionRecord {
            val record = SessionRecord(UUID.randomUUID(), examId, supervisorId, code, SessionStatus.PENDING, null, null)
            sessions[record.id] = record
            return record
        }

        override fun updateStatus(id: UUID, status: SessionStatus): Boolean {
            val session = sessions[id] ?: return false
            val now = Instant.now()
            sessions[id] = session.copy(
                status    = status,
                startedAt = if (status == SessionStatus.ACTIVE) now else session.startedAt,
                endedAt   = if (status == SessionStatus.ENDED)  now else session.endedAt
            )
            return true
        }
    }

    private inner class FakeParticipantRepository : IParticipantRepository {
        val participants = mutableListOf<ParticipantRecord>()

        override fun findById(id: UUID): ParticipantRecord? = participants.find { it.id == id }
        override fun findBySession(sessionId: UUID): List<ParticipantRecord> =
            participants.filter { it.sessionId == sessionId }
        override fun findByUserAndSession(userId: UUID, sessionId: UUID): ParticipantRecord? =
            participants.find { it.userId == userId && it.sessionId == sessionId }
        override fun create(sessionId: UUID, userId: UUID): ParticipantRecord {
            val record = ParticipantRecord(
                id               = UUID.randomUUID(),
                sessionId        = sessionId,
                userId           = userId,
                email            = "student@test.pt",
                connectionStatus = ConnectionStatus.CONNECTED,
                lastHeartbeat    = null,
                joinedAt         = Instant.now()
            )
            participants.add(record)
            return record
        }
        override fun updateHeartbeat(id: UUID): Boolean = participants.any { it.id == id }
        override fun updateConnectionStatus(id: UUID, status: ConnectionStatus): Boolean = participants.any { it.id == id }
        override fun markTimedOut(threshold: Instant): List<ParticipantRecord> = emptyList()
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private lateinit var fakeUsers: FakeUserRepository
    private lateinit var fakeExams: FakeExamRepository
    private lateinit var fakeSessions: FakeSessionRepository
    private lateinit var fakeParticipants: FakeParticipantRepository
    private lateinit var service: SessionService

    private lateinit var professorId: UUID
    private lateinit var studentId: UUID
    private lateinit var examId: UUID

    @BeforeEach
    fun setup() {
        fakeUsers        = FakeUserRepository()
        fakeExams        = FakeExamRepository()
        fakeSessions     = FakeSessionRepository()
        fakeParticipants = FakeParticipantRepository()
        service          = SessionService(fakeSessions, fakeExams, fakeParticipants, fakeUsers)

        professorId = fakeUsers.create("prof@isel.pt", UserRole.PROFESSOR, "hash").id
        studentId   = fakeUsers.create("student@alunos.isel.pt", UserRole.STUDENT, "hash").id
        examId      = fakeExams.create(professorId, "Networks", null, 90).id
    }

    // ── createSession ─────────────────────────────────────────────────────────

    @Test
    fun `createSession returns PENDING session with a 6-char code`() {
        val response = service.createSession(professorId, examId)

        assertEquals("PENDING", response.status)
        assertEquals(examId.toString(), response.examId)
        assertEquals(professorId.toString(), response.supervisorId)
        assertEquals(6, response.code.length)
        assertNotNull(response.id)
    }

    @Test
    fun `createSession throws NoSuchElementException when exam does not exist`() {
        assertThrows<NoSuchElementException> {
            service.createSession(professorId, UUID.randomUUID())
        }
    }

    // ── startSession ──────────────────────────────────────────────────────────

    @Test
    fun `startSession transitions session to ACTIVE`() {
        val session = service.createSession(professorId, examId)

        val result = service.startSession(UUID.fromString(session.id), professorId)

        assertEquals("ACTIVE", result.status)
        assertNotNull(result.startedAt)
    }

    @Test
    fun `startSession throws NoSuchElementException when session does not exist`() {
        assertThrows<NoSuchElementException> {
            service.startSession(UUID.randomUUID(), professorId)
        }
    }

    @Test
    fun `startSession throws IllegalStateException when called by a different professor`() {
        val session = service.createSession(professorId, examId)
        val otherId = UUID.randomUUID()

        assertThrows<IllegalStateException> {
            service.startSession(UUID.fromString(session.id), otherId)
        }
    }

    @Test
    fun `startSession throws IllegalStateException when session is already ACTIVE`() {
        val session = service.createSession(professorId, examId)
        service.startSession(UUID.fromString(session.id), professorId)

        assertThrows<IllegalStateException> {
            service.startSession(UUID.fromString(session.id), professorId)
        }
    }

    @Test
    fun `startSession throws IllegalStateException when session is ENDED`() {
        val session = service.createSession(professorId, examId)
        val sessionId = UUID.fromString(session.id)
        service.startSession(sessionId, professorId)
        service.endSession(sessionId, professorId)

        assertThrows<IllegalStateException> {
            service.startSession(sessionId, professorId)
        }
    }

    // ── endSession ────────────────────────────────────────────────────────────

    @Test
    fun `endSession transitions session to ENDED`() {
        val session   = service.createSession(professorId, examId)
        val sessionId = UUID.fromString(session.id)
        service.startSession(sessionId, professorId)

        val result = service.endSession(sessionId, professorId)

        assertEquals("ENDED", result.status)
        assertNotNull(result.endedAt)
    }

    @Test
    fun `endSession throws NoSuchElementException when session does not exist`() {
        assertThrows<NoSuchElementException> {
            service.endSession(UUID.randomUUID(), professorId)
        }
    }

    @Test
    fun `endSession throws IllegalStateException when called by a different professor`() {
        val session   = service.createSession(professorId, examId)
        val sessionId = UUID.fromString(session.id)
        service.startSession(sessionId, professorId)

        assertThrows<IllegalStateException> {
            service.endSession(sessionId, UUID.randomUUID())
        }
    }

    @Test
    fun `endSession throws IllegalStateException when session is still PENDING`() {
        val session = service.createSession(professorId, examId)

        assertThrows<IllegalStateException> {
            service.endSession(UUID.fromString(session.id), professorId)
        }
    }

    // ── joinSession ───────────────────────────────────────────────────────────

    @Test
    fun `joinSession returns participantId and exam details for a new join`() {
        val session  = service.createSession(professorId, examId)
        val response = service.joinSession(session.code, studentId)

        assertNotNull(response.participantId)
        assertEquals(session.id, response.sessionId)
        assertEquals("Networks", response.examTitle)
        assertEquals(90, response.durationMins)
    }

    @Test
    fun `joinSession is idempotent and returns the same participantId on repeated calls`() {
        val session = service.createSession(professorId, examId)

        val first  = service.joinSession(session.code, studentId)
        val second = service.joinSession(session.code, studentId)

        assertEquals(first.participantId, second.participantId)
    }

    @Test
    fun `joinSession allows joining a PENDING session`() {
        val session  = service.createSession(professorId, examId)
        assertEquals("PENDING", session.status)

        val response = service.joinSession(session.code, studentId)

        assertNotNull(response.participantId)
    }

    @Test
    fun `joinSession allows joining an ACTIVE session`() {
        val session   = service.createSession(professorId, examId)
        val sessionId = UUID.fromString(session.id)
        service.startSession(sessionId, professorId)

        val response = service.joinSession(session.code, studentId)

        assertNotNull(response.participantId)
    }

    @Test
    fun `joinSession throws IllegalStateException when session has ENDED`() {
        val session   = service.createSession(professorId, examId)
        val sessionId = UUID.fromString(session.id)
        service.startSession(sessionId, professorId)
        service.endSession(sessionId, professorId)

        assertThrows<IllegalStateException> {
            service.joinSession(session.code, studentId)
        }
    }

    @Test
    fun `joinSession throws NoSuchElementException when code does not exist`() {
        assertThrows<NoSuchElementException> {
            service.joinSession("XXXXXX", studentId)
        }
    }

    // ── getSession ────────────────────────────────────────────────────────────

    @Test
    fun `getSession returns session when it exists`() {
        val created = service.createSession(professorId, examId)

        val result = service.getSession(UUID.fromString(created.id))

        assertEquals(created.id, result.id)
        assertEquals("PENDING", result.status)
    }

    @Test
    fun `getSession throws NoSuchElementException when session does not exist`() {
        assertThrows<NoSuchElementException> {
            service.getSession(UUID.randomUUID())
        }
    }

    // ── getParticipants ───────────────────────────────────────────────────────

    @Test
    fun `getParticipants returns all participants in the session`() {
        val session   = service.createSession(professorId, examId)
        val sessionId = UUID.fromString(session.id)
        val student2Id = fakeUsers.create("student2@alunos.isel.pt", UserRole.STUDENT, "hash").id

        service.joinSession(session.code, studentId)
        service.joinSession(session.code, student2Id)

        val results = service.getParticipants(sessionId)

        assertEquals(2, results.size)
        assertTrue(results.all { it.sessionId == session.id })
    }

    @Test
    fun `getParticipants throws NoSuchElementException when session does not exist`() {
        assertThrows<NoSuchElementException> {
            service.getParticipants(UUID.randomUUID())
        }
    }
}
