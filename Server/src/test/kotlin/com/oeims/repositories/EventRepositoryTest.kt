package com.oeims.repositories

import com.oeims.models.Events
import com.oeims.models.Exams
import com.oeims.models.Participants
import com.oeims.models.SessionStatus
import com.oeims.models.Sessions
import com.oeims.models.Severity
import com.oeims.models.UserRole
import com.oeims.models.Users
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EventRepositoryTest {
    private lateinit var database: TestDatabase
    private lateinit var eventRepository: EventRepository
    private lateinit var participantId: UUID
    private lateinit var otherParticipantId: UUID
    private lateinit var sessionId: UUID

    @BeforeEach
    fun setup() = runBlocking {
        database = TestDatabase(Users, Exams, Sessions, Participants, Events).also { it.connect() }

        val userRepo = UserRepository()
        val examRepo = ExamRepository()
        val sessionRepo = SessionRepository(Clock.systemUTC())
        val participantRepo = ParticipantRepository()

        val professorId = userRepo.create("prof@isel.pt", UserRole.PROFESSOR, "hash").id
        val examId = examRepo.create(professorId, "Networks", null, 90).id
        sessionId = sessionRepo.create(examId, professorId, "EVT001", "alunos.isel.pt")!!.id
        sessionRepo.updateStatus(sessionId, SessionStatus.ACTIVE)
        participantId = participantRepo.create(sessionId, "student1@alunos.isel.pt").id
        otherParticipantId = participantRepo.create(sessionId, "student2@alunos.isel.pt").id
        eventRepository = EventRepository()
    }

    @AfterEach
    fun teardown() {
        database.close()
    }

    private suspend fun createEvent(
        participantId: UUID,
        monitorName: String,
        message: String,
        severity: Severity,
    ) = eventRepository.create(participantId, monitorName, message, severity, null)

    @Test
    fun `create returns record with correct fields`(): Unit = runBlocking {
        val event = createEvent(participantId, "FocusMonitor", "Window lost focus", Severity.WARNING)

        assertEquals(participantId, event.participantId)
        assertEquals("FocusMonitor", event.monitorName)
        assertEquals("Window lost focus", event.message)
        assertEquals(Severity.WARNING, event.severity)
        assertNotNull(event.id)
        assertNotNull(event.occurredAt)
    }

    @Test
    fun `create stores all severity levels correctly`() = runBlocking {
        val info = createEvent(participantId, "ProcessMonitor", "proc started", Severity.INFO)
        val warning = createEvent(participantId, "FocusMonitor", "focus lost", Severity.WARNING)
        val critical = createEvent(participantId, "ClipboardMonitor", "clipboard", Severity.CRITICAL)

        assertEquals(Severity.INFO, info.severity)
        assertEquals(Severity.WARNING, warning.severity)
        assertEquals(Severity.CRITICAL, critical.severity)
    }

    @Test
    fun `findByParticipant returns only events for that participant`() = runBlocking {
        createEvent(participantId, "FocusMonitor", "A", Severity.INFO)
        createEvent(participantId, "FocusMonitor", "B", Severity.INFO)
        createEvent(otherParticipantId, "FocusMonitor", "C", Severity.INFO)

        val results = eventRepository.findByParticipant(participantId)

        assertEquals(2, results.size)
        assertTrue(results.all { it.participantId == participantId })
    }

    @Test
    fun `findByParticipant returns events ordered by occurredAt ascending`() = runBlocking {
        createEvent(participantId, "FocusMonitor", "first", Severity.INFO)
        createEvent(participantId, "ClipboardMonitor", "second", Severity.WARNING)
        createEvent(participantId, "ProcessMonitor", "third", Severity.CRITICAL)

        val results = eventRepository.findByParticipant(participantId)

        assertEquals(3, results.size)
        assertTrue(results[0].occurredAt <= results[1].occurredAt)
        assertTrue(results[1].occurredAt <= results[2].occurredAt)
    }

    @Test
    fun `findBySession returns events from all participants in the session`() = runBlocking {
        createEvent(participantId, "FocusMonitor", "from p1", Severity.INFO)
        createEvent(otherParticipantId, "FocusMonitor", "from p2", Severity.WARNING)

        val results = eventRepository.findBySession(sessionId)

        assertEquals(2, results.size)
    }

    @Test
    fun `findBySession does not return events from a different session`() = runBlocking {
        val userRepo = UserRepository()
        val examRepo = ExamRepository()
        val sessionRepo = SessionRepository(Clock.systemUTC())
        val participantRepo = ParticipantRepository()
        val professorId = userRepo.create("prof2@isel.pt", UserRole.PROFESSOR, "hash").id
        val examId = examRepo.create(professorId, "Algebra", null, 60).id
        val otherSessionId = sessionRepo.create(examId, professorId, "OTH002", "alunos.isel.pt")!!.id
        val otherSessionParticipantId = participantRepo.create(otherSessionId, "student3@alunos.isel.pt").id

        createEvent(otherSessionParticipantId, "FocusMonitor", "other session", Severity.INFO)
        createEvent(participantId, "FocusMonitor", "this session", Severity.INFO)

        val results = eventRepository.findBySession(sessionId)

        assertEquals(1, results.size)
        assertEquals("this session", results[0].message)
    }
}
