package com.oeims.repositories

import com.oeims.models.Exams
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExamRepositoryTest {

    private lateinit var examRepository: ExamRepository
    private lateinit var professorId: UUID
    private lateinit var otherProfessorId: UUID
    private var keepAlive: java.sql.Connection? = null

    @BeforeEach
    fun setup() {
        keepAlive = DriverManager.getConnection("jdbc:sqlite:file:testdb?mode=memory&cache=shared")
        Database.connect(
            url = "jdbc:sqlite:file:testdb?mode=memory&cache=shared",
            driver = "org.sqlite.JDBC"
        )
        transaction { SchemaUtils.create(Users, Exams) }

        val userRepository = UserRepository()
        professorId      = userRepository.create("prof1@isel.pt", UserRole.PROFESSOR, "hash1").id
        otherProfessorId = userRepository.create("prof2@isel.pt", UserRole.PROFESSOR, "hash2").id

        examRepository = ExamRepository()
    }

    @AfterEach
    fun teardown() {
        transaction { SchemaUtils.drop(Exams, Users) }
        keepAlive?.close()
        keepAlive = null
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    fun `create returns record with correct fields`() {
        val exam = examRepository.create(professorId, "Operating Systems", "OS exam", 90)

        assertEquals("Operating Systems", exam.title)
        assertEquals("OS exam", exam.description)
        assertEquals(90, exam.durationMins)
        assertEquals(professorId, exam.createdBy)
        assertNotNull(exam.id)
        assertNotNull(exam.createdAt)
    }

    @Test
    fun `create stores null description correctly`() {
        val exam = examRepository.create(professorId, "Networks", null, 60)

        assertNull(exam.description)
    }

    @Test
    fun `create assigns a unique id to each exam`() {
        val exam1 = examRepository.create(professorId, "Exam A", null, 60)
        val exam2 = examRepository.create(professorId, "Exam B", null, 60)

        assertTrue(exam1.id != exam2.id)
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    fun `findById returns exam when id exists`() {
        val created = examRepository.create(professorId, "Algorithms", null, 120)

        val result = examRepository.findById(created.id)

        assertNotNull(result)
        assertEquals(created.id, result.id)
        assertEquals("Algorithms", result.title)
    }

    @Test
    fun `findById returns null when id does not exist`() {
        val result = examRepository.findById(UUID.randomUUID())

        assertNull(result)
    }

    // ── findByTitle ───────────────────────────────────────────────────────────

    @Test
    fun `findByTitle returns all exams with that title`() {
        examRepository.create(professorId,      "Calculus", null, 90)
        examRepository.create(otherProfessorId, "Calculus", null, 90)
        examRepository.create(professorId,      "Physics",  null, 60)

        val results = examRepository.findByTitle("Calculus")

        assertEquals(2, results.size)
        assertTrue(results.all { it.title == "Calculus" })
    }

    @Test
    fun `findByTitle returns empty list when no match`() {
        examRepository.create(professorId, "Networks", null, 60)

        val results = examRepository.findByTitle("Algebra")

        assertTrue(results.isEmpty())
    }

    @Test
    fun `findByTitle is case sensitive`() {
        examRepository.create(professorId, "Networks", null, 60)

        val results = examRepository.findByTitle("networks")

        assertTrue(results.isEmpty())
    }

    // ── findByProfessor ───────────────────────────────────────────────────────

    @Test
    fun `findByProfessor returns only exams belonging to that professor`() {
        examRepository.create(professorId,      "Exam A", null, 60)
        examRepository.create(professorId,      "Exam B", null, 60)
        examRepository.create(otherProfessorId, "Exam C", null, 60)

        val results = examRepository.findByProfessor(professorId)

        assertEquals(2, results.size)
        assertTrue(results.all { it.createdBy == professorId })
    }

    @Test
    fun `findByProfessor returns empty list when professor has no exams`() {
        val results = examRepository.findByProfessor(professorId)

        assertTrue(results.isEmpty())
    }

    // ── findAll ───────────────────────────────────────────────────────────────

    @Test
    fun `findAll returns all exams regardless of professor`() {
        examRepository.create(professorId,      "Exam A", null, 60)
        examRepository.create(otherProfessorId, "Exam B", null, 60)

        val results = examRepository.findAll()

        assertEquals(2, results.size)
    }

    @Test
    fun `findAll returns empty list when no exams exist`() {
        val results = examRepository.findAll()

        assertTrue(results.isEmpty())
    }
}
