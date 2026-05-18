package com.oeims.services

import com.oeims.exceptions.NotFoundException
import com.oeims.exceptions.ValidationException
import com.oeims.repositories.ExamRecord
import com.oeims.repositories.interfaces.IExamRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExamServiceTest {

    // ── Fake ─────────────────────────────────────────────────────────────────

    private inner class FakeExamRepository : IExamRepository {
        val exams = mutableListOf<ExamRecord>()

        override suspend fun findById(id: UUID): ExamRecord? =
            exams.find { it.id == id }

        override suspend fun findAll(): List<ExamRecord> =
            exams.toList()

        override suspend fun findByTitle(title: String): List<ExamRecord> =
            exams.filter { it.title == title }

        override suspend fun findByProfessor(professorId: UUID): List<ExamRecord> =
            exams.filter { it.createdBy == professorId }

        override suspend fun create(createdBy: UUID, title: String, description: String?, durationMins: Int): ExamRecord {
            val record = ExamRecord(UUID.randomUUID(), createdBy, title, description, durationMins, Instant.now())
            exams.add(record)
            return record
        }
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private lateinit var fakeRepo: FakeExamRepository
    private lateinit var service: ExamService
    private val professorId = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        fakeRepo = FakeExamRepository()
        service  = ExamService(fakeRepo)
    }

    // ── createExam ────────────────────────────────────────────────────────────

    @Test
    fun `createExam returns response with correct fields`() = runBlocking {
        val response = service.createExam(professorId, "Networks", "Network fundamentals", 90)

        assertEquals("Networks", response.title)
        assertEquals("Network fundamentals", response.description)
        assertEquals(90, response.durationMins)
        assertEquals(professorId.toString(), response.createdBy)
        assertNotNull(response.id)
        assertNotNull(response.createdAt)
    }

    @Test
    fun `createExam stores null description`() = runBlocking {
        val response = service.createExam(professorId, "Networks", null, 90)

        assertNull(response.description)
    }

    @Test
    fun `createExam throws ValidationException when title is blank`() {
        assertThrows<ValidationException> {
            runBlocking { service.createExam(professorId, "  ", null, 90) }
        }
    }

    @Test
    fun `createExam throws ValidationException when duration is zero`() {
        assertThrows<ValidationException> {
            runBlocking { service.createExam(professorId, "Networks", null, 0) }
        }
    }

    @Test
    fun `createExam throws ValidationException when duration is negative`() {
        assertThrows<ValidationException> {
            runBlocking { service.createExam(professorId, "Networks", null, -1) }
        }
    }

    // ── getExamsByTitle ───────────────────────────────────────────────────────

    @Test
    fun `getExamsByTitle returns all exams with that title`() = runBlocking {
        service.createExam(professorId, "Calculus", null, 90)
        service.createExam(professorId, "Calculus", null, 60)
        service.createExam(professorId, "Physics",  null, 60)

        val results = service.getExamsByTitle("Calculus")

        assertEquals(2, results.size)
        assertTrue(results.all { it.title == "Calculus" })
    }

    @Test
    fun `getExamsByTitle returns empty list when no exams match`() = runBlocking {
        service.createExam(professorId, "Networks", null, 60)

        val results = service.getExamsByTitle("Algebra")

        assertTrue(results.isEmpty())
    }

    @Test
    fun `getExamsByTitle throws ValidationException when title is blank`() {
        assertThrows<ValidationException> {
            runBlocking { service.getExamsByTitle("  ") }
        }
    }

    // ── getAllExams ───────────────────────────────────────────────────────────

    @Test
    fun `getAllExams returns all exams`() = runBlocking {
        val other = UUID.randomUUID()
        service.createExam(professorId, "Exam A", null, 60)
        service.createExam(other,       "Exam B", null, 60)

        val results = service.getAllExams()

        assertEquals(2, results.size)
    }

    @Test
    fun `getAllExams returns empty list when no exams exist`() = runBlocking {
        val results = service.getAllExams()

        assertTrue(results.isEmpty())
    }

    // ── getExamById ───────────────────────────────────────────────────────────

    @Test
    fun `getExamById returns exam when it exists`() = runBlocking {
        val created = service.createExam(professorId, "Algorithms", null, 120)

        val result = service.getExamById(UUID.fromString(created.id))

        assertEquals(created.id, result.id)
        assertEquals("Algorithms", result.title)
    }

    @Test
    fun `getExamById throws NotFoundException when exam does not exist`() {
        assertThrows<NotFoundException> {
            runBlocking { service.getExamById(UUID.randomUUID()) }
        }
    }
}
