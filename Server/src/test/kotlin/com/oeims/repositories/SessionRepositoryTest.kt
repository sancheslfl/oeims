package com.oeims.repositories

import com.oeims.models.Exams
import com.oeims.models.SessionStatus
import com.oeims.models.Sessions
import com.oeims.models.SessionSupervisors
import com.oeims.models.UserRole
import com.oeims.models.Users
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionRepositoryTest {

    private lateinit var sessionRepository: SessionRepository
    private lateinit var professorId: UUID
    private lateinit var otherProfessorId: UUID
    private lateinit var examId: UUID
    private var keepAlive: java.sql.Connection? = null

    @BeforeEach
    fun setup() = runBlocking {
        keepAlive = DriverManager.getConnection("jdbc:sqlite:file:testdb?mode=memory&cache=shared")
        Database.connect(
            url = "jdbc:sqlite:file:testdb?mode=memory&cache=shared",
            driver = "org.sqlite.JDBC"
        )
        transaction { SchemaUtils.create(Users, Exams, Sessions, SessionSupervisors) }

        val userRepo = UserRepository()
        val examRepo = ExamRepository()

        professorId      = userRepo.create("prof1@isel.pt", UserRole.PROFESSOR, "hash").id
        otherProfessorId = userRepo.create("prof2@isel.pt", UserRole.PROFESSOR, "hash").id
        examId           = examRepo.create(professorId, "Networks", null, 90).id

        sessionRepository = SessionRepository()
    }

    @AfterEach
    fun teardown() {
        transaction { SchemaUtils.drop(SessionSupervisors, Sessions, Exams, Users) }
        keepAlive?.close()
        keepAlive = null
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    fun `create returns session with PENDING status`() = runBlocking {
        val session = sessionRepository.create(examId, professorId, "ABC123")

        assertEquals(SessionStatus.PENDING, session.status)
        assertEquals(examId, session.examId)
        assertEquals(professorId, session.supervisorId)
        assertEquals("ABC123", session.code)
        assertNull(session.startedAt)
        assertNull(session.endedAt)
        assertNotNull(session.id)
    }

    @Test
    fun `create assigns unique ids`() = runBlocking {
        val s1 = sessionRepository.create(examId, professorId, "AAA111")
        val s2 = sessionRepository.create(examId, professorId, "BBB222")

        assertTrue(s1.id != s2.id)
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    fun `findById returns session when id exists`() = runBlocking {
        val created = sessionRepository.create(examId, professorId, "XYZ789")

        val result = sessionRepository.findById(created.id)

        assertNotNull(result)
        assertEquals(created.id, result.id)
        assertEquals("XYZ789", result.code)
    }

    @Test
    fun `findById returns null when id does not exist`() = runBlocking {
        val result = sessionRepository.findById(UUID.randomUUID())

        assertNull(result)
    }

    // ── findByCode ────────────────────────────────────────────────────────────

    @Test
    fun `findByCode returns session when code exists`() = runBlocking {
        sessionRepository.create(examId, professorId, "CODE01")

        val result = sessionRepository.findByCode("CODE01")

        assertNotNull(result)
        assertEquals("CODE01", result.code)
    }

    @Test
    fun `findByCode returns null when code does not exist`() = runBlocking {
        val result = sessionRepository.findByCode("ZZZZZZ")

        assertNull(result)
    }

    // ── findBySupervisor ──────────────────────────────────────────────────────

    @Test
    fun `findBySupervisor returns only sessions belonging to that professor`() = runBlocking {
        sessionRepository.create(examId, professorId,      "AAA001")
        sessionRepository.create(examId, professorId,      "BBB002")
        sessionRepository.create(examId, otherProfessorId, "CCC003")

        val results = sessionRepository.findBySupervisor(professorId)

        assertEquals(2, results.size)
        assertTrue(results.all { it.supervisorId == professorId })
    }

    @Test
    fun `findBySupervisor returns empty list when professor has no sessions`() = runBlocking {
        val results = sessionRepository.findBySupervisor(professorId)

        assertTrue(results.isEmpty())
    }

    // ── updateStatus ──────────────────────────────────────────────────────────

    @Test
    fun `updateStatus PENDING to ACTIVE sets startedAt and returns true`() = runBlocking {
        val session = sessionRepository.create(examId, professorId, "ACT001")

        val updated = sessionRepository.updateStatus(session.id, SessionStatus.ACTIVE)
        val result  = sessionRepository.findById(session.id)!!

        assertTrue(updated)
        assertEquals(SessionStatus.ACTIVE, result.status)
        assertNotNull(result.startedAt)
        assertNull(result.endedAt)
    }

    @Test
    fun `updateStatus ACTIVE to ENDED sets endedAt and returns true`() = runBlocking {
        val session = sessionRepository.create(examId, professorId, "END001")
        sessionRepository.updateStatus(session.id, SessionStatus.ACTIVE)

        val updated = sessionRepository.updateStatus(session.id, SessionStatus.ENDED)
        val result  = sessionRepository.findById(session.id)!!

        assertTrue(updated)
        assertEquals(SessionStatus.ENDED, result.status)
        assertNotNull(result.endedAt)
    }

    @Test
    fun `updateStatus returns false when session does not exist`() = runBlocking {
        val updated = sessionRepository.updateStatus(UUID.randomUUID(), SessionStatus.ACTIVE)

        assertFalse(updated)
    }

    // ── findAllActive ─────────────────────────────────────────────────────────

    @Test
    fun `findAllActive returns PENDING and ACTIVE sessions`() = runBlocking {
        val s1 = sessionRepository.create(examId, professorId, "ACT001")
        val s2 = sessionRepository.create(examId, professorId, "ACT002")
        sessionRepository.updateStatus(s2.id, SessionStatus.ACTIVE)

        val result = sessionRepository.findAllActive()

        assertEquals(2, result.size)
        assertTrue(result.any { it.id == s1.id })
        assertTrue(result.any { it.id == s2.id })
    }

    @Test
    fun `findAllActive excludes ENDED sessions`() = runBlocking {
        val session = sessionRepository.create(examId, professorId, "END001")
        sessionRepository.updateStatus(session.id, SessionStatus.ACTIVE)
        sessionRepository.updateStatus(session.id, SessionStatus.ENDED)

        val result = sessionRepository.findAllActive()

        assertTrue(result.isEmpty())
    }

    // ── isSupervisor ──────────────────────────────────────────────────────────

    @Test
    fun `isSupervisor returns true for the session creator`() = runBlocking {
        val session = sessionRepository.create(examId, professorId, "SUP001")

        assertTrue(sessionRepository.isSupervisor(session.id, professorId))
    }

    @Test
    fun `isSupervisor returns false for a professor with no relation to the session`() = runBlocking {
        val session = sessionRepository.create(examId, professorId, "SUP002")

        assertFalse(sessionRepository.isSupervisor(session.id, otherProfessorId))
    }

    @Test
    fun `isSupervisor returns true after addSupervisor is called`() = runBlocking {
        val session = sessionRepository.create(examId, professorId, "SUP003")

        sessionRepository.addSupervisor(session.id, otherProfessorId)

        assertTrue(sessionRepository.isSupervisor(session.id, otherProfessorId))
    }

    @Test
    fun `isSupervisor returns false for session that does not exist`() = runBlocking {
        assertFalse(sessionRepository.isSupervisor(UUID.randomUUID(), professorId))
    }

    // ── addSupervisor ─────────────────────────────────────────────────────────

    @Test
    fun `addSupervisor is idempotent when called twice for the same pair`() = runBlocking {
        val session = sessionRepository.create(examId, professorId, "ADD001")
        sessionRepository.addSupervisor(session.id, otherProfessorId)

        // second call must not throw (composite PK would normally reject a duplicate)
        sessionRepository.addSupervisor(session.id, otherProfessorId)

        assertTrue(sessionRepository.isSupervisor(session.id, otherProfessorId))
    }

    // ── findLatestOpenBySupervisor (with access table) ────────────────────────

    @Test
    fun `findLatestOpenBySupervisor returns session when professor is the creator`() = runBlocking {
        val session = sessionRepository.create(examId, professorId, "LAT001")

        val result = sessionRepository.findLatestOpenBySupervisor(professorId)

        assertNotNull(result)
        assertEquals(session.id, result.id)
    }

    @Test
    fun `findLatestOpenBySupervisor returns session when professor has access via session_supervisors`() = runBlocking {
        val session = sessionRepository.create(examId, professorId, "LAT002")
        sessionRepository.addSupervisor(session.id, otherProfessorId)

        val result = sessionRepository.findLatestOpenBySupervisor(otherProfessorId)

        assertNotNull(result)
        assertEquals(session.id, result.id)
    }

    @Test
    fun `findLatestOpenBySupervisor returns null when professor has no open sessions`() = runBlocking {
        val result = sessionRepository.findLatestOpenBySupervisor(otherProfessorId)

        assertNull(result)
    }

    @Test
    fun `findLatestOpenBySupervisor does not return ENDED sessions even with access`() = runBlocking {
        val session = sessionRepository.create(examId, professorId, "LAT003")
        sessionRepository.updateStatus(session.id, SessionStatus.ACTIVE)
        sessionRepository.updateStatus(session.id, SessionStatus.ENDED)
        sessionRepository.addSupervisor(session.id, otherProfessorId)

        val result = sessionRepository.findLatestOpenBySupervisor(otherProfessorId)

        assertNull(result)
    }
}
