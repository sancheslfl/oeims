package com.oeims.repositories

import com.oeims.models.*
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.sql.DriverManager
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EventRepositoryTest {

    private lateinit var eventRepository: EventRepository
    private lateinit var participantId: UUID
    private lateinit var otherParticipantId: UUID
    private lateinit var sessionId: UUID
    private var keepAlive: java.sql.Connection? = null

    @BeforeEach
    fun setup() = runBlocking {
        keepAlive = DriverManager.getConnection("jdbc:sqlite:file:testdb?mode=memory&cache=shared")
        Database.connect(
            url = "jdbc:sqlite:file:testdb?mode=memory&cache=shared",
            driver = "org.sqlite.JDBC"
        )
        transaction { SchemaUtils.create(Users, Exams, Sessions, Participants, Events) }

        val userRepo = UserRepository()
        val examRepo = ExamRepository()
        val sessionRepo = SessionRepository()
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
        transaction { SchemaUtils.drop(Events, Participants, Sessions, Exams, Users) }
        keepAlive?.close()
        keepAlive = null
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    fun `create returns record with correct fields`(): Unit = runBlocking {
        val event = eventRepository.create(participantId, "FocusMonitor", "Window lost focus", Severity.WARNING)

        assertEquals(participantId, event.participantId)
        assertEquals("FocusMonitor", event.monitorName)
        assertEquals("Window lost focus", event.message)
        assertEquals(Severity.WARNING, event.severity)
        assertNotNull(event.id)
        assertNotNull(event.occurredAt)
    }

    @Test
    fun `create assigns unique ids to each event`() = runBlocking {
        val e1 = eventRepository.create(participantId, "FocusMonitor", "msg1", Severity.INFO)
        val e2 = eventRepository.create(participantId, "FocusMonitor", "msg2", Severity.INFO)

        assertTrue(e1.id != e2.id)
    }

    @Test
    fun `create stores all severity levels correctly`() = runBlocking {
        val info = eventRepository.create(participantId, "ProcessMonitor", "proc started", Severity.INFO)
        val warning = eventRepository.create(participantId, "FocusMonitor", "focus lost", Severity.WARNING)
        val critical = eventRepository.create(participantId, "ClipboardMonitor", "clipboard", Severity.CRITICAL)

        assertEquals(Severity.INFO, info.severity)
        assertEquals(Severity.WARNING, warning.severity)
        assertEquals(Severity.CRITICAL, critical.severity)
    }

    // ── findByParticipant ─────────────────────────────────────────────────────

    @Test
    fun `findByParticipant returns only events for that participant`() = runBlocking {
        eventRepository.create(participantId, "FocusMonitor", "A", Severity.INFO)
        eventRepository.create(participantId, "FocusMonitor", "B", Severity.INFO)
        eventRepository.create(otherParticipantId, "FocusMonitor", "C", Severity.INFO)

        val results = eventRepository.findByParticipant(participantId)

        assertEquals(2, results.size)
        assertTrue(results.all { it.participantId == participantId })
    }

    @Test
    fun `findByParticipant returns empty list when participant has no events`() = runBlocking {
        val results = eventRepository.findByParticipant(participantId)

        assertTrue(results.isEmpty())
    }

    @Test
    fun `findByParticipant returns events ordered by occurredAt ascending`() = runBlocking {
        eventRepository.create(participantId, "FocusMonitor", "first", Severity.INFO)
        Thread.sleep(10) // ensure distinct timestamps
        eventRepository.create(participantId, "ClipboardMonitor", "second", Severity.WARNING)
        Thread.sleep(10)
        eventRepository.create(participantId, "ProcessMonitor", "third", Severity.CRITICAL)

        val results = eventRepository.findByParticipant(participantId)

        assertEquals(3, results.size)
        assertTrue(results[0].occurredAt <= results[1].occurredAt)
        assertTrue(results[1].occurredAt <= results[2].occurredAt)
    }

    @Test
    fun `findByParticipant returns empty list for unknown participant id`() = runBlocking {
        val results = eventRepository.findByParticipant(UUID.randomUUID())

        assertTrue(results.isEmpty())
    }

    // ── findBySession ─────────────────────────────────────────────────────────

    @Test
    fun `findBySession returns events from all participants in the session`() = runBlocking {
        eventRepository.create(participantId, "FocusMonitor", "from p1", Severity.INFO)
        eventRepository.create(otherParticipantId, "FocusMonitor", "from p2", Severity.WARNING)

        val results = eventRepository.findBySession(sessionId)

        assertEquals(2, results.size)
    }

    @Test
    fun `findBySession returns empty list when session has no events`() = runBlocking {
        val results = eventRepository.findBySession(sessionId)

        assertTrue(results.isEmpty())
    }

    @Test
    fun `findBySession returns events ordered by occurredAt ascending`() = runBlocking {
        eventRepository.create(participantId, "FocusMonitor", "first", Severity.INFO)
        Thread.sleep(10)
        eventRepository.create(otherParticipantId, "FocusMonitor", "second", Severity.WARNING)

        val results = eventRepository.findBySession(sessionId)

        assertEquals(2, results.size)
        assertTrue(results[0].occurredAt <= results[1].occurredAt)
    }

    @Test
    fun `findBySession does not return events from a different session`() = runBlocking {
        val userRepo2 = UserRepository()
        val examRepo2 = ExamRepository()
        val sessionRepo2 = SessionRepository()
        val partRepo2 = ParticipantRepository()

        val prof2Id = userRepo2.create("prof2@isel.pt", UserRole.PROFESSOR, "hash").id
        val exam2Id = examRepo2.create(prof2Id, "Algebra", null, 60).id
        val session2Id = sessionRepo2.create(exam2Id, prof2Id, "OTH002", "alunos.isel.pt")!!.id
        val part2Id = partRepo2.create(session2Id, "student3@alunos.isel.pt").id

        eventRepository.create(part2Id, "FocusMonitor", "other session", Severity.INFO)
        eventRepository.create(participantId, "FocusMonitor", "this session", Severity.INFO)

        val results = eventRepository.findBySession(sessionId)

        assertEquals(1, results.size)
        assertEquals("this session", results[0].message)
    }
}
