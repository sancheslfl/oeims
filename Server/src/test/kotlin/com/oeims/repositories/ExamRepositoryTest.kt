package com.oeims.repositories

import com.oeims.models.Exams
import com.oeims.models.UserRole
import com.oeims.models.Users
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExamRepositoryTest {
    private lateinit var database: TestDatabase
    private lateinit var examRepository: ExamRepository
    private lateinit var professorId: UUID
    private lateinit var otherProfessorId: UUID

    @BeforeEach
    fun setup() = runBlocking {
        database = TestDatabase(Users, Exams).also { it.connect() }

        val userRepository = UserRepository()
        professorId = userRepository.create("prof1@isel.pt", UserRole.PROFESSOR, "hash1").id
        otherProfessorId = userRepository.create("prof2@isel.pt", UserRole.PROFESSOR, "hash2").id
        examRepository = ExamRepository()
    }

    @AfterEach
    fun teardown() {
        database.close()
    }

    @Test
    fun `create returns record with correct fields`(): Unit = runBlocking {
        val exam = examRepository.create(professorId, "Operating Systems", "OS exam", 90)

        assertEquals("Operating Systems", exam.title)
        assertEquals("OS exam", exam.description)
        assertEquals(90, exam.durationMins)
        assertEquals(professorId, exam.createdBy)
        assertNotNull(exam.id)
        assertNotNull(exam.createdAt)
    }

    @Test
    fun `create stores null description correctly`() = runBlocking {
        val exam = examRepository.create(professorId, "Networks", null, 60)

        assertNull(exam.description)
    }

    @Test
    fun `create assigns a unique id to each exam`() = runBlocking {
        val exam1 = examRepository.create(professorId, "Exam A", null, 60)
        val exam2 = examRepository.create(professorId, "Exam B", null, 60)

        assertTrue(exam1.id != exam2.id)
    }

    @Test
    fun `findById returns exam when id exists`() = runBlocking {
        val created = examRepository.create(professorId, "Algorithms", null, 120)

        val result = examRepository.findById(created.id)

        assertNotNull(result)
        assertEquals(created.id, result.id)
        assertEquals("Algorithms", result.title)
    }

    @Test
    fun `findById returns null when id does not exist`() = runBlocking {
        val result = examRepository.findById(UUID.randomUUID())

        assertNull(result)
    }

    @Test
    fun `findByTitle returns all exams with that title`() = runBlocking {
        examRepository.create(professorId, "Calculus", null, 90)
        examRepository.create(otherProfessorId, "Calculus", null, 90)
        examRepository.create(professorId, "Physics", null, 60)

        val results = examRepository.findByTitle("Calculus")

        assertEquals(2, results.size)
        assertTrue(results.all { it.title == "Calculus" })
    }

    @Test
    fun `findByTitle returns empty list when no match`() = runBlocking {
        examRepository.create(professorId, "Networks", null, 60)

        val results = examRepository.findByTitle("Algebra")

        assertTrue(results.isEmpty())
    }

    @Test
    fun `findByTitle is case sensitive`() = runBlocking {
        examRepository.create(professorId, "Networks", null, 60)

        val results = examRepository.findByTitle("networks")

        assertTrue(results.isEmpty())
    }

    @Test
    fun `findByProfessor returns only exams belonging to that professor`() = runBlocking {
        examRepository.create(professorId, "Exam A", null, 60)
        examRepository.create(professorId, "Exam B", null, 60)
        examRepository.create(otherProfessorId, "Exam C", null, 60)

        val results = examRepository.findByProfessor(professorId)

        assertEquals(2, results.size)
        assertTrue(results.all { it.createdBy == professorId })
    }

    @Test
    fun `findByProfessor returns empty list when professor has no exams`() = runBlocking {
        val results = examRepository.findByProfessor(professorId)

        assertTrue(results.isEmpty())
    }

    @Test
    fun `findAll returns all exams regardless of professor`() = runBlocking {
        examRepository.create(professorId, "Exam A", null, 60)
        examRepository.create(otherProfessorId, "Exam B", null, 60)

        val results = examRepository.findAll()

        assertEquals(2, results.size)
    }

    @Test
    fun `findAll returns empty list when no exams exist`() = runBlocking {
        val results = examRepository.findAll()

        assertTrue(results.isEmpty())
    }
}
