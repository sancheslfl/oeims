package com.oeims.routes

import com.oeims.models.dto.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Test
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
        professorToken: String, examId: String, allowedEmailDomain: String = "isel.pt"
    ): SessionResponse = jsonClient().post("/sessions") {
        bearerAuth(professorToken)
        contentType(ContentType.Application.Json)
        setBody(CreateSessionRequest(examId, allowedEmailDomain))
    }.body()

    private suspend fun ApplicationTestBuilder.startSession(
        professorToken: String, sessionId: String
    ) = jsonClient().post("/sessions/$sessionId/start") { bearerAuth(professorToken) }

    private suspend fun ApplicationTestBuilder.endSession(
        professorToken: String, sessionId: String
    ) = jsonClient().post("/sessions/$sessionId/end") { bearerAuth(professorToken) }

    private suspend fun ApplicationTestBuilder.requestJoin(
        code: String, email: String
    ) = jsonClient().post("/sessions/$code/join") {
        contentType(ContentType.Application.Json)
        setBody(EmailJoinRequest(email))
    }

    private suspend fun ApplicationTestBuilder.verifyJoin(
        token: String
    ) = jsonClient().post("/sessions/join/verify") {
        contentType(ContentType.Application.Json)
        setBody(VerifyJoinRequest(token))
    }

    private fun tokenFromEmail(): String =
        URLDecoder.decode(
            (capturingEmailSender.lastLink ?: error("No verification email was captured")).substringAfter("token="),
            StandardCharsets.UTF_8
        )

    private suspend fun ApplicationTestBuilder.joinAsAdditionalSupervisor(
        professorToken: String, code: String
    ) = jsonClient().post("/sessions/join-as-supervisor") {
        bearerAuth(professorToken)
        contentType(ContentType.Application.Json)
        setBody(JoinSessionRequest(code))
    }

    // ── POST /sessions ────────────────────────────────────────────────────────

    @Test
    fun `professor creates a session and receives 201 with PENDING status`() = routeTest {
        val prof = register("prof@isel.pt", "password123", "PROFESSOR")
        val exam = createExam(prof.token)
        val client = jsonClient()

        val response = client.post("/sessions") {
            bearerAuth(prof.token)
            contentType(ContentType.Application.Json)
            setBody(CreateSessionRequest(exam.id, "isel.pt"))
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
        val prof = register("prof@isel.pt", "password123", "PROFESSOR")
        val exam = createExam(prof.token)
        val client = jsonClient()

        val response = client.post("/sessions") {
            contentType(ContentType.Application.Json)
            setBody(CreateSessionRequest(exam.id))
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `student token cannot create a session and receives 403`() = routeTest {
        val prof = register("prof@isel.pt", "password123", "PROFESSOR")
        val student = register("student@isel.pt", "password123", "STUDENT")
        val exam = createExam(prof.token)
        val client = jsonClient()

        val response = client.post("/sessions") {
            bearerAuth(student.token)
            contentType(ContentType.Application.Json)
            setBody(CreateSessionRequest(exam.id))
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `creating a session for a non-existent exam returns 404`() = routeTest {
        val prof = register("prof@isel.pt", "password123", "PROFESSOR")
        val client = jsonClient()

        val response = client.post("/sessions") {
            bearerAuth(prof.token)
            contentType(ContentType.Application.Json)
            setBody(CreateSessionRequest("00000000-0000-0000-0000-000000000000", "isel.pt"))
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // ── GET /sessions/{id} ────────────────────────────────────────────────────

    @Test
    fun `professor fetches a session by id and receives the correct session`() = routeTest {
        val prof = register("prof@isel.pt", "password123", "PROFESSOR")
        val exam = createExam(prof.token)
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
        val prof = register("prof@isel.pt", "password123", "PROFESSOR")
        val session = createSession(prof.token, createExam(prof.token).id)

        val response = startSession(prof.token, session.id)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ACTIVE", response.body<SessionResponse>().status)
    }

    @Test
    fun `a different professor cannot start a session they do not own`() = routeTest {
        val prof1 = register("prof1@isel.pt", "password123", "PROFESSOR")
        val prof2 = register("prof2@isel.pt", "password123", "PROFESSOR")
        val session = createSession(prof1.token, createExam(prof1.token).id)

        val response = startSession(prof2.token, session.id)

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `starting an already ACTIVE session returns 409`() = routeTest {
        val prof = register("prof@isel.pt", "password123", "PROFESSOR")
        val session = createSession(prof.token, createExam(prof.token).id)
        startSession(prof.token, session.id)

        val response = startSession(prof.token, session.id)

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `starting an ENDED session returns 409`() = routeTest {
        val prof = register("prof@isel.pt", "password123", "PROFESSOR")
        val session = createSession(prof.token, createExam(prof.token).id)
        startSession(prof.token, session.id)
        endSession(prof.token, session.id)

        val response = startSession(prof.token, session.id)

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    // ── POST /sessions/{id}/end ───────────────────────────────────────────────

    @Test
    fun `professor ends an ACTIVE session and receives 200 with ENDED status`() = routeTest {
        val prof = register("prof@isel.pt", "password123", "PROFESSOR")
        val session = createSession(prof.token, createExam(prof.token).id)
        startSession(prof.token, session.id)

        val response = endSession(prof.token, session.id)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ENDED", response.body<SessionResponse>().status)
    }

    @Test
    fun `ending a PENDING session returns 409`() = routeTest {
        val prof = register("prof@isel.pt", "password123", "PROFESSOR")
        val session = createSession(prof.token, createExam(prof.token).id)

        val response = endSession(prof.token, session.id)

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `ending an already ENDED session returns 409`() = routeTest {
        val prof = register("prof@isel.pt", "password123", "PROFESSOR")
        val session = createSession(prof.token, createExam(prof.token).id)
        startSession(prof.token, session.id)
        endSession(prof.token, session.id)

        val response = endSession(prof.token, session.id)

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `a different professor cannot end a session they do not own`() = routeTest {
        val prof1 = register("prof1@isel.pt", "password123", "PROFESSOR")
        val prof2 = register("prof2@isel.pt", "password123", "PROFESSOR")
        val session = createSession(prof1.token, createExam(prof1.token).id)
        startSession(prof1.token, session.id)

        val response = endSession(prof2.token, session.id)

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    // ── POST /sessions/{code}/join  +  POST /sessions/join/verify ─────────────

    @Test
    fun `requesting to join a PENDING session returns 202 and verifying yields a participant`() = routeTest {
        val prof = register("prof@isel.pt", "password123", "PROFESSOR")
        val session = createSession(prof.token, createExam(prof.token).id)

        val request = requestJoin(session.code, "student@isel.pt")
        assertEquals(HttpStatusCode.Accepted, request.status)

        val verify = verifyJoin(tokenFromEmail())
        assertEquals(HttpStatusCode.OK, verify.status)
        assertTrue(verify.body<VerifyJoinResponse>().participantId.isNotBlank())
    }

    @Test
    fun `requesting to join an ACTIVE session returns 202 and verifying returns 200`() = routeTest {
        val prof = register("prof@isel.pt", "password123", "PROFESSOR")
        val session = createSession(prof.token, createExam(prof.token).id)
        startSession(prof.token, session.id)

        assertEquals(HttpStatusCode.Accepted, requestJoin(session.code, "student@isel.pt").status)
        assertEquals(HttpStatusCode.OK, verifyJoin(tokenFromEmail()).status)
    }

    @Test
    fun `requesting to join with a wrong code returns 404`() = routeTest {
        val response = requestJoin("XXXXXX", "student@isel.pt")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `requesting to join an ENDED session returns 409`() = routeTest {
        val prof = register("prof@isel.pt", "password123", "PROFESSOR")
        val session = createSession(prof.token, createExam(prof.token).id)
        startSession(prof.token, session.id)
        endSession(prof.token, session.id)

        val response = requestJoin(session.code, "student@isel.pt")

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `requesting to join with an email outside the allowed domain returns 403`() = routeTest {
        val prof = register("prof@isel.pt", "password123", "PROFESSOR")
        val session = createSession(prof.token, createExam(prof.token).id)

        val response = requestJoin(session.code, "intruder@gmail.com")

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `requesting to join an already-joined email is accepted and creates no second participant`() = routeTest {
        val prof = register("prof@isel.pt", "password123", "PROFESSOR")
        val session = createSession(prof.token, createExam(prof.token).id)

        joinAsParticipant(session.code, "student@isel.pt")

        val second = requestJoin(session.code, "student@isel.pt")
        assertEquals(HttpStatusCode.Accepted, second.status)

        val participants = jsonClient().get("/sessions/${session.id}/participants") {
            bearerAuth(prof.token)
        }.body<List<ParticipantResponse>>()
        assertEquals(1, participants.size)
    }

    @Test
    fun `verifying with a malformed token returns 400`() = routeTest {
        val response = jsonClient().post("/sessions/join/verify") {
            contentType(ContentType.Application.Json)
            setBody(VerifyJoinRequest("not-a-jwt"))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // ── GET /sessions/active ──────────────────────────────────────────────────

    @Test
    fun `professor fetches active sessions and sees PENDING and ACTIVE ones`() = routeTest {
        val prof = register("prof@isel.pt", "password123", "PROFESSOR")
        val exam = createExam(prof.token)
        val s1 = createSession(prof.token, exam.id)
        val s2 = createSession(prof.token, exam.id)
        startSession(prof.token, s2.id)

        val response = jsonClient().get("/sessions/active") { bearerAuth(prof.token) }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<List<SessionResponse>>()
        assertEquals(2, body.size)
        assertTrue(body.any { it.id == s1.id })
        assertTrue(body.any { it.id == s2.id })
    }

    @Test
    fun `active sessions does not include ENDED sessions`() = routeTest {
        val prof = register("prof@isel.pt", "password123", "PROFESSOR")
        val session = createSession(prof.token, createExam(prof.token).id)
        startSession(prof.token, session.id)
        endSession(prof.token, session.id)

        val response = jsonClient().get("/sessions/active") { bearerAuth(prof.token) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(emptyList<SessionResponse>(), response.body<List<SessionResponse>>())
    }

    @Test
    fun `fetching active sessions without a token returns 403`() = routeTest {
        val response = jsonClient().get("/sessions/active")

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    // ── POST /sessions/join-as-supervisor ────────────────────────────────────

    @Test
    fun `professor joins an active session as additional supervisor and receives 200`() = routeTest {
        val prof1 = register("prof1@isel.pt", "password123", "PROFESSOR")
        val prof2 = register("prof2@isel.pt", "password123", "PROFESSOR")
        val session = createSession(prof1.token, createExam(prof1.token).id)
        startSession(prof1.token, session.id)

        val response = joinAsAdditionalSupervisor(prof2.token, session.code)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(session.id, response.body<SessionResponse>().id)
    }

    @Test
    fun `joining as supervisor with a wrong code returns 404`() = routeTest {
        val prof = register("prof@isel.pt", "password123", "PROFESSOR")

        val response = joinAsAdditionalSupervisor(prof.token, "XXXXXX")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `professor joins a PENDING session as supervisor and receives 200`() = routeTest {
        val prof1 = register("prof1@isel.pt", "password123", "PROFESSOR")
        val prof2 = register("prof2@isel.pt", "password123", "PROFESSOR")
        val session = createSession(prof1.token, createExam(prof1.token).id)

        val response = joinAsAdditionalSupervisor(prof2.token, session.code)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(session.id, response.body<SessionResponse>().id)
    }

    @Test
    fun `joining an ENDED session as supervisor returns 409`() = routeTest {
        val prof1 = register("prof1@isel.pt", "password123", "PROFESSOR")
        val prof2 = register("prof2@isel.pt", "password123", "PROFESSOR")
        val session = createSession(prof1.token, createExam(prof1.token).id)
        startSession(prof1.token, session.id)
        endSession(prof1.token, session.id)

        val response = joinAsAdditionalSupervisor(prof2.token, session.code)

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `joining as supervisor without a token returns 403`() = routeTest {
        val prof = register("prof@isel.pt", "password123", "PROFESSOR")
        val session = createSession(prof.token, createExam(prof.token).id)
        startSession(prof.token, session.id)

        val response = jsonClient().post("/sessions/join-as-supervisor") {
            contentType(ContentType.Application.Json)
            setBody(JoinSessionRequest(session.code))
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    // ── GET /sessions/{id}/participants ───────────────────────────────────────

    @Test
    fun `professor lists participants and sees all students who have joined`() = routeTest {
        val prof = register("prof@isel.pt", "password123", "PROFESSOR")
        val session = createSession(prof.token, createExam(prof.token).id)
        joinAsParticipant(session.code, "s1@isel.pt")
        joinAsParticipant(session.code, "s2@isel.pt")

        val response = jsonClient().get("/sessions/${session.id}/participants") {
            bearerAuth(prof.token)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.body<List<ParticipantResponse>>()
        assertEquals(2, body.size)
        val emails = body.map { it.email }
        assertTrue("s1@isel.pt" in emails)
        assertTrue("s2@isel.pt" in emails)
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
        val prof = register("prof@isel.pt", "password123", "PROFESSOR")
        val session = createSession(prof.token, createExam(prof.token).id)

        val response = jsonClient().get("/sessions/${session.id}/events") {
            bearerAuth(prof.token)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(emptyList<EventResponse>(), response.body<List<EventResponse>>())
    }
}
