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
import java.time.Instant
import java.util.*
import kotlin.test.*

class ParticipantRepositoryTest {

    private lateinit var participantRepository: ParticipantRepository
    private lateinit var sessionRepository: SessionRepository
    private lateinit var studentId: UUID
    private lateinit var otherStudentId: UUID
    private lateinit var sessionId: UUID
    private var keepAlive: java.sql.Connection? = null

    @BeforeEach
    fun setup() = runBlocking {
        keepAlive = DriverManager.getConnection("jdbc:sqlite:file:testdb?mode=memory&cache=shared")
        Database.connect(
            url = "jdbc:sqlite:file:testdb?mode=memory&cache=shared",
            driver = "org.sqlite.JDBC"
        )
        transaction { SchemaUtils.create(Users, Exams, Sessions, Participants) }

        val userRepo = UserRepository()
        val examRepo = ExamRepository()
        sessionRepository = SessionRepository()

        val professorId = userRepo.create("prof@isel.pt", UserRole.PROFESSOR, "hash").id
        studentId = userRepo.create("student1@alunos.isel.pt", UserRole.STUDENT, "hash").id
        otherStudentId = userRepo.create("student2@alunos.isel.pt", UserRole.STUDENT, "hash").id
        val examId = examRepo.create(professorId, "Networks", null, 90).id
        sessionId = sessionRepository.create(examId, professorId, "SES001").id

        // Activate the session so updateTimedOut considers it
        sessionRepository.updateStatus(sessionId, SessionStatus.ACTIVE)

        participantRepository = ParticipantRepository()
    }

    @AfterEach
    fun teardown() {
        transaction { SchemaUtils.drop(Participants, Sessions, Exams, Users) }
        keepAlive?.close()
        keepAlive = null
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    fun `create returns record with CONNECTED status and correct fields`() = runBlocking {
        val participant = participantRepository.create(sessionId, studentId)

        assertEquals(sessionId, participant.sessionId)
        assertEquals(studentId, participant.userId)
        assertEquals(ConnectionStatus.CONNECTED, participant.connectionStatus)
        assertNull(participant.lastHeartbeat)
        assertNotNull(participant.id)
        assertNotNull(participant.joinedAt)
        assertNotNull(participant.email)
    }

    @Test
    fun `create assigns unique ids to each participant`() = runBlocking {
        val p1 = participantRepository.create(sessionId, studentId)
        val p2 = participantRepository.create(sessionId, otherStudentId)

        assertTrue(p1.id != p2.id)
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    fun `findById returns participant when id exists`() = runBlocking {
        val created = participantRepository.create(sessionId, studentId)

        val result = participantRepository.findById(created.id)

        assertNotNull(result)
        assertEquals(created.id, result.id)
        assertEquals(studentId, result.userId)
    }

    @Test
    fun `findById returns null when id does not exist`() = runBlocking {
        val result = participantRepository.findById(UUID.randomUUID())

        assertNull(result)
    }

    // ── findBySession ─────────────────────────────────────────────────────────

    @Test
    fun `findBySession returns all participants in the session`() = runBlocking {
        participantRepository.create(sessionId, studentId)
        participantRepository.create(sessionId, otherStudentId)

        val results = participantRepository.findBySession(sessionId)

        assertEquals(2, results.size)
        assertTrue(results.all { it.sessionId == sessionId })
    }

    @Test
    fun `findBySession returns empty list when session has no participants`() = runBlocking {
        val results = participantRepository.findBySession(sessionId)

        assertTrue(results.isEmpty())
    }

    // ── findByUserAndSession ──────────────────────────────────────────────────

    @Test
    fun `findByUserAndSession returns participant when both match`() = runBlocking {
        participantRepository.create(sessionId, studentId)

        val result = participantRepository.findByUserAndSession(studentId, sessionId)

        assertNotNull(result)
        assertEquals(studentId, result.userId)
        assertEquals(sessionId, result.sessionId)
    }

    @Test
    fun `findByUserAndSession returns null when student is not in that session`() = runBlocking {
        val result = participantRepository.findByUserAndSession(studentId, sessionId)

        assertNull(result)
    }

    // ── updateHeartbeat ───────────────────────────────────────────────────────

    @Test
    fun `updateHeartbeat sets lastHeartbeat and returns true`() = runBlocking {
        val participant = participantRepository.create(sessionId, studentId)

        val updated = participantRepository.updateHeartbeat(participant.id)
        val result = participantRepository.findById(participant.id)!!

        assertTrue(updated)
        assertNotNull(result.lastHeartbeat)
        assertEquals(ConnectionStatus.CONNECTED, result.connectionStatus)
    }

    @Test
    fun `updateHeartbeat returns false when participant does not exist`() = runBlocking {
        val updated = participantRepository.updateHeartbeat(UUID.randomUUID())

        assertFalse(updated)
    }

    // ── updateConnectionStatus ────────────────────────────────────────────────

    @Test
    fun `updateConnectionStatus changes status and returns true`() = runBlocking {
        val participant = participantRepository.create(sessionId, studentId)

        val updated = participantRepository.updateConnectionStatus(participant.id, ConnectionStatus.DISCONNECTED)
        val result = participantRepository.findById(participant.id)!!

        assertTrue(updated)
        assertEquals(ConnectionStatus.DISCONNECTED, result.connectionStatus)
    }

    @Test
    fun `updateConnectionStatus returns false when participant does not exist`() = runBlocking {
        val updated = participantRepository.updateConnectionStatus(UUID.randomUUID(), ConnectionStatus.TIMED_OUT)

        assertFalse(updated)
    }

    // ── updateTimedOut ──────────────────────────────────────────────────────────

    @Test
    fun `markTimedOut marks CONNECTED participants whose heartbeat is older than threshold`() = runBlocking {
        val participant = participantRepository.create(sessionId, studentId)
        participantRepository.updateHeartbeat(participant.id)

        // threshold = now + 1 hour → every heartbeat is "older than" this
        val threshold = Instant.now().plusSeconds(3600)
        val timedOut = participantRepository.updateTimedOut(threshold)

        assertEquals(1, timedOut.size)
        assertEquals(participant.id, timedOut[0].id)

        val updated = participantRepository.findById(participant.id)!!
        assertEquals(ConnectionStatus.TIMED_OUT, updated.connectionStatus)
    }

    @Test
    fun `markTimedOut does not mark participants whose heartbeat is recent`() = runBlocking {
        val participant = participantRepository.create(sessionId, studentId)
        participantRepository.updateHeartbeat(participant.id)

        // threshold = 1 hour ago → no recent heartbeat is older than this
        val threshold = Instant.now().minusSeconds(3600)
        val timedOut = participantRepository.updateTimedOut(threshold)

        assertTrue(timedOut.isEmpty())

        val unchanged = participantRepository.findById(participant.id)!!
        assertEquals(ConnectionStatus.CONNECTED, unchanged.connectionStatus)
    }

    @Test
    fun `markTimedOut ignores participants that have never sent a heartbeat`() = runBlocking {
        // lastHeartbeat is NULL — the `lessEq` condition does not match NULL
        participantRepository.create(sessionId, studentId)

        val threshold = Instant.now().plusSeconds(3600)
        val timedOut = participantRepository.updateTimedOut(threshold)

        assertTrue(timedOut.isEmpty())
    }

    @Test
    fun `markTimedOut only considers participants in ACTIVE sessions`() = runBlocking {
        // Create a second session and leave it PENDING
        val userRepo = UserRepository()
        val examRepo = ExamRepository()
        val prof2Id = userRepo.create("prof2@isel.pt", UserRole.PROFESSOR, "hash").id
        val exam2Id = examRepo.create(prof2Id, "Algebra", null, 60).id
        val pendingSessionId = sessionRepository.create(exam2Id, prof2Id, "PEN001").id
        // Note: do NOT activate this session

        val participantInPending = participantRepository.create(pendingSessionId, otherStudentId)
        participantRepository.updateHeartbeat(participantInPending.id)

        val threshold = Instant.now().plusSeconds(3600)
        val timedOut = participantRepository.updateTimedOut(threshold)

        // Participant in PENDING session must NOT be timed out
        assertTrue(timedOut.none { it.id == participantInPending.id })
    }
}
