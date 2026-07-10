package com.oeims.routes

import com.oeims.models.dto.*
import io.ktor.client.call.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

class WebSocketRoutesTest : BaseRouteTest() {

    private fun ApplicationTestBuilder.wsClient() = createClient {
        install(WebSockets)
    }

    private fun sentinelFrame(
        monitorName: String,
        message: String,
        severity: String,
    ): String = Json.encodeToString<SentinelEventMessage>(
        SentinelEventMessage(
            monitorName = monitorName,
            message = message,
            severity = severity,
            occurredAt = Instant.now().toString(),
        )
    )

    private data class Ctx(
        val profToken: String,
        val studentToken: String,
        val sessionId: String,
        val participantId: String,
        val code: String
    )

    private suspend fun ApplicationTestBuilder.setup(): Ctx {
        val http = jsonClient()

        val prof = http.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("prof@isel.pt", "password123", "PROFESSOR"))
        }.body<AuthResponse>()

        val exam = http.post("/exams") {
            bearerAuth(prof.token)
            contentType(ContentType.Application.Json)
            setBody(CreateExamRequest("LEIC-AED T1 C.3.07", null, 60))
        }.body<ExamResponse>()

        val session = http.post("/sessions") {
            bearerAuth(prof.token)
            contentType(ContentType.Application.Json)
            setBody(CreateSessionRequest(exam.id, "isel.pt"))
        }.body<SessionResponse>()

        http.post("/sessions/${session.id}/start") { bearerAuth(prof.token) }

        val joined = joinAsParticipant(session.code, "student@isel.pt")

        return Ctx(prof.token, joined.token, session.id, joined.participantId, session.code)
    }

    @Test
    fun `valid daemon frame is persisted and returned by the events endpoint`() = routeTest {
        val ctx = setup()
        val http = jsonClient()

        wsClient().webSocket("/ws/daemon/${ctx.participantId}", {
            bearerAuth(ctx.studentToken)
        }) {
            send(sentinelFrame("FocusMonitor", "Window lost focus", "Warning"))
            delay(50.milliseconds)
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
            delay(50.milliseconds)
            send(sentinelFrame("FocusMonitor", "valid msg", "Info"))
            delay(50.milliseconds)
            close(CloseReason(CloseReason.Codes.NORMAL, "done"))
        }

        val events = http.get("/sessions/${ctx.sessionId}/events") {
            bearerAuth(ctx.profToken)
        }.body<List<EventResponse>>()

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
            send(sentinelFrame("FocusMonitor", "msg", "NotARealSeverity"))
            delay(50.milliseconds)
            send(sentinelFrame("FocusMonitor", "valid msg", "Critical"))
            delay(50.milliseconds)
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
            send(sentinelFrame("FocusMonitor", "lost focus", "Info"))
            send(sentinelFrame("ClipboardMonitor", "clipboard access", "Warning"))
            send(sentinelFrame("ProcessMonitor", "unknown process", "Critical"))
            delay(50.milliseconds)
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
        val other = joinAsParticipant(ctx.code, "other@isel.pt")
        var receivedCloseReason: CloseReason? = null

        wsClient().webSocket("/ws/daemon/${ctx.participantId}", {
            bearerAuth(other.token)
        }) {
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
