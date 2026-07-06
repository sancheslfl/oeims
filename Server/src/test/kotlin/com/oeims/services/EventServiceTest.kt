package com.oeims.services

import com.oeims.models.NotFoundException
import com.oeims.models.ConnectionStatus
import com.oeims.models.SessionStatus
import com.oeims.models.Severity

import com.oeims.repositories.interfaces.IEventRepository
import com.oeims.repositories.interfaces.IParticipantRepository
import com.oeims.repositories.interfaces.ISessionRepository
import com.oeims.connections.SseBroadcaster
import com.oeims.connections.SseChannels
import com.oeims.connections.SseEvent
import com.oeims.connections.SseMessage
import com.oeims.models.EventRecord
import com.oeims.models.ParticipantRecord
import com.oeims.models.SessionJoinRecord
import com.oeims.models.SessionRecord
import com.oeims.models.toParticipantId
import com.oeims.models.toSessionId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class EventServiceTest {

    private class FakeParticipantRepository : IParticipantRepository {
        val participants = mutableListOf<ParticipantRecord>()

        override suspend fun findById(id: UUID): ParticipantRecord? = participants.find { it.id == id }
        override suspend fun findBySession(sessionId: UUID): List<ParticipantRecord> =
            participants.filter { it.sessionId == sessionId }

        override suspend fun findByExamIdentityCode(examIdentityCode: String): ParticipantRecord? =
            participants.find { it.examIdentityCode == examIdentityCode }

        override suspend fun findByEmailAndSession(email: String, sessionId: UUID): ParticipantRecord? =
            participants.find { it.email == email && it.sessionId == sessionId }

        override suspend fun findConnectedBySession(sessionId: UUID): List<ParticipantRecord> =
            participants.filter { it.sessionId == sessionId && it.connectionStatus == ConnectionStatus.CONNECTED }

        override suspend fun create(sessionId: UUID, email: String): ParticipantRecord {
            val record = ParticipantRecord(
                id = UUID.randomUUID(),
                sessionId = sessionId,
                email = email,
                examIdentityCode = null,
                connectionStatus = ConnectionStatus.CONNECTED,
                lastHeartbeat = null,
                joinedAt = Instant.now()
            )
            participants.add(record)
            return record
        }

        override suspend fun updateHeartbeat(id: UUID): Boolean = true
        override suspend fun updateConnectionStatus(id: UUID, status: ConnectionStatus): Boolean = true
        override suspend fun updateTimedOut(threshold: Instant): List<ParticipantRecord> = emptyList()
        override suspend fun updateExamIdentityCode(participantId: UUID, examIdentityCode: String): Boolean =
            participants.any { it.id == participantId }
    }

    private class FakeEventRepository : IEventRepository {
        val events = mutableListOf<EventRecord>()

        override suspend fun create(
            participantId: UUID,
            monitorName: String,
            message: String,
            severity: Severity,
            occurredAt: Instant?
        ): EventRecord {
            val record =
                EventRecord(UUID.randomUUID(), participantId, monitorName, message, severity, occurredAt ?: Instant.now())
            events.add(record)
            return record
        }

        override suspend fun findByParticipant(participantId: UUID): List<EventRecord> =
            events.filter { it.participantId == participantId }

        override suspend fun findByParticipants(participantIds: List<UUID>): List<EventRecord> =
            if (participantIds.isEmpty()) emptyList()
            else events.filter { it.participantId in participantIds }

        override suspend fun findBySession(sessionId: UUID): List<EventRecord> = events
    }

    private class FakeSessionRepository : ISessionRepository {
        val sessions = mutableMapOf<UUID, SessionRecord>()

        override suspend fun findById(id: UUID): SessionRecord? = sessions[id]
        override suspend fun findByCode(code: String): SessionRecord? = sessions.values.find { it.code == code }
        override suspend fun findBySupervisor(supervisorId: UUID): List<SessionRecord> = emptyList()
        override suspend fun findLatestOpenBySupervisor(supervisorId: UUID): SessionRecord? = null
        override suspend fun create(
            examId: UUID,
            supervisorId: UUID,
            code: String,
            allowedEmailDomain: String,
        ): SessionRecord = throw UnsupportedOperationException()

        override suspend fun updateStatus(id: UUID, status: SessionStatus): Boolean = false
        override suspend fun addSupervisor(sessionId: UUID, userId: UUID) {}
        override suspend fun isSupervisor(sessionId: UUID, userId: UUID): Boolean = false
        override suspend fun findAllActive(): List<SessionRecord> = emptyList()
        override suspend fun updateJoinVerification(id: UUID, verifiedAt: Instant): Boolean {
            TODO("Not yet implemented")
        }

        override suspend fun findJoinRequestByJwtId(jwtId: String): SessionJoinRecord? {
            TODO("Not yet implemented")
        }

        override suspend fun createJoinRequest(
            sessionId: UUID,
            email: String,
            jwtId: String,
            expiresAt: Instant
        ): SessionJoinRecord {
            TODO("Not yet implemented")
        }
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
        fakeEvents = FakeEventRepository()
        fakeSessions = FakeSessionRepository()
        sseBroadcaster = SseBroadcaster()
        service = EventService(fakeEvents, fakeParticipants, fakeSessions, sseBroadcaster)

        sessionId = UUID.randomUUID()
        participantId = fakeParticipants.create(sessionId, "student@alunos.isel.pt").id

        fakeSessions.sessions[sessionId] = SessionRecord(
            id = sessionId,
            examId = UUID.randomUUID(),
            supervisorId = UUID.randomUUID(),
            code = "TEST01",
            allowedEmailDomain = "alunos.isel.pt",
            status = SessionStatus.ACTIVE,
            createdAt = Instant.now(),
            startedAt = Instant.now(),
            endedAt = null
        )
    }


    @Test
    fun `create event returns response with correct fields`(): Unit = runBlocking {
        val response =
            service.create(participantId.toParticipantId(), "FocusMonitor", "Window lost focus", Severity.WARNING)

        assertNotNull(response)
        assertEquals(participantId.toString(), response.participantId)
        assertEquals("FocusMonitor", response.monitorName)
        assertEquals("Window lost focus", response.message)
        assertEquals("WARNING", response.severity)
        assertNotNull(response.id)
        assertNotNull(response.occurredAt)
    }

    @Test
    fun `create event persists the event`() = runBlocking {
        service.create(participantId.toParticipantId(), "FocusMonitor", "msg", Severity.INFO)

        assertEquals(1, fakeEvents.events.size)
        assertEquals("msg", fakeEvents.events[0].message)
    }

    @Test
    fun `create event publishes to the correct SSE channel`() = runBlocking {
        val sseChannel = SseChannels.session(sessionId.toSessionId())
        val received = Channel<SseMessage>(Channel.UNLIMITED)
        val subscribed = CompletableDeferred<Unit>()

        val job = launch {
            sseBroadcaster.subscribe(sseChannel)
                .onSubscription { subscribed.complete(Unit) }
                .collect { received.send(it) }
        }

        subscribed.await()
        service.create(participantId.toParticipantId(), "FocusMonitor", "msg", Severity.INFO)

        val message = withTimeout(1_000.milliseconds) { received.receive() }
        job.cancel()

        assertEquals(SseEvent.PARTICIPANT_EVENT_RECEIVED, message.event)
    }

    @Test
    fun `create event returns null when session is not ACTIVE`() = runBlocking {
        fakeSessions.sessions[sessionId] = fakeSessions.sessions[sessionId]!!.copy(status = SessionStatus.PENDING)

        val response = service.create(participantId.toParticipantId(), "FocusMonitor", "msg", Severity.INFO)

        assertNull(response)
    }

    @Test
    fun `create event throws NotFoundException when participant does not exist`() {
        assertThrows<NotFoundException> {
            runBlocking {
                service.create(
                    UUID.randomUUID().toParticipantId(),
                    "FocusMonitor",
                    "msg",
                    Severity.INFO
                )
            }
        }
    }

    @Test
    fun `create event does not persist when session is not ACTIVE`() = runBlocking {
        fakeSessions.sessions[sessionId] = fakeSessions.sessions[sessionId]!!.copy(status = SessionStatus.PENDING)

        service.create(participantId.toParticipantId(), "FocusMonitor", "msg", Severity.INFO)

        assertTrue(fakeEvents.events.isEmpty())
    }

    // ── getSessionEvents ──────────────────────────────────────────────────────

    @Test
    fun `getSessionEvents returns all events for the session`() = runBlocking {
        service.create(participantId.toParticipantId(), "FocusMonitor", "first", Severity.INFO)
        service.create(participantId.toParticipantId(), "ClipboardMonitor", "second", Severity.WARNING)

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
        service.create(participantId.toParticipantId(), "FocusMonitor", "msg", Severity.CRITICAL)

        val results = service.getSessionEvents(sessionId.toSessionId())

        assertEquals("CRITICAL", results[0].severity)
    }
}
