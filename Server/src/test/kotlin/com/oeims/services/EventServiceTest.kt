package com.oeims.services

import com.oeims.exceptions.NotFoundException
import com.oeims.models.ConnectionStatus
import com.oeims.models.SessionStatus
import com.oeims.models.Severity
import com.oeims.models.ids.toParticipantId
import com.oeims.models.ids.toSessionId
import com.oeims.repositories.EventRecord
import com.oeims.repositories.ParticipantRecord
import com.oeims.repositories.SessionRecord
import com.oeims.repositories.interfaces.IEventRepository
import com.oeims.repositories.interfaces.IParticipantRepository
import com.oeims.repositories.interfaces.ISessionRepository
import com.oeims.sse.SseBroadcaster
import com.oeims.sse.SseChannels
import com.oeims.sse.SseEvent
import com.oeims.sse.SseMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventServiceTest {

    // ── Fakes ─────────────────────────────────────────────────────────────────

    private inner class FakeParticipantRepository : IParticipantRepository {
        val participants = mutableListOf<ParticipantRecord>()

        override suspend fun findById(id: UUID): ParticipantRecord? = participants.find { it.id == id }
        override suspend fun findBySession(sessionId: UUID): List<ParticipantRecord> =
            participants.filter { it.sessionId == sessionId }
        override suspend fun findByUserAndSession(userId: UUID, sessionId: UUID): ParticipantRecord? =
            participants.find { it.userId == userId && it.sessionId == sessionId }
        override suspend fun create(sessionId: UUID, userId: UUID): ParticipantRecord {
            val record = ParticipantRecord(
                id               = UUID.randomUUID(),
                sessionId        = sessionId,
                userId           = userId,
                email            = "student@test.pt",
                connectionStatus = ConnectionStatus.CONNECTED,
                lastHeartbeat    = null,
                joinedAt         = Instant.now()
            )
            participants.add(record)
            return record
        }
        override suspend fun updateHeartbeat(id: UUID): Boolean = true
        override suspend fun updateConnectionStatus(id: UUID, status: ConnectionStatus): Boolean = true
        override suspend fun markTimedOut(threshold: Instant): List<ParticipantRecord> = emptyList()
    }

    private inner class FakeEventRepository : IEventRepository {
        val events = mutableListOf<EventRecord>()

        override suspend fun create(participantId: UUID, monitorName: String, message: String, severity: Severity): EventRecord {
            val record = EventRecord(UUID.randomUUID(), participantId, monitorName, message, severity, Instant.now())
            events.add(record)
            return record
        }
        override suspend fun findByParticipant(participantId: UUID): List<EventRecord> =
            events.filter { it.participantId == participantId }
        override suspend fun findBySession(sessionId: UUID): List<EventRecord> = events
    }

    private inner class FakeSessionRepository : ISessionRepository {
        val sessions = mutableMapOf<UUID, SessionRecord>()

        override suspend fun findById(id: UUID): SessionRecord? = sessions[id]
        override suspend fun findByCode(code: String): SessionRecord? = sessions.values.find { it.code == code }
        override suspend fun findBySupervisor(supervisorId: UUID): List<SessionRecord> = emptyList()
        override suspend fun findLatestOpenBySupervisor(supervisorId: UUID): SessionRecord? = null
        override suspend fun create(examId: UUID, supervisorId: UUID, code: String): SessionRecord =
            throw UnsupportedOperationException()
        override suspend fun updateStatus(id: UUID, status: SessionStatus): Boolean = false
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private lateinit var fakeParticipants: FakeParticipantRepository
    private lateinit var fakeEvents: FakeEventRepository
    private lateinit var fakeSessions: FakeSessionRepository
    private lateinit var sseBroadcaster: SseBroadcaster
    private lateinit var service: EventService

    private lateinit var sessionId: UUID
    private lateinit var participantId: UUID

    @BeforeEach
    fun setup() = runBlocking {
        fakeParticipants = FakeParticipantRepository()
        fakeEvents       = FakeEventRepository()
        fakeSessions     = FakeSessionRepository()
        sseBroadcaster   = SseBroadcaster()
        service          = EventService(fakeEvents, fakeParticipants, fakeSessions, sseBroadcaster)

        sessionId     = UUID.randomUUID()
        participantId = fakeParticipants.create(sessionId, UUID.randomUUID()).id

        fakeSessions.sessions[sessionId] = SessionRecord(
            id           = sessionId,
            examId       = UUID.randomUUID(),
            supervisorId = UUID.randomUUID(),
            code         = "TEST01",
            status       = SessionStatus.ACTIVE,
            createdAt    = Instant.now(),
            startedAt    = Instant.now(),
            endedAt      = null
        )
    }

    // ── handleEvent ───────────────────────────────────────────────────────────

    @Test
    fun `handleEvent returns response with correct fields`() = runBlocking {
        val response = service.handleEvent(participantId.toParticipantId(), "FocusMonitor", "Window lost focus", Severity.WARNING)

        assertNotNull(response)
        assertEquals(participantId.toString(), response.participantId)
        assertEquals("FocusMonitor", response.monitorName)
        assertEquals("Window lost focus", response.message)
        assertEquals("WARNING", response.severity)
        assertNotNull(response.id)
        assertNotNull(response.occurredAt)
    }

    @Test
    fun `handleEvent persists the event`() = runBlocking {
        service.handleEvent(participantId.toParticipantId(), "FocusMonitor", "msg", Severity.INFO)

        assertEquals(1, fakeEvents.events.size)
        assertEquals("msg", fakeEvents.events[0].message)
    }

    @Test
    fun `handleEvent publishes to the correct SSE channel`() = runBlocking {
        val sseChannel = SseChannels.session(sessionId.toSessionId())
        val received   = Channel<SseMessage>(Channel.UNLIMITED)
        val subscribed = CompletableDeferred<Unit>()

        val job = launch {
            sseBroadcaster.subscribe(sseChannel)
                .onSubscription { subscribed.complete(Unit) }
                .collect { received.send(it) }
        }

        subscribed.await()
        service.handleEvent(participantId.toParticipantId(), "FocusMonitor", "msg", Severity.INFO)

        val message = withTimeout(1_000) { received.receive() }
        job.cancel()

        assertEquals(SseEvent.PARTICIPANT_EVENT_RECEIVED, message.event)
    }

    @Test
    fun `handleEvent returns null when session is not ACTIVE`() = runBlocking {
        fakeSessions.sessions[sessionId] = fakeSessions.sessions[sessionId]!!.copy(status = SessionStatus.PENDING)

        val response = service.handleEvent(participantId.toParticipantId(), "FocusMonitor", "msg", Severity.INFO)

        assertNull(response)
    }

    @Test
    fun `handleEvent throws NotFoundException when participant does not exist`() {
        assertThrows<NotFoundException> {
            runBlocking { service.handleEvent(UUID.randomUUID().toParticipantId(), "FocusMonitor", "msg", Severity.INFO) }
        }
    }

    @Test
    fun `handleEvent does not persist when session is not ACTIVE`() = runBlocking {
        fakeSessions.sessions[sessionId] = fakeSessions.sessions[sessionId]!!.copy(status = SessionStatus.PENDING)

        service.handleEvent(participantId.toParticipantId(), "FocusMonitor", "msg", Severity.INFO)

        assertTrue(fakeEvents.events.isEmpty())
    }

    // ── getSessionEvents ──────────────────────────────────────────────────────

    @Test
    fun `getSessionEvents returns all events for the session`() = runBlocking {
        service.handleEvent(participantId.toParticipantId(), "FocusMonitor",     "first",  Severity.INFO)
        service.handleEvent(participantId.toParticipantId(), "ClipboardMonitor", "second", Severity.WARNING)

        val results = service.getSessionEvents(sessionId.toSessionId())

        assertEquals(2, results.size)
    }

    @Test
    fun `getSessionEvents returns empty list when no events exist`() = runBlocking {
        val results = service.getSessionEvents(sessionId.toSessionId())

        assertTrue(results.isEmpty())
    }

    @Test
    fun `getSessionEvents returns responses with correct severity strings`() = runBlocking {
        service.handleEvent(participantId.toParticipantId(), "FocusMonitor", "msg", Severity.CRITICAL)

        val results = service.getSessionEvents(sessionId.toSessionId())

        assertEquals("CRITICAL", results[0].severity)
    }
}
