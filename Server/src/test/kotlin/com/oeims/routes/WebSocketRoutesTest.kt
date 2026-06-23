package com.oeims.routes

import com.oeims.models.dto.*
import io.ktor.client.call.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * WebSocket-layer tests for the two channels:
 *
 *   /ws/daemon/{participantId}   — student daemon sends monitoring events
 *   /ws/console/{sessionId}      — professor receives live event broadcasts
 *
 * ## The end-to-end broadcast test
 *
 * The console test coordinates two concurrent WebSocket connections:
 * 1. A professor connects and waits for frames via [launch].
 * 2. A [delay] gives the server-side `flow.collect` coroutine time to subscribe
 *    to the [kotlinx.coroutines.flow.SharedFlow] before anything is emitted.
 * 3. The daemon then sends an event, which is persisted and broadcast.
 * 4. The professor's connection receives the broadcast frame.
 *
 * The 100 ms delay is the standard synchronisation point for SharedFlow tests:
 * the flow has no replay, so the subscriber must exist before the first emit.
 */
class WebSocketRoutesTest : BaseRouteTest() {

    // ── Clients ───────────────────────────────────────────────────────────────

    /** Client with WebSocket support only — used for frame-level tests. */
    private fun ApplicationTestBuilder.wsClient() = createClient {
        install(WebSockets)
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private data class Ctx(
        val profToken: String,
        val studentToken: String,
        val sessionId: String,
        val participantId: String
    )

    /**
     * Registers a professor and a student, creates an exam + session, and has the
     * student join, returning all tokens and IDs needed by the tests.
     */
    private suspend fun ApplicationTestBuilder.setup(): Ctx {
        val http = jsonClient()

        val prof = http.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("prof@isel.pt", "password123", "PROFESSOR"))
        }.body<AuthResponse>()

        val student = http.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("student@isel.pt", "password123", "STUDENT"))
        }.body<AuthResponse>()

        val exam = http.post("/exams") {
            bearerAuth(prof.token)
            contentType(ContentType.Application.Json)
            setBody(CreateExamRequest("LEIC-AED T1 C.3.07", null, 60))
        }.body<ExamResponse>()

        val session = http.post("/sessions") {
            bearerAuth(prof.token)
            contentType(ContentType.Application.Json)
            setBody(CreateSessionRequest(exam.id))
        }.body<SessionResponse>()

        http.post("/sessions/${session.id}/start") { bearerAuth(prof.token) }

        val join = http.post("/sessions/join") {
            bearerAuth(student.token)
            contentType(ContentType.Application.Json)
            setBody(JoinSessionRequest(session.code))
        }.body<JoinSessionResponse>()

        return Ctx(prof.token, student.token, session.id, join.participantId)
    }

    // ── Daemon channel ────────────────────────────────────────────────────────

    @Test
    fun `valid daemon frame is persisted and returned by the events endpoint`() = routeTest {
        val ctx = setup()
        val http = jsonClient()

        wsClient().webSocket("/ws/daemon/${ctx.participantId}", {
            bearerAuth(ctx.studentToken)
        }) {
            send(Json.encodeToString(SentinelEventMessage("FocusMonitor", "Window lost focus", "Warning")))
            delay(50)   // let the server process the frame before we close
            close(CloseReason(CloseReason.Codes.NORMAL, "done"))
        }

        val events = http.get("/sessions/${ctx.sessionId}/events") {
            bearerAuth(ctx.profToken)
        }.body<List<EventResponse>>()

        assertEquals(1, events.size)
        assertEquals("FocusMonitor", events[0].monitorName)
        assertEquals("Window lost focus", events[0].message)
        assertEquals("WARNING", events[0].severity)
    }

    @Test
    fun `malformed daemon frame is silently dropped and connection stays open`() = routeTest {
        val ctx = setup()
        val http = jsonClient()

        wsClient().webSocket("/ws/daemon/${ctx.participantId}", {
            bearerAuth(ctx.studentToken)
        }) {
            send("this is not valid json {{{")
            delay(50)
            // Send a valid frame afterwards — connection must still be alive
            send(Json.encodeToString(SentinelEventMessage("FocusMonitor", "valid msg", "Info")))
            delay(50)
            close(CloseReason(CloseReason.Codes.NORMAL, "done"))
        }

        val events = http.get("/sessions/${ctx.sessionId}/events") {
            bearerAuth(ctx.profToken)
        }.body<List<EventResponse>>()

        // Only the valid frame produced an event; the malformed one was silently dropped
        assertEquals(1, events.size)
        assertEquals("INFO", events[0].severity)
    }

    @Test
    fun `daemon frame with unknown severity is dropped and connection stays open`() = routeTest {
        val ctx = setup()
        val http = jsonClient()

        wsClient().webSocket("/ws/daemon/${ctx.participantId}", {
            bearerAuth(ctx.studentToken)
        }) {
            send(Json.encodeToString(SentinelEventMessage("FocusMonitor", "msg", "NotARealSeverity")))
            delay(50)
            // Valid frame after the bad one — connection must still be alive
            send(Json.encodeToString(SentinelEventMessage("FocusMonitor", "valid msg", "Critical")))
            delay(50)
            close(CloseReason(CloseReason.Codes.NORMAL, "done"))
        }

        val events = http.get("/sessions/${ctx.sessionId}/events") {
            bearerAuth(ctx.profToken)
        }.body<List<EventResponse>>()

        assertEquals(1, events.size)
        assertEquals("CRITICAL", events[0].severity)
    }

    @Test
    fun `multiple valid daemon frames all produce persisted events`() = routeTest {
        val ctx = setup()
        val http = jsonClient()

        wsClient().webSocket("/ws/daemon/${ctx.participantId}", {
            bearerAuth(ctx.studentToken)
        }) {
            send(Json.encodeToString(SentinelEventMessage("FocusMonitor", "lost focus", "Info")))
            send(Json.encodeToString(SentinelEventMessage("ClipboardMonitor", "clipboard access", "Warning")))
            send(Json.encodeToString(SentinelEventMessage("ProcessMonitor", "unknown process", "Critical")))
            delay(50)
            close(CloseReason(CloseReason.Codes.NORMAL, "done"))
        }

        val events = http.get("/sessions/${ctx.sessionId}/events") {
            bearerAuth(ctx.profToken)
        }.body<List<EventResponse>>()

        assertEquals(3, events.size)
    }

    @Test
    fun `daemon connection is closed with VIOLATED_POLICY when a different student connects`() = routeTest {
        val ctx = setup()

        // Register a second student who does NOT own this participant
        val other = jsonClient().post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("other@isel.pt", "password123", "STUDENT"))
        }.body<AuthResponse>()

        var receivedCloseReason: CloseReason? = null

        wsClient().webSocket("/ws/daemon/${ctx.participantId}", {
            bearerAuth(other.token)
        }) {
            // Server closes the connection immediately; await the close reason
            receivedCloseReason = closeReason.await()
        }

        assertEquals(CloseReason.Codes.VIOLATED_POLICY, receivedCloseReason?.knownReason)
    }

    @Test
    fun `daemon connection is closed with VIOLATED_POLICY when participant does not exist`() = routeTest {
        val ctx = setup()
        val nonExistentId = "00000000-0000-0000-0000-000000000000"
        var receivedCloseReason: CloseReason? = null

        wsClient().webSocket("/ws/daemon/$nonExistentId", {
            bearerAuth(ctx.studentToken)
        }) {
            receivedCloseReason = closeReason.await()
        }

        assertEquals(CloseReason.Codes.VIOLATED_POLICY, receivedCloseReason?.knownReason)
    }

}
