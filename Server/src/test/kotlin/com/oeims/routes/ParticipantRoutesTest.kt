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

    private suspend fun ApplicationTestBuilder.createProfAndSession(): Pair<String, SessionResponse> {
        val prof = register("prof@isel.pt", "password123", "PROFESSOR")

        val exam = jsonClient().post("/exams") {
            bearerAuth(prof.token)
            contentType(ContentType.Application.Json)
            setBody(CreateExamRequest("LEIC-AED T1 C.3.07", null, 60))
        }.body<ExamResponse>()

        val session = jsonClient().post("/sessions") {
            bearerAuth(prof.token)
            contentType(ContentType.Application.Json)
            setBody(CreateSessionRequest(exam.id, "isel.pt"))
        }.body<SessionResponse>()

        return prof.token to session
    }

    // Returns (professorToken, studentSentinelToken, participantId).
    private suspend fun ApplicationTestBuilder.setup(
        studentEmail: String = "student@isel.pt"
    ): Triple<String, String, String> {
        val (profToken, session) = createProfAndSession()
        val joined = joinAsParticipant(session.code, studentEmail)
        return Triple(profToken, joined.token, joined.participantId)
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
        val (_, session) = createProfAndSession()
        val victim = joinAsParticipant(session.code, "student1@isel.pt")
        val attacker = joinAsParticipant(session.code, "student2@isel.pt")

        val response = jsonClient().post("/participants/${victim.participantId}/heartbeat") {
            bearerAuth(attacker.token)
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `heartbeat for a non-existent participant returns 404`() = routeTest {
        val (_, studentToken, _) = setup()

        val response = jsonClient().post("/participants/00000000-0000-0000-0000-000000000000/heartbeat") {
            bearerAuth(studentToken)
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `heartbeat with a malformed participant UUID returns 400`() = routeTest {
        val (_, studentToken, _) = setup()

        val response = jsonClient().post("/participants/not-a-uuid/heartbeat") {
            bearerAuth(studentToken)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
