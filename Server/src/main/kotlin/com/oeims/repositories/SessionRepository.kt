package com.oeims.repositories

import com.oeims.models.SessionStatus
import com.oeims.models.SessionSupervisors
import com.oeims.models.Sessions
import com.oeims.repositories.interfaces.ISessionRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

data class SessionRecord(
    val id: UUID,
    val examId: UUID,
    val supervisorId: UUID,
    val code: String,
    val status: SessionStatus,
    val createdAt: Instant,
    val startedAt: Instant?,
    val endedAt: Instant?
)

class SessionRepository : ISessionRepository {

    override suspend fun findById(id: UUID): SessionRecord? = newSuspendedTransaction(Dispatchers.IO) {
        Sessions.selectAll()
            .where { Sessions.id eq id }
            .singleOrNull()
            ?.toRecord()
    }

    override suspend fun findByCode(code: String): SessionRecord? = newSuspendedTransaction(Dispatchers.IO) {
        Sessions.selectAll()
            .where { Sessions.code eq code }
            .singleOrNull()
            ?.toRecord()
    }

    override suspend fun findBySupervisor(supervisorId: UUID): List<SessionRecord> =
        newSuspendedTransaction(Dispatchers.IO) {
            Sessions.selectAll()
                .where { Sessions.supervisorId eq supervisorId }
                .map { it.toRecord() }
        }

    override suspend fun findLatestOpenBySupervisor(supervisorId: UUID): SessionRecord? =
        newSuspendedTransaction(Dispatchers.IO) {
            val openStatuses = listOf(SessionStatus.PENDING, SessionStatus.ACTIVE)

            val accessibleIds = SessionSupervisors
                .selectAll()
                .where { SessionSupervisors.userId eq supervisorId }
                .map { it[SessionSupervisors.sessionId].value }
                .toSet()

            Sessions.selectAll()
                .where {
                    (Sessions.status inList openStatuses) and
                    ((Sessions.supervisorId eq supervisorId) or (Sessions.id inList accessibleIds))
                }
                .orderBy(Sessions.createdAt to SortOrder.DESC)
                .limit(1)
                .singleOrNull()
                ?.toRecord()
        }

    override suspend fun create(examId: UUID, supervisorId: UUID, code: String): SessionRecord =
        newSuspendedTransaction(Dispatchers.IO) {
            val id = UUID.randomUUID()
            val now = Instant.now()

            Sessions.insert {
                it[Sessions.id] = id
                it[Sessions.examId] = examId
                it[Sessions.supervisorId] = supervisorId
                it[Sessions.code] = code
                it[Sessions.status] = SessionStatus.PENDING
                it[Sessions.createdAt] = now
                it[Sessions.startedAt] = null
                it[Sessions.endedAt] = null
            }

            SessionRecord(
                id = id,
                examId = examId,
                supervisorId = supervisorId,
                code = code,
                status = SessionStatus.PENDING,
                createdAt = now,
                startedAt = null,
                endedAt = null
            )
        }

    override suspend fun updateStatus(id: UUID, status: SessionStatus): Boolean =
        newSuspendedTransaction(Dispatchers.IO) {
            val now = Instant.now()
            Sessions.update({ Sessions.id eq id }) {
                it[Sessions.status] = status
                when (status) {
                    SessionStatus.ACTIVE -> it[Sessions.startedAt] = now
                    SessionStatus.ENDED -> it[Sessions.endedAt] = now
                    else -> Unit
                }
            } > 0
        }

    override suspend fun addSupervisor(sessionId: UUID, userId: UUID): Unit =
        newSuspendedTransaction(Dispatchers.IO) {
            SessionSupervisors.insertIgnore {
                it[SessionSupervisors.sessionId] = sessionId
                it[SessionSupervisors.userId]    = userId
            }
        }

    override suspend fun isSupervisor(sessionId: UUID, userId: UUID): Boolean =
        newSuspendedTransaction(Dispatchers.IO) {
            val isCreator = Sessions.selectAll()
                .where { (Sessions.id eq sessionId) and (Sessions.supervisorId eq userId) }
                .count() > 0

            val hasAccess = SessionSupervisors.selectAll()
                .where {
                    (SessionSupervisors.sessionId eq sessionId) and
                    (SessionSupervisors.userId eq userId)
                }
                .count() > 0

            isCreator || hasAccess
        }
}

private fun ResultRow.toRecord() = SessionRecord(
    id = this[Sessions.id].value,
    examId = this[Sessions.examId].value,
    supervisorId = this[Sessions.supervisorId].value,
    code = this[Sessions.code],
    status = this[Sessions.status],
    createdAt = this[Sessions.createdAt],
    startedAt = this[Sessions.startedAt],
    endedAt = this[Sessions.endedAt]
)
