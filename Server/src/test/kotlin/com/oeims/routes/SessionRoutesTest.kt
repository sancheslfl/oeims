package com.oeims.routes

import com.oeims.models.dto.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * HTTP-layer tests for the session lifecycle:
 *
 *   POST   /sessions               — professor creates session (PENDING)
 *   GET    /sessions/{id}          — professor reads session
 *   POST   /sessions/{id}/start    — professor moves session to ACTIVE
 *   POST   /sessions/{id}/end      — professor moves session to ENDED
 *   POST   /sessions/join          — student joins by code
 *   GET    /sessions/{id}/participants
 *   GET    /sessions/{id}/events
 */
class SessionRoutesTest : BaseRouteTest() {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun ApplicationTestBuilder.register(
        email: String, password: String, role: String
    ): AuthResponse = jsonClient().post("/auth/register") {
        contentType(ContentType.Application.Json)
        setBody(RegisterRequest(email, password, role))
    }.body()

    private suspend fun ApplicationTestBuilder.createExam(
        professorToken: String, title: String = "LEIC-AED T1 C.3.07"
    ): ExamResponse = jsonClient().post("/exams") {
        bearerAuth(professorToken)
        contentType(ContentType.Application.Json)
        setBody(CreateExamRequest(title, null, 60))
    }.body()

    private suspend fun ApplicationTestBuilder.createSession(
        professorToken: String, examId: String
    ): SessionResponse = jsonClient().post("/sessions") {
        bearerAuth(professorToken)
        contentType(ContentType.Application.Json)
        setBody(CreateSessionRequest(examId))
    }.body()

    private suspend fun ApplicationTestBuilder.startSession(
        professorToken: String, sessionId: String
    ) = jsonClient().post("/sessions/$sessionId/start") { bearerAuth(professorToken) }

    private suspend fun ApplicationTestBuilder.endSession(
        professorToken: String, sessionId: String
    ) = jsonClient().post("/sessions/$sessionId/end") { bearerAuth(professorToken) }

    private suspend fun ApplicationTestBuilder.joinSession(
        studentToken: String, code: String
    ) = jsonClient().post("/sessions/join") {
        bearerAuth(studentToken)
        contentType(ContentType.Application.Json)
        setBody(JoinSessionRequest(code))
    }

    // ── POST /sessions ────────────────────────────────────────────────────────

    @Test
    fun `professor creates a session and receives 201 with PENDING status`() = routeTest {
        val prof    = register("prof@isel.pt", "password123", "PROFESSOR")
        val exam    = createExam(prof.token)
        val client  = jsonClient()

        val response = client.post("/sessions") {
            bearerAuth(prof.token)
            contentType(ContentType.Application.Json)
            setBody(CreateSessionRequest(exam.id))
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.body<SessionResponse>()
        assertEquals("PENDING", body.status)
        assertEquals(exam.id, body.examId)
        assertEquals(prof.userId, body.supervisorId)
        assertNotNull(body.code)
    }

    @Test
    fun `creating a session without a token returns 403`() = routeTest {
        val prof   = register("prof@isel.pt", "password123", "PROFESSOR")
        val exam   = createExam(prof.token)
        val client = jsonClient()

        val response = client.post("/sessions") {
            contentType(ContentType.Application.Json)
            setBody(CreateSessionRequest(exam.id))
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `student token cannot create a session and receives 403`() = routeTest {
        val prof    = register("prof@isel.pt", "password123", "PROFESSOR")
        val student = register("student@isel.pt", "password123", "STUDENT")
        val exam    = createExam(prof.token)
        val client  = jsonClient()

        val response = client.post("/sessions") {
            bearerAuth(student.token)
            contentType(ContentType.Application.Json)
            setBody(CreateSessionRequest(exam.id))
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `creating a session for a non-existent exam returns 404`() = routeTest {
        val prof   = register("prof@isel.pt", "password123", "PROFESSOR")
        val client = jsonClient()

        val response = client.post("/sessions") {
            bearerAuth(prof.token)
            contentType(ContentType.Application.Json)
            setBody(CreateSessionRequest("00000000-0000-0000-0000-000000000000"))
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // ── GET /sessions/{id} ────────────────────────────────────────────────────

    @Test
    fun `professor fetches a session by id and receives the correct session`() = routeTest {
        val prof    = register("prof@isel.pt", "password123", "PROFESSOR")
        val exam    = createExam(prof.token)
        val session = createSession(prof.token, exam.id)

        val response = jsonClient().get("/sessions/${session.id}") { bearerAuth(prof.token) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(session.id, response.body<SessionResponse>().id)
    }

    @Test
    fun `fetching a non-existent session returns 404`() = routeTest {
        val prof = register("prof@isel.pt", "password123", "PROFESSOR")

        val response = jsonClient().get("/sessions/00000000-0000-0000-0000-000000000000") {
            bearerAuth(prof.token)
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // ── POST /sessions/{id}/start ─────────────────────────────────────────────

    @Test
    fun `professor starts a PENDING session and receives 200 with ACTIVE status`() = routeTest {
        val prof    = register("prof@isel.pt", "password123", "PROFESSOR")
        val session = createSession(prof.token, createExam(prof.token).id)

        val response = startSession(prof.token, session.id)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ACTIVE", response.body<SessionResponse>().status)
    }

    @Test
    fun `a different professor cannot start a session they do not own`() = routeTest {
        val prof1   = register("prof1@isel.pt", "password123", "PROFESSOR")
        val prof2   = register("prof2@isel.pt", "password123", "PROFESSOR")
        val session = createSession(prof1.token, createExam(prof1.token).id)

        val response = startSession(prof2.token, session.id)

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `starting an already ACTIVE session returns 409`() = routeTest {
        val prof    = register("prof@isel.pt", "password123", "PROFESSOR")
        val session = createSession(prof.token, createExam(prof.token).id)
        startSession(prof.token, session.id)

        val response = startSession(prof.token, session.id)

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `starting an ENDED session returns 409`() = routeTest {
        val prof    = register("prof@isel.pt", "password123", "PROFESSOR")
        val session = createSession(prof.token, createExam(prof.token).id)
        startSession(prof.token, session.id)
        endSession(prof.token, session.id)

        val response = startSession(prof.token, session.id)

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    // ── POST /sessions/{id}/end ───────────────────────────────────────────────

    @Test
    fun `professor ends an ACTIVE session and receives 200 with ENDED status`() = routeTest {
        val prof    = register("prof@isel.pt", "password123", "PROFESSOR")
        val session = createSession(prof.token, createExam(prof.token).id)
        startSession(prof.token, session.id)

        val response = endSession(prof.token, session.id)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ENDED", response.body<SessionResponse>().status)
    }

    @Test
    fun `ending a PENDING session returns 409`() = routeTest {
        val prof    = register("prof@isel.pt", "password123", "PROFESSOR")
        val session = createSession(prof.token, createExam(prof.token).id)

        val response = endSession(prof.token, session.id)

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `ending an already ENDED session returns 409`() = routeTest {
        val prof    = register("prof@isel.pt", "password123", "PROFESSOR")
        val session = createSession(prof.token, createExam(prof.token).id)
        startSession(prof.token, session.id)
        endSession(prof.token, session.id)

        val response = endSession(prof.token, session.id)

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `a different professor cannot end a session they do not own`() = routeTest {
        val prof1   = register("prof1@isel.pt", "password123", "PROFESSOR")
        val prof2   = register("prof2@isel.pt", "password123", "PROFESSOR")
        val session = createSession(prof1.token, createExam(prof1.token).id)
        startSession(prof1.token, session.id)

        val response = endSession(prof2.token, session.id)

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    // ── POST /sessions/join ───────────────────────────────────────────────────

    @Test
    fun `student joins a PENDING session by code and receives 200`() = routeTest {
        val prof    = register("prof@isel.pt", "password123", "PROFESSOR")
        val student = register("student@isel.pt", "password123", "STUDENT")
        val exam    = createExam(prof.token, "LEIC-AED T1 C.3.07")
        val session = createSession(prof.token, exam.id)

        val response = joinSession(student.token, session.code)

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<JoinSessionResponse>()
        assertEquals(session.id, body.sessionId)
        assertEquals("LEIC-AED T1 C.3.07", body.examTitle)
    }

    @Test
    fun `student joins an ACTIVE session by code and receives 200`() = routeTest {
        val prof    = register("prof@isel.pt", "password123", "PROFESSOR")
        val student = register("student@isel.pt", "password123", "STUDENT")
        val session = createSession(prof.token, createExam(prof.token).id)
        startSession(prof.token, session.id)

        val response = joinSession(student.token, session.code)

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `joining with a wrong code returns 404`() = routeTest {
        val student = register("student@isel.pt", "password123", "STUDENT")

        val response = joinSession(student.token, "XXXXXX")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `joining an ENDED session returns 409`() = routeTest {
        val prof    = register("prof@isel.pt", "password123", "PROFESSOR")
        val student = register("student@isel.pt", "password123", "STUDENT")
        val session = createSession(prof.token, createExam(prof.token).id)
        startSession(prof.token, session.id)
        endSession(prof.token, session.id)

        val response = joinSession(student.token, session.code)

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `joining the same session twice is idempotent and returns 200 both times`() = routeTest {
        val prof    = register("prof@isel.pt", "password123", "PROFESSOR")
        val student = register("student@isel.pt", "password123", "STUDENT")
        val session = createSession(prof.token, createExam(prof.token).id)

        val first  = joinSession(student.token, session.code)
        val second = joinSession(student.token, session.code)

        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals(HttpStatusCode.OK, second.status)
        // Same participant returned both times
        assertEquals(
            first.body<JoinSessionResponse>().participantId,
            second.body<JoinSessionResponse>().participantId
        )
    }

    @Test
    fun `joining without a token returns 403`() = routeTest {
        val prof    = register("prof@isel.pt", "password123", "PROFESSOR")
        val session = createSession(prof.token, createExam(prof.token).id)

        val response = jsonClient().post("/sessions/join") {
            contentType(ContentType.Application.Json)
            setBody(JoinSessionRequest(session.code))
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    // ── GET /sessions/{id}/participants ───────────────────────────────────────

    @Test
    fun `professor lists participants and sees all students who have joined`() = routeTest {
        val prof     = register("prof@isel.pt", "password123", "PROFESSOR")
        val student1 = register("s1@isel.pt", "password123", "STUDENT")
        val student2 = register("s2@isel.pt", "password123", "STUDENT")
        val session  = createSession(prof.token, createExam(prof.token).id)
        joinSession(student1.token, session.code)
        joinSession(student2.token, session.code)

        val response = jsonClient().get("/sessions/${session.id}/participants") {
            bearerAuth(prof.token)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<List<ParticipantResponse>>()
        assertEquals(2, body.size)
        val emails = body.map { it.email }
        assert("s1@isel.pt" in emails)
        assert("s2@isel.pt" in emails)
    }

    @Test
    fun `listing participants for a non-existent session returns 404`() = routeTest {
        val prof = register("prof@isel.pt", "password123", "PROFESSOR")

        val response = jsonClient().get("/sessions/00000000-0000-0000-0000-000000000000/participants") {
            bearerAuth(prof.token)
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // ── GET /sessions/{id}/events ─────────────────────────────────────────────

    @Test
    fun `professor lists events for a new session and receives an empty list`() = routeTest {
        val prof    = register("prof@isel.pt", "password123", "PROFESSOR")
        val session = createSession(prof.token, createExam(prof.token).id)

        val response = jsonClient().get("/sessions/${session.id}/events") {
            bearerAuth(prof.token)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(emptyList<EventResponse>(), response.body<List<EventResponse>>())
    }
}
