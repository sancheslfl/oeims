package com.oeims.repositories

import com.oeims.models.SessionJoins
import com.oeims.models.SessionStatus
import com.oeims.models.SessionSupervisors
import com.oeims.models.Sessions
import com.oeims.repositories.interfaces.ISessionRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.util.*

data class SessionRecord(
    val id: UUID,
    val examId: UUID,
    val supervisorId: UUID,
    val code: String,
    val allowedEmailDomain: String,
    val status: SessionStatus,
    val createdAt: Instant,
    val startedAt: Instant?,
    val endedAt: Instant?
)

data class EmailJoinRecord(
    val id: UUID,
    val sessionId: UUID,
    val email: String,
    val jwtId: String,
    val expiresAt: Instant,
    val verifiedAt: Instant?,
    val createdAt: Instant,
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

    override suspend fun create(
        examId: UUID,
        supervisorId: UUID,
        code: String,
        allowedEmailDomain: String,
    ): SessionRecord? =
        newSuspendedTransaction(Dispatchers.IO) {
            val id = UUID.randomUUID()
            val now = Instant.now()

            val insert = Sessions.insertIgnore {
                it[Sessions.id] = id
                it[Sessions.examId] = examId
                it[Sessions.supervisorId] = supervisorId
                it[Sessions.code] = code
                it[Sessions.allowedEmailDomain] = allowedEmailDomain
                it[Sessions.status] = SessionStatus.PENDING
                it[Sessions.createdAt] = now
                it[Sessions.startedAt] = null
                it[Sessions.endedAt] = null
            }

            if (insert.insertedCount == 0)
                return@newSuspendedTransaction null

            SessionRecord(
                id = id,
                examId = examId,
                supervisorId = supervisorId,
                code = code,
                allowedEmailDomain = allowedEmailDomain,
                status = SessionStatus.PENDING,
                createdAt = now,
                startedAt = null,
                endedAt = null,
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
                it[SessionSupervisors.userId] = userId
            }
        }

    override suspend fun findAllActive(): List<SessionRecord> =
        newSuspendedTransaction(Dispatchers.IO) {
            val openStatuses = listOf(SessionStatus.PENDING, SessionStatus.ACTIVE)
            Sessions.selectAll()
                .where { Sessions.status inList openStatuses }
                .map { it.toRecord() }
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

    override suspend fun createEmailJoin(
        sessionId: UUID,
        email: String,
        jwtId: String,
        expiresAt: Instant,
    ): EmailJoinRecord =
        newSuspendedTransaction(Dispatchers.IO) {
            val id = UUID.randomUUID()
            val now = Instant.now()

            SessionJoins.insert {
                it[SessionJoins.id] = id
                it[SessionJoins.sessionId] = sessionId
                it[SessionJoins.email] = email
                it[SessionJoins.jwtId] = jwtId
                it[SessionJoins.expiresAt] = expiresAt
                it[SessionJoins.verifiedAt] = null
                it[SessionJoins.createdAt] = now
            }

            EmailJoinRecord(
                id = id,
                sessionId = sessionId,
                email = email,
                jwtId = jwtId,
                expiresAt = expiresAt,
                verifiedAt = null,
                createdAt = now,
            )
        }

    override suspend fun findEmailJoinByJwtId(jwtId: String): EmailJoinRecord? =
        newSuspendedTransaction(Dispatchers.IO) {
            SessionJoins.selectAll()
                .where { SessionJoins.jwtId eq jwtId }
                .singleOrNull()
                ?.toEmailJoinRecord()
        }

    override suspend fun updateEmailJoinVerification(id: UUID, verifiedAt: Instant): Boolean =
        newSuspendedTransaction(Dispatchers.IO) {
            SessionJoins.update(
                where = {
                    (SessionJoins.id eq id) and SessionJoins.verifiedAt.isNull()
                }
            ) {
                it[SessionJoins.verifiedAt] = verifiedAt
            } == 1
        }
}

private fun ResultRow.toRecord() = SessionRecord(
    id = this[Sessions.id].value,
    examId = this[Sessions.examId].value,
    supervisorId = this[Sessions.supervisorId].value,
    code = this[Sessions.code],
    allowedEmailDomain = this[Sessions.allowedEmailDomain],
    status = this[Sessions.status],
    createdAt = this[Sessions.createdAt],
    startedAt = this[Sessions.startedAt],
    endedAt = this[Sessions.endedAt]
)

private fun ResultRow.toEmailJoinRecord() = EmailJoinRecord(
    id = this[SessionJoins.id].value,
    sessionId = this[SessionJoins.sessionId].value,
    email = this[SessionJoins.email],
    jwtId = this[SessionJoins.jwtId],
    expiresAt = this[SessionJoins.expiresAt],
    verifiedAt = this[SessionJoins.verifiedAt],
    createdAt = this[SessionJoins.createdAt],
)