package com.oeims.routes

import com.oeims.models.dto.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * HTTP-layer tests for POST /participants/{id}/heartbeat.
 *
 * The heartbeat endpoint is student-only and enforces ownership:
 * only the student who joined the session owns that participant record
 * and may send a heartbeat for it.
 */
class ParticipantRoutesTest : BaseRouteTest() {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun ApplicationTestBuilder.register(
        email: String, password: String, role: String
    ): AuthResponse = jsonClient().post("/auth/register") {
        contentType(ContentType.Application.Json)
        setBody(RegisterRequest(email, password, role))
    }.body()

    /**
     * Full setup: creates a professor, a student, an exam, a session, and has the
     * student join the session.
     *
     * @return Triple(professorToken, studentToken, participantId)
     */
    private suspend fun ApplicationTestBuilder.setup(
        profEmail: String = "prof@isel.pt",
        studentEmail: String = "student@isel.pt"
    ): Triple<String, String, String> {
        val prof = register(profEmail, "password123", "PROFESSOR")
        val student = register(studentEmail, "password123", "STUDENT")

        val exam = jsonClient().post("/exams") {
            bearerAuth(prof.token)
            contentType(ContentType.Application.Json)
            setBody(CreateExamRequest("LEIC-AED T1 C.3.07", null, 60))
        }.body<ExamResponse>()

        val session = jsonClient().post("/sessions") {
            bearerAuth(prof.token)
            contentType(ContentType.Application.Json)
            setBody(CreateSessionRequest(exam.id))
        }.body<SessionResponse>()

        val join = jsonClient().post("/sessions/join") {
            bearerAuth(student.token)
            contentType(ContentType.Application.Json)
            setBody(JoinSessionRequest(session.code))
        }.body<JoinSessionResponse>()

        return Triple(prof.token, student.token, join.participantId)
    }

    // ── POST /participants/{id}/heartbeat ─────────────────────────────────────

    @Test
    fun `student sends a heartbeat for their own participant and receives 204`() = routeTest {
        val (_, studentToken, participantId) = setup()

        val response = jsonClient().post("/participants/$participantId/heartbeat") {
            bearerAuth(studentToken)
        }

        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    @Test
    fun `heartbeat without a token returns 403`() = routeTest {
        val (_, _, participantId) = setup()

        val response = jsonClient().post("/participants/$participantId/heartbeat")

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `professor token cannot access the heartbeat endpoint and receives 403`() = routeTest {
        val (profToken, _, participantId) = setup()

        val response = jsonClient().post("/participants/$participantId/heartbeat") {
            bearerAuth(profToken)
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `a different student cannot send a heartbeat for another student's participant`() = routeTest {
        val (_, _, participantId) = setup(studentEmail = "student1@isel.pt")
        val otherStudent = register("student2@isel.pt", "password123", "STUDENT")

        val response = jsonClient().post("/participants/$participantId/heartbeat") {
            bearerAuth(otherStudent.token)
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `heartbeat for a non-existent participant returns 404`() = routeTest {
        val student = register("student@isel.pt", "password123", "STUDENT")

        val response = jsonClient().post("/participants/00000000-0000-0000-0000-000000000000/heartbeat") {
            bearerAuth(student.token)
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `heartbeat with a malformed participant UUID returns 400`() = routeTest {
        val student = register("student@isel.pt", "password123", "STUDENT")

        val response = jsonClient().post("/participants/not-a-uuid/heartbeat") {
            bearerAuth(student.token)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
