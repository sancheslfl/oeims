package com.oeims.services

import com.oeims.dto.EventResponse
import com.oeims.dto.ParticipantStatusUpdate
import com.oeims.models.ConnectionStatus
import com.oeims.models.Severity
import com.oeims.repositories.EventRecord
import com.oeims.repositories.ParticipantRecord
import com.oeims.repositories.interfaces.IEventRepository
import com.oeims.repositories.interfaces.IParticipantRepository
import com.oeims.websocket.IConnectionRegistry
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EventServiceTest {

    // ── Fakes ─────────────────────────────────────────────────────────────────

    private inner class FakeParticipantRepository : IParticipantRepository {
        val participants = mutableListOf<ParticipantRecord>()

        override fun findById(id: UUID): ParticipantRecord? = participants.find { it.id == id }
        override fun findBySession(sessionId: UUID): List<ParticipantRecord> =
            participants.filter { it.sessionId == sessionId }
        override fun findByUserAndSession(userId: UUID, sessionId: UUID): ParticipantRecord? =
            participants.find { it.userId == userId && it.sessionId == sessionId }
        override fun create(sessionId: UUID, userId: UUID): ParticipantRecord {
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
        override fun updateHeartbeat(id: UUID): Boolean = true
        override fun updateConnectionStatus(id: UUID, status: ConnectionStatus): Boolean = true
        override fun markTimedOut(threshold: Instant): List<ParticipantRecord> = emptyList()
    }

    private inner class FakeEventRepository : IEventRepository {
        val events = mutableListOf<EventRecord>()

        override fun create(participantId: UUID, monitorName: String, message: String, severity: Severity): EventRecord {
            val record = EventRecord(UUID.randomUUID(), participantId, monitorName, message, severity, Instant.now())
            events.add(record)
            return record
        }
        override fun findByParticipant(participantId: UUID): List<EventRecord> =
            events.filter { it.participantId == participantId }
        override fun findBySession(sessionId: UUID): List<EventRecord> =
            events // Fake: just return all; the test doesn't need session-level filtering here
    }

    private inner class FakeConnectionRegistry : IConnectionRegistry {
        val broadcastedEvents        = mutableListOf<Pair<UUID, EventResponse>>()
        val broadcastedStatusUpdates = mutableListOf<Pair<UUID, ParticipantStatusUpdate>>()

        override suspend fun broadcastEventToSession(sessionId: UUID, event: EventResponse) {
            broadcastedEvents.add(sessionId to event)
        }
        override suspend fun broadcastStatusUpdate(sessionId: UUID, update: ParticipantStatusUpdate) {
            broadcastedStatusUpdates.add(sessionId to update)
        }
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private lateinit var fakeParticipants: FakeParticipantRepository
    private lateinit var fakeEvents: FakeEventRepository
    private lateinit var fakeRegistry: FakeConnectionRegistry
    private lateinit var service: EventService

    private lateinit var sessionId: UUID
    private lateinit var participantId: UUID

    @BeforeEach
    fun setup() {
        fakeParticipants = FakeParticipantRepository()
        fakeEvents       = FakeEventRepository()
        fakeRegistry     = FakeConnectionRegistry()
        service          = EventService(fakeEvents, fakeParticipants, fakeRegistry)

        sessionId     = UUID.randomUUID()
        participantId = fakeParticipants.create(sessionId, UUID.randomUUID()).id
    }

    // ── handleEvent ───────────────────────────────────────────────────────────

    @Test
    fun `handleEvent returns response with correct fields`() = runBlocking {
        val response = service.handleEvent(participantId, "FocusMonitor", "Window lost focus", Severity.WARNING)

        assertEquals(participantId.toString(), response.participantId)
        assertEquals("FocusMonitor", response.monitorName)
        assertEquals("Window lost focus", response.message)
        assertEquals("WARNING", response.severity)
        assertNotNull(response.id)
        assertNotNull(response.occurredAt)
    }

    @Test
    fun `handleEvent persists the event`() = runBlocking {
        service.handleEvent(participantId, "FocusMonitor", "msg", Severity.INFO)

        assertEquals(1, fakeEvents.events.size)
        assertEquals("msg", fakeEvents.events[0].message)
    }

    @Test
    fun `handleEvent broadcasts the event to the correct session`() = runBlocking {
        service.handleEvent(participantId, "FocusMonitor", "msg", Severity.INFO)

        assertEquals(1, fakeRegistry.broadcastedEvents.size)
        assertEquals(sessionId, fakeRegistry.broadcastedEvents[0].first)
    }

    @Test
    fun `handleEvent broadcasts the correct event payload`() = runBlocking {
        val response = service.handleEvent(participantId, "ClipboardMonitor", "clipboard access", Severity.CRITICAL)

        val (_, broadcast) = fakeRegistry.broadcastedEvents[0]
        assertEquals(response.id, broadcast.id)
        assertEquals("CRITICAL", broadcast.severity)
    }

    @Test
    fun `handleEvent throws NoSuchElementException when participant does not exist`() {
        assertThrows<NoSuchElementException> {
            runBlocking {
                service.handleEvent(UUID.randomUUID(), "FocusMonitor", "msg", Severity.INFO)
            }
        }
    }

    @Test
    fun `handleEvent does not broadcast when participant does not exist`() {
        runCatching {
            runBlocking {
                service.handleEvent(UUID.randomUUID(), "FocusMonitor", "msg", Severity.INFO)
            }
        }

        assertTrue(fakeRegistry.broadcastedEvents.isEmpty())
    }

    // ── getSessionEvents ──────────────────────────────────────────────────────

    @Test
    fun `getSessionEvents returns all events for the session`() = runBlocking {
        service.handleEvent(participantId, "FocusMonitor",     "first",  Severity.INFO)
        service.handleEvent(participantId, "ClipboardMonitor", "second", Severity.WARNING)

        val results = service.getSessionEvents(sessionId)

        assertEquals(2, results.size)
    }

    @Test
    fun `getSessionEvents returns empty list when no events exist`() {
        val results = service.getSessionEvents(sessionId)

        assertTrue(results.isEmpty())
    }

    @Test
    fun `getSessionEvents returns responses with correct severity strings`() = runBlocking {
        service.handleEvent(participantId, "FocusMonitor", "msg", Severity.CRITICAL)

        val results = service.getSessionEvents(sessionId)

        assertEquals("CRITICAL", results[0].severity)
    }
}
