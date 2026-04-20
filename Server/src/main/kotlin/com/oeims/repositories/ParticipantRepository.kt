package com.oeims.repositories

import com.oeims.models.ConnectionStatus
import com.oeims.models.Participants
import com.oeims.models.SessionStatus
import com.oeims.models.Sessions
import com.oeims.models.Users
import com.oeims.repositories.interfaces.IParticipantRepository
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
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

    override fun findById(id: UUID): ParticipantRecord? = transaction {
        Participants.join(Users, JoinType.INNER, Participants.userId, Users.id)
            .selectAll()
            .where { Participants.id eq id }
            .singleOrNull()
            ?.toRecord()
    }

    override fun findBySession(sessionId: UUID): List<ParticipantRecord> = transaction {
        Participants.join(Users, JoinType.INNER, Participants.userId, Users.id)
            .selectAll()
            .where { Participants.sessionId eq sessionId }
            .map { it.toRecord() }
    }

    override fun findByUserAndSession(userId: UUID, sessionId: UUID): ParticipantRecord? = transaction {
        Participants.join(Users, JoinType.INNER, Participants.userId, Users.id)
            .selectAll()
            .where { (Participants.userId eq userId) and (Participants.sessionId eq sessionId) }
            .singleOrNull()
            ?.toRecord()
    }

    override fun create(sessionId: UUID, userId: UUID): ParticipantRecord = transaction {
        val id = UUID.randomUUID()
        val now = Instant.now()
        Participants.insert {
            it[Participants.id] = id
            it[Participants.sessionId] = sessionId
            it[Participants.userId] = userId
            it[Participants.connectionStatus] = ConnectionStatus.CONNECTED
            it[Participants.lastHeartbeat] = null
            it[Participants.joinedAt] = now
        }
        // Re-fetch with email join
        findById(id)!!
    }

    override fun updateHeartbeat(id: UUID): Boolean = transaction {
        Participants.update({ Participants.id eq id }) {
            it[Participants.lastHeartbeat] = Instant.now()
            it[Participants.connectionStatus] = ConnectionStatus.CONNECTED
        } > 0
    }

    override fun updateConnectionStatus(id: UUID, status: ConnectionStatus): Boolean = transaction {
        Participants.update({ Participants.id eq id }) {
            it[Participants.connectionStatus] = status
        } > 0
    }

    // Called by HeartbeatService: marks participants as TIMED_OUT if their
    // last heartbeat is older than the given threshold.
    // Only checks participants in ACTIVE sessions — no point checking ended sessions.
    // SELECT candidates first, then bulk UPDATE — both in one transaction.
    override fun markTimedOut(threshold: Instant): List<ParticipantRecord> = transaction {
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
            Participants.update({
                (Participants.connectionStatus eq ConnectionStatus.CONNECTED) and
                (Participants.lastHeartbeat lessEq threshold)
            }) {
                it[Participants.connectionStatus] = ConnectionStatus.TIMED_OUT
            }
        }

        candidates
    }

    private fun ResultRow.toRecord() = ParticipantRecord(
        id               = this[Participants.id].value,
        sessionId        = this[Participants.sessionId].value,
        userId           = this[Participants.userId].value,
        email            = this[Users.email],
        connectionStatus = this[Participants.connectionStatus],
        lastHeartbeat    = this[Participants.lastHeartbeat],
        joinedAt         = this[Participants.joinedAt]
    )
}
