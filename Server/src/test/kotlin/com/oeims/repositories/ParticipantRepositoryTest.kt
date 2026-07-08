package com.oeims.repositories

import com.oeims.models.ConnectionStatus
import com.oeims.models.Exams
import com.oeims.models.Participants
import com.oeims.models.SessionStatus
import com.oeims.models.Sessions
import com.oeims.models.UserRole
import com.oeims.models.Users
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParticipantRepositoryTest {
    private lateinit var database: TestDatabase
    private lateinit var participantRepository: ParticipantRepository
    private lateinit var sessionRepository: SessionRepository
    private lateinit var sessionId: UUID

    @BeforeEach
    fun setup() = runBlocking {
        database = TestDatabase(Users, Exams, Sessions, Participants).also { it.connect() }
        val userRepo = UserRepository()
        val examRepo = ExamRepository()
        sessionRepository = SessionRepository(Clock.systemUTC())
        val professorId = userRepo.create("prof@isel.pt", UserRole.PROFESSOR, "hash").id
        val examId = examRepo.create(professorId, "Networks", null, 90).id
        sessionId = sessionRepository.create(examId, professorId, "SES001", "alunos.isel.pt")!!.id
        sessionRepository.updateStatus(sessionId, SessionStatus.ACTIVE)
        participantRepository = ParticipantRepository()
    }

    @AfterEach
    fun teardown() {
        database.close()
    }

    @Test
    fun `create stores a disconnected participant`() = runBlocking {
        val participant = participantRepository.create(sessionId, "student1@alunos.isel.pt")

        assertEquals(sessionId, participant.sessionId)
        assertEquals(ConnectionStatus.DISCONNECTED, participant.connectionStatus)
        assertNull(participant.lastHeartbeat)
        assertNull(participant.examIdentityCode)
    }

    @Test
    fun `find methods return matching participants only`() = runBlocking {
        val participant = participantRepository.create(sessionId, "student1@alunos.isel.pt")

        assertEquals(participant.id, participantRepository.findById(participant.id)?.id)
        assertEquals(1, participantRepository.findBySession(sessionId).size)
        assertEquals(participant.id, participantRepository.findByEmailAndSession("student1@alunos.isel.pt", sessionId)?.id)
        assertNull(participantRepository.findById(UUID.randomUUID()))
    }

    @Test
    fun `heartbeat marks participant as connected`() = runBlocking {
        val participant = participantRepository.create(sessionId, "student1@alunos.isel.pt")

        val updated = participantRepository.updateHeartbeat(participant.id)
        val result = participantRepository.findById(participant.id)!!

        assertTrue(updated)
        assertEquals(ConnectionStatus.CONNECTED, result.connectionStatus)
        assertNotNull(result.lastHeartbeat)
    }

    @Test
    fun `connection status can be updated`() = runBlocking {
        val participant = participantRepository.create(sessionId, "student1@alunos.isel.pt")

        assertTrue(participantRepository.updateConnectionStatus(participant.id, ConnectionStatus.TIMED_OUT))
        assertEquals(ConnectionStatus.TIMED_OUT, participantRepository.findById(participant.id)?.connectionStatus)
        assertFalse(participantRepository.updateConnectionStatus(UUID.randomUUID(), ConnectionStatus.TIMED_OUT))
    }

    @Test
    fun `exam identity code is set once and can be found`() = runBlocking {
        val participant = participantRepository.create(sessionId, "student1@alunos.isel.pt")

        assertTrue(participantRepository.updateExamIdentityCode(participant.id, "ABCD2345"))
        assertFalse(participantRepository.updateExamIdentityCode(participant.id, "WXYZ6789"))
        assertEquals(participant.id, participantRepository.findByExamIdentityCode("ABCD2345")?.id)
    }

    @Test
    fun `updateTimedOut only marks connected participants in active sessions`() = runBlocking {
        val activeParticipant = participantRepository.create(sessionId, "student1@alunos.isel.pt")
        participantRepository.updateHeartbeat(activeParticipant.id)

        val userRepo = UserRepository()
        val examRepo = ExamRepository()
        val professorId = userRepo.create("prof2@isel.pt", UserRole.PROFESSOR, "hash").id
        val examId = examRepo.create(professorId, "Algebra", null, 60).id
        val pendingSessionId = sessionRepository.create(examId, professorId, "PEN001", "alunos.isel.pt")!!.id
        val pendingParticipant = participantRepository.create(pendingSessionId, "student2@alunos.isel.pt")
        participantRepository.updateHeartbeat(pendingParticipant.id)

        val timedOut = participantRepository.updateTimedOut(Instant.now().plusSeconds(3600))

        assertEquals(listOf(activeParticipant.id), timedOut.map { it.id })
    }
}
