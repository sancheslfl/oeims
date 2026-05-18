package com.oeims.routes

import com.oeims.dto.AuthResponse
import com.oeims.dto.CreateExamRequest
import com.oeims.dto.ExamResponse
import com.oeims.dto.RegisterRequest
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * HTTP-layer tests for:
 *   POST   /exams
 *   GET    /exams
 *   GET    /exams?title=...
 *   GET    /exams/{id}
 *
 * All endpoints are protected by "auth-professor"; students and unauthenticated
 * callers receive 403.
 */
class ExamRoutesTest : BaseRouteTest() {

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Register a user and return the full AuthResponse (contains token + userId). */
    private suspend fun ApplicationTestBuilder.register(
        email: String, password: String, role: String
    ): AuthResponse = jsonClient().post("/auth/register") {
        contentType(ContentType.Application.Json)
        setBody(RegisterRequest(email, password, role))
    }.body()

    /** Create an exam as the given professor and return the ExamResponse. */
    private suspend fun ApplicationTestBuilder.createExam(
        professorToken: String,
        title: String       = "Algorithms",
        durationMins: Int   = 90,
        description: String? = null
    ): ExamResponse = jsonClient().post("/exams") {
        bearerAuth(professorToken)
        contentType(ContentType.Application.Json)
        setBody(CreateExamRequest(title, description, durationMins))
    }.body()

    // ── POST /exams ───────────────────────────────────────────────────────────

    @Test
    fun `professor creates an exam and receives 201 with ExamResponse`() = routeTest {
        val prof   = register("prof@isel.pt", "password123", "PROFESSOR")
        val client = jsonClient()

        val response = client.post("/exams") {
            bearerAuth(prof.token)
            contentType(ContentType.Application.Json)
            setBody(CreateExamRequest("Linear Algebra", "Final exam", 120))
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.body<ExamResponse>()
        assertEquals("Linear Algebra", body.title)
        assertEquals(120, body.durationMins)
        assertEquals("Final exam", body.description)
        assertEquals(prof.userId, body.createdBy)
    }

    @Test
    fun `creating an exam without a token returns 403`() = routeTest {
        val client = jsonClient()

        val response = client.post("/exams") {
            contentType(ContentType.Application.Json)
            setBody(CreateExamRequest("Math", null, 90))
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `student token cannot create an exam and receives 403`() = routeTest {
        val student = register("student@isel.pt", "password123", "STUDENT")
        val client  = jsonClient()

        val response = client.post("/exams") {
            bearerAuth(student.token)
            contentType(ContentType.Application.Json)
            setBody(CreateExamRequest("Math", null, 90))
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    // ── GET /exams ────────────────────────────────────────────────────────────

    @Test
    fun `professor can list all exams and the count matches what was created`() = routeTest {
        val prof = register("prof@isel.pt", "password123", "PROFESSOR")
        createExam(prof.token, "Exam A")
        createExam(prof.token, "Exam B")
        createExam(prof.token, "Exam C")

        val response = jsonClient().get("/exams") { bearerAuth(prof.token) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(3, response.body<List<ExamResponse>>().size)
    }

    @Test
    fun `listing exams without a token returns 403`() = routeTest {
        val response = jsonClient().get("/exams")
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    // ── GET /exams?title=... ──────────────────────────────────────────────────
    //
    // findByTitle performs an exact-match query (SQL `=`), not a LIKE search.
    // Tests therefore use the full title as the query parameter.

    @Test
    fun `title filter returns the exact-matched exam`() = routeTest {
        val prof = register("prof@isel.pt", "password123", "PROFESSOR")
        createExam(prof.token, "Algorithms")
        createExam(prof.token, "Database Exam")

        val response = jsonClient().get("/exams?title=Algorithms") { bearerAuth(prof.token) }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<List<ExamResponse>>()
        assertEquals(1, body.size)
        assertEquals("Algorithms", body[0].title)
    }

    @Test
    fun `title filter with a non-existent title returns an empty list`() = routeTest {
        val prof = register("prof@isel.pt", "password123", "PROFESSOR")
        createExam(prof.token, "Physics")

        val response = jsonClient().get("/exams?title=Biology") { bearerAuth(prof.token) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(emptyList<ExamResponse>(), response.body<List<ExamResponse>>())
    }

    // ── GET /exams/{id} ───────────────────────────────────────────────────────

    @Test
    fun `professor fetches an exam by id and receives the correct exam`() = routeTest {
        val prof    = register("prof@isel.pt", "password123", "PROFESSOR")
        val created = createExam(prof.token, "Operating Systems", 100)

        val response = jsonClient().get("/exams/${created.id}") { bearerAuth(prof.token) }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<ExamResponse>()
        assertEquals(created.id, body.id)
        assertEquals("Operating Systems", body.title)
        assertEquals(100, body.durationMins)
    }

    @Test
    fun `fetching a non-existent exam returns 404`() = routeTest {
        val prof = register("prof@isel.pt", "password123", "PROFESSOR")

        val response = jsonClient().get("/exams/00000000-0000-0000-0000-000000000000") {
            bearerAuth(prof.token)
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `fetching an exam with a malformed UUID returns 400`() = routeTest {
        val prof = register("prof@isel.pt", "password123", "PROFESSOR")

        val response = jsonClient().get("/exams/not-a-uuid") { bearerAuth(prof.token) }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
