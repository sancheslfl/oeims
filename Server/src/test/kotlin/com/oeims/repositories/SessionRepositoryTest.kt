package com.oeims.repositories

import com.oeims.models.Exams
import com.oeims.models.SessionStatus
import com.oeims.models.Sessions
import com.oeims.models.UserRole
import com.oeims.models.Users
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
    fun setup() {
        keepAlive = DriverManager.getConnection("jdbc:sqlite:file:testdb?mode=memory&cache=shared")
        Database.connect(
            url = "jdbc:sqlite:file:testdb?mode=memory&cache=shared",
            driver = "org.sqlite.JDBC"
        )
        transaction { SchemaUtils.create(Users, Exams, Sessions) }

        val userRepo = UserRepository()
        val examRepo = ExamRepository()

        professorId      = userRepo.create("prof1@isel.pt", UserRole.PROFESSOR, "hash").id
        otherProfessorId = userRepo.create("prof2@isel.pt", UserRole.PROFESSOR, "hash").id
        examId           = examRepo.create(professorId, "Networks", null, 90).id

        sessionRepository = SessionRepository()
    }

    @AfterEach
    fun teardown() {
        transaction { SchemaUtils.drop(Sessions, Exams, Users) }
        keepAlive?.close()
        keepAlive = null
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    fun `create returns session with PENDING status`() {
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
    fun `create assigns unique ids`() {
        val s1 = sessionRepository.create(examId, professorId, "AAA111")
        val s2 = sessionRepository.create(examId, professorId, "BBB222")

        assertTrue(s1.id != s2.id)
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    fun `findById returns session when id exists`() {
        val created = sessionRepository.create(examId, professorId, "XYZ789")

        val result = sessionRepository.findById(created.id)

        assertNotNull(result)
        assertEquals(created.id, result.id)
        assertEquals("XYZ789", result.code)
    }

    @Test
    fun `findById returns null when id does not exist`() {
        val result = sessionRepository.findById(UUID.randomUUID())

        assertNull(result)
    }

    // ── findByCode ────────────────────────────────────────────────────────────

    @Test
    fun `findByCode returns session when code exists`() {
        sessionRepository.create(examId, professorId, "CODE01")

        val result = sessionRepository.findByCode("CODE01")

        assertNotNull(result)
        assertEquals("CODE01", result.code)
    }

    @Test
    fun `findByCode returns null when code does not exist`() {
        val result = sessionRepository.findByCode("ZZZZZZ")

        assertNull(result)
    }

    // ── findBySupervisor ──────────────────────────────────────────────────────

    @Test
    fun `findBySupervisor returns only sessions belonging to that professor`() {
        sessionRepository.create(examId, professorId,      "AAA001")
        sessionRepository.create(examId, professorId,      "BBB002")
        sessionRepository.create(examId, otherProfessorId, "CCC003")

        val results = sessionRepository.findBySupervisor(professorId)

        assertEquals(2, results.size)
        assertTrue(results.all { it.supervisorId == professorId })
    }

    @Test
    fun `findBySupervisor returns empty list when professor has no sessions`() {
        val results = sessionRepository.findBySupervisor(professorId)

        assertTrue(results.isEmpty())
    }

    // ── updateStatus ──────────────────────────────────────────────────────────

    @Test
    fun `updateStatus PENDING to ACTIVE sets startedAt and returns true`() {
        val session = sessionRepository.create(examId, professorId, "ACT001")

        val updated = sessionRepository.updateStatus(session.id, SessionStatus.ACTIVE)
        val result  = sessionRepository.findById(session.id)!!

        assertTrue(updated)
        assertEquals(SessionStatus.ACTIVE, result.status)
        assertNotNull(result.startedAt)
        assertNull(result.endedAt)
    }

    @Test
    fun `updateStatus ACTIVE to ENDED sets endedAt and returns true`() {
        val session = sessionRepository.create(examId, professorId, "END001")
        sessionRepository.updateStatus(session.id, SessionStatus.ACTIVE)

        val updated = sessionRepository.updateStatus(session.id, SessionStatus.ENDED)
        val result  = sessionRepository.findById(session.id)!!

        assertTrue(updated)
        assertEquals(SessionStatus.ENDED, result.status)
        assertNotNull(result.endedAt)
    }

    @Test
    fun `updateStatus returns false when session does not exist`() {
        val updated = sessionRepository.updateStatus(UUID.randomUUID(), SessionStatus.ACTIVE)

        assertFalse(updated)
    }
}
