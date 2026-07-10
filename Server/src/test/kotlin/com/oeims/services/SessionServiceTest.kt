package com.oeims.services

import com.oeims.connections.SseBroadcaster
import com.oeims.models.*
import com.oeims.repositories.interfaces.IEventRepository
import com.oeims.repositories.interfaces.IExamRepository
import com.oeims.repositories.interfaces.IParticipantRepository
import com.oeims.repositories.interfaces.ISessionRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class SessionServiceTest {
    private class FakeExamRepository : IExamRepository {
        val exams = mutableListOf<ExamRecord>()

        override suspend fun findById(id: UUID): ExamRecord? = exams.find { it.id == id }
        override suspend fun findAll(): List<ExamRecord> = exams.toList()
        override suspend fun findByTitle(title: String): List<ExamRecord> = exams.filter { it.title == title }
        override suspend fun findByProfessor(professorId: UUID): List<ExamRecord> = exams.filter { it.createdBy == professorId }

        override suspend fun create(createdBy: UUID, title: String, description: String?, durationMins: Int): ExamRecord {
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
        override suspend fun findBySupervisor(supervisorId: UUID): List<SessionRecord> = sessions.values.filter { it.supervisorId == supervisorId }

        override suspend fun findLatestOpenBySupervisor(supervisorId: UUID): SessionRecord? =
            sessions.values
                .filter {
                    it.status != SessionStatus.ENDED &&
                        (it.supervisorId == supervisorId || supervisors[it.id]?.contains(supervisorId) == true)
                }
                .maxByOrNull { it.createdAt }

        override suspend fun create(examId: UUID, supervisorId: UUID, code: String, allowedEmailDomain: String): SessionRecord? {
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
                endedAt = if (status == SessionStatus.ENDED) now else session.endedAt,
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

        override suspend fun findAllActive(): List<SessionRecord> = sessions.values.filter { it.status != SessionStatus.ENDED }
        override suspend fun updateJoinVerification(id: UUID, verifiedAt: Instant): Boolean = false
        override suspend fun findJoinRequestByJwtId(jwtId: String): SessionJoinRecord? = null
        override suspend fun createJoinRequest(sessionId: UUID, email: String, jwtId: String, expiresAt: Instant): SessionJoinRecord =
            throw UnsupportedOperationException()
    }

    private class FakeParticipantRepository : IParticipantRepository {
        override suspend fun findById(id: UUID): ParticipantRecord? = null
        override suspend fun findBySession(sessionId: UUID): List<ParticipantRecord> = emptyList()
        override suspend fun findByExamIdentityCode(examIdentityCode: String): ParticipantRecord? = null
        override suspend fun findByEmailAndSession(email: String, sessionId: UUID): ParticipantRecord? = null
        override suspend fun findConnectedBySession(sessionId: UUID): List<ParticipantRecord> = emptyList()
        override suspend fun create(sessionId: UUID, email: String): ParticipantRecord = throw UnsupportedOperationException()
        override suspend fun updateHeartbeat(id: UUID): Boolean = false
        override suspend fun updateConnectionStatus(id: UUID, status: ConnectionStatus): Boolean = false
        override suspend fun updateTimedOut(threshold: Instant): List<ParticipantRecord> = emptyList()
        override suspend fun updateExamIdentityCode(participantId: UUID, examIdentityCode: String): Boolean = false
    }

    private class FakeEventRepository : IEventRepository {
        override suspend fun create(participantId: UUID, monitorName: String, message: String, severity: Severity, occurredAt: Instant?): EventRecord =
            EventRecord(UUID.randomUUID(), participantId, monitorName, message, severity, occurredAt ?: Instant.now())

        override suspend fun findByParticipant(participantId: UUID): List<EventRecord> = emptyList()
        override suspend fun findByParticipants(participantIds: List<UUID>): List<EventRecord> = emptyList()
        override suspend fun findBySession(sessionId: UUID): List<EventRecord> = emptyList()
    }

    private lateinit var exams: FakeExamRepository
    private lateinit var sessions: FakeSessionRepository
    private lateinit var service: SessionService
    private lateinit var professorId: UUID
    private lateinit var otherProfessorId: UUID
    private lateinit var examId: UUID

    private val allowedDomain = AllowedEmailDomain("alunos.isel.pt")

    @BeforeEach
    fun setup() = runBlocking {
        exams = FakeExamRepository()
        sessions = FakeSessionRepository()
        service = SessionService(sessions, exams, FakeParticipantRepository(), FakeEventRepository(), SseBroadcaster())
        professorId = UUID.randomUUID()
        otherProfessorId = UUID.randomUUID()
        examId = exams.create(professorId, "Networks", null, 90).id
    }

    private suspend fun createSession() = service.create(professorId.toProfessorId(), examId.toExamId(), allowedDomain)

    @Test
    fun `create returns a pending session`() = runBlocking {
        val session = createSession()

        assertEquals("PENDING", session.status)
        assertEquals(examId.toString(), session.examId)
        assertEquals(professorId.toString(), session.supervisorId)
        assertEquals(6, session.code.length)
    }

    @Test
    fun `create rejects an unknown exam`() {
        assertThrows<NotFoundException> {
            runBlocking { service.create(professorId.toProfessorId(), UUID.randomUUID().toExamId(), allowedDomain) }
        }
    }

    @Test
    fun `start activates a pending session`() = runBlocking {
        val session = createSession()

        val result = service.start(session.id.toSessionId(), professorId.toProfessorId())

        assertEquals("ACTIVE", result.status)
        assertNotNull(result.startedAt)
    }

    @Test
    fun `start validates session ownership and state`() = runBlocking {
        val session = createSession()
        val sessionId = session.id.toSessionId()

        assertThrows<ForbiddenException> { service.start(sessionId, otherProfessorId.toProfessorId()) }

        service.start(sessionId, professorId.toProfessorId())

        assertThrows<ConflictException> { service.start(sessionId, professorId.toProfessorId()) }
    }

    @Test
    fun `end closes an active session`() = runBlocking {
        val session = createSession()
        val sessionId = session.id.toSessionId()
        service.start(sessionId, professorId.toProfessorId())

        val result = service.end(sessionId, professorId.toProfessorId())

        assertEquals("ENDED", result.status)
        assertNotNull(result.endedAt)
    }

    @Test
    fun `end validates session ownership and state`() = runBlocking {
        val session = createSession()
        val sessionId = session.id.toSessionId()

        assertThrows<ConflictException> { service.end(sessionId, professorId.toProfessorId()) }

        service.start(sessionId, professorId.toProfessorId())

        assertThrows<ForbiddenException> { service.end(sessionId, otherProfessorId.toProfessorId()) }
    }

    @Test
    fun `getSession returns an existing session`() = runBlocking {
        val created = createSession()

        val result = service.getSession(created.id.toSessionId())

        assertEquals(created.id, result.id)
    }

    @Test
    fun `joinAsAdditionalSupervisor grants supervision on open sessions`() = runBlocking {
        val session = createSession()
        val sessionId = session.id.toSessionId()

        val result = service.joinAsAdditionalSupervisor(session.code.toSessionCode(), otherProfessorId.toProfessorId())

        assertEquals(session.id, result.id)
        assertTrue(service.canSupervise(sessionId, otherProfessorId.toProfessorId()))
    }

    @Test
    fun `joinAsAdditionalSupervisor rejects missing and ended sessions`() = runBlocking {
        assertThrows<NotFoundException> {
            service.joinAsAdditionalSupervisor("XXXXXX".toSessionCode(), otherProfessorId.toProfessorId())
        }

        val session = createSession()
        val sessionId = session.id.toSessionId()
        service.start(sessionId, professorId.toProfessorId())
        service.end(sessionId, professorId.toProfessorId())

        assertThrows<ConflictException> {
            service.joinAsAdditionalSupervisor(session.code.toSessionCode(), otherProfessorId.toProfessorId())
        }
    }

    @Test
    fun `getActiveSessions returns pending and active sessions only`() = runBlocking {
        val pending = createSession()
        val active = createSession()
        val ended = createSession()

        service.start(active.id.toSessionId(), professorId.toProfessorId())
        service.start(ended.id.toSessionId(), professorId.toProfessorId())
        service.end(ended.id.toSessionId(), professorId.toProfessorId())

        val result = service.getActiveSessions()

        assertTrue(result.any { it.id == pending.id })
        assertTrue(result.any { it.id == active.id })
        assertFalse(result.any { it.id == ended.id })
    }
}
