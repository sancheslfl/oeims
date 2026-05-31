package com.oeims.repositories

import com.oeims.models.ConnectionStatus
import com.oeims.models.Participants
import com.oeims.models.SessionStatus
import com.oeims.models.Sessions
import com.oeims.models.Users
import com.oeims.repositories.interfaces.IParticipantRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

data class ParticipantRecord(
    val id: UUID,
    val sessionId: UUID,
    val userId: UUID,
    val email: String,
    val connectionStatus: ConnectionStatus,
    val lastHeartbeat: Instant?,
    val joinedAt: Instant
)

class ParticipantRepository : IParticipantRepository {

    override suspend fun findById(id: UUID): ParticipantRecord? = newSuspendedTransaction(Dispatchers.IO) {
        Participants.join(Users, JoinType.INNER, Participants.userId, Users.id)
            .selectAll()
            .where { Participants.id eq id }
            .singleOrNull()
            ?.toRecord()
    }

    override suspend fun findBySession(sessionId: UUID): List<ParticipantRecord> =
        newSuspendedTransaction(Dispatchers.IO) {
            Participants.join(Users, JoinType.INNER, Participants.userId, Users.id)
                .selectAll()
                .where { Participants.sessionId eq sessionId }
                .map { it.toRecord() }
        }

    override suspend fun findByUserAndSession(userId: UUID, sessionId: UUID): ParticipantRecord? =
        newSuspendedTransaction(Dispatchers.IO) {
            Participants.join(Users, JoinType.INNER, Participants.userId, Users.id)
                .selectAll()
                .where { (Participants.userId eq userId) and (Participants.sessionId eq sessionId) }
                .singleOrNull()
                ?.toRecord()
        }

    override suspend fun create(sessionId: UUID, userId: UUID): ParticipantRecord =
        newSuspendedTransaction(Dispatchers.IO) {
            val id = UUID.randomUUID()
            val now = Instant.now()
            Participants.insert {
                it[Participants.id] = id
                it[Participants.sessionId] = sessionId
                it[Participants.userId] = userId
                it[Participants.connectionStatus] = ConnectionStatus.DISCONNECTED
                it[Participants.lastHeartbeat] = null
                it[Participants.joinedAt] = now
            }
            // Re-fetch with email join — runs in the same transaction
            Participants.join(Users, JoinType.INNER, Participants.userId, Users.id)
                .selectAll()
                .where { Participants.id eq id }
                .single()
                .toRecord()
        }

    override suspend fun updateHeartbeat(id: UUID): Boolean = newSuspendedTransaction(Dispatchers.IO) {
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

    override suspend fun markTimedOut(threshold: Instant): List<ParticipantRecord> =
        newSuspendedTransaction(Dispatchers.IO) {
            val candidates = Participants
                .join(Users, JoinType.INNER, Participants.userId, Users.id)
                .join(Sessions, JoinType.INNER, Participants.sessionId, Sessions.id)
                .selectAll()
                .where {
                    (Participants.connectionStatus eq ConnectionStatus.CONNECTED) and
                            (Participants.lastHeartbeat lessEq threshold) and
                            (Sessions.status eq SessionStatus.ACTIVE)
                }
                .map { it.toRecord() }

            if (candidates.isNotEmpty()) {
                val candidateIds = candidates.map { it.id }

                Participants.update({
                    Participants.id inList candidateIds
                }) {
                    it[connectionStatus] = ConnectionStatus.TIMED_OUT
                }
            }

            candidates
        }

    private fun ResultRow.toRecord() = ParticipantRecord(
        id = this[Participants.id].value,
        sessionId = this[Participants.sessionId].value,
        userId = this[Participants.userId].value,
        email = this[Users.email],
        connectionStatus = this[Participants.connectionStatus],
        lastHeartbeat = this[Participants.lastHeartbeat],
        joinedAt = this[Participants.joinedAt]
    )
}
