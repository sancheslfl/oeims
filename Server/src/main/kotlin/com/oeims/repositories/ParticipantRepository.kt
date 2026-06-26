package com.oeims.repositories

import com.oeims.models.*
import com.oeims.repositories.interfaces.IParticipantRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.util.*

data class ParticipantRecord(
    val id: UUID,
    val sessionId: UUID,
    val email: String,
    val examIdentityCode: String?,
    val connectionStatus: ConnectionStatus,
    val lastHeartbeat: Instant?,
    val joinedAt: Instant
)

class ParticipantRepository : IParticipantRepository {

    override suspend fun findById(id: UUID): ParticipantRecord? =
        newSuspendedTransaction(Dispatchers.IO) {
            Participants.selectAll()
                .where { Participants.id eq id }
                .singleOrNull()
                ?.toRecord()
        }

    override suspend fun findBySession(sessionId: UUID): List<ParticipantRecord> =
        newSuspendedTransaction(Dispatchers.IO) {
            Participants.selectAll()
                .where { Participants.sessionId eq sessionId }
                .map { it.toRecord() }
        }

    override suspend fun findByEmailAndSession(email: String, sessionId: UUID): ParticipantRecord? =
        newSuspendedTransaction(Dispatchers.IO) {
            Participants.selectAll()
                .where {
                    (Participants.email eq email) and
                            (Participants.sessionId eq sessionId)
                }
                .singleOrNull()
                ?.toRecord()
        }

    override suspend fun findConnectedBySession(
        sessionId: UUID,
    ): List<ParticipantRecord> =
        newSuspendedTransaction(Dispatchers.IO) {
            Participants
                .selectAll()
                .where {
                    (Participants.sessionId eq sessionId) and
                            (Participants.connectionStatus eq ConnectionStatus.CONNECTED)
                }
                .map { it.toRecord() }
        }

    override suspend fun updateExamIdentityCode(
        participantId: UUID,
        examIdentityCode: String
    ): Boolean =
        newSuspendedTransaction(Dispatchers.IO) {
            try {
                Participants.update({
                    (Participants.id eq participantId) and
                            Participants.examIdentityCode.isNull()
                }) {
                    it[Participants.examIdentityCode] = examIdentityCode
                } == 1
            } catch (_: ExposedSQLException) {
                false
            }
        }

    override suspend fun findByExamIdentityCode(
        examIdentityCode: String
    ): ParticipantRecord? =
        newSuspendedTransaction(Dispatchers.IO) {
            Participants
                .selectAll()
                .where { Participants.examIdentityCode eq examIdentityCode }
                .singleOrNull()
                ?.toRecord()
        }

    override suspend fun create(sessionId: UUID, email: String): ParticipantRecord =
        newSuspendedTransaction(Dispatchers.IO) {
            val id = UUID.randomUUID()
            val now = Instant.now()

            Participants.insert {
                it[Participants.id] = id
                it[Participants.sessionId] = sessionId
                it[Participants.email] = email
                it[Participants.examIdentityCode] = null
                it[Participants.connectionStatus] = ConnectionStatus.DISCONNECTED
                it[Participants.lastHeartbeat] = null
                it[Participants.joinedAt] = now
            }

            ParticipantRecord(
                id = id,
                sessionId = sessionId,
                email = email,
                examIdentityCode = null,
                connectionStatus = ConnectionStatus.DISCONNECTED,
                lastHeartbeat = null,
                joinedAt = now
            )
        }

    override suspend fun updateHeartbeat(id: UUID): Boolean =
        newSuspendedTransaction(Dispatchers.IO) {
            Participants.update({ Participants.id eq id }) {
                it[Participants.lastHeartbeat] = Instant.now()
                it[Participants.connectionStatus] = ConnectionStatus.CONNECTED
            } > 0
        }

    override suspend fun updateConnectionStatus(id: UUID, status: ConnectionStatus): Boolean =
        newSuspendedTransaction(Dispatchers.IO) {
            Participants.update({ Participants.id eq id }) {
                it[Participants.connectionStatus] = status
            } > 0
        }

    override suspend fun updateTimedOut(threshold: Instant): List<ParticipantRecord> =
        newSuspendedTransaction(Dispatchers.IO) {
            val candidates = Participants
                .join(Sessions, JoinType.INNER, Participants.sessionId, Sessions.id)
                .selectAll()
                .where {
                    (Participants.connectionStatus eq ConnectionStatus.CONNECTED) and
                            (Participants.lastHeartbeat lessEq threshold) and
                            (Sessions.status eq SessionStatus.ACTIVE)
                }
                .map { it.toRecord() }

            if (candidates.isNotEmpty()) {
                Participants.update({ Participants.id inList candidates.map { it.id } }) {
                    it[connectionStatus] = ConnectionStatus.TIMED_OUT
                }
            }

            candidates
        }

    private fun ResultRow.toRecord() = ParticipantRecord(
        id = this[Participants.id].value,
        sessionId = this[Participants.sessionId].value,
        email = this[Participants.email],
        examIdentityCode = this[Participants.examIdentityCode],
        connectionStatus = this[Participants.connectionStatus],
        lastHeartbeat = this[Participants.lastHeartbeat],
        joinedAt = this[Participants.joinedAt]
    )
}