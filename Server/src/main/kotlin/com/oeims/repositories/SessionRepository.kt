package com.oeims.repositories

import com.oeims.models.SessionStatus
import com.oeims.models.Sessions
import com.oeims.repositories.interfaces.ISessionRepository
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID

data class SessionRecord(
    val id: UUID,
    val examId: UUID,
    val supervisorId: UUID,
    val code: String,
    val status: SessionStatus,
    val startedAt: Instant?,
    val endedAt: Instant?
)

class SessionRepository : ISessionRepository {

    override fun findById(id: UUID): SessionRecord? = transaction {
        Sessions.selectAll()
            .where { Sessions.id eq id }
            .singleOrNull()
            ?.toRecord()
    }

    override fun findByCode(code: String): SessionRecord? = transaction {
        Sessions.selectAll()
            .where { Sessions.code eq code }
            .singleOrNull()
            ?.toRecord()
    }

    override fun findBySupervisor(supervisorId: UUID): List<SessionRecord> = transaction {
        Sessions.selectAll()
            .where { Sessions.supervisorId eq supervisorId }
            .map { it.toRecord() }
    }

    override fun create(examId: UUID, supervisorId: UUID, code: String): SessionRecord = transaction {
        val id = UUID.randomUUID()
        Sessions.insert {
            it[Sessions.id] = id
            it[Sessions.examId] = examId
            it[Sessions.supervisorId] = supervisorId
            it[Sessions.code] = code
            it[Sessions.status] = SessionStatus.PENDING
            it[Sessions.startedAt] = null
            it[Sessions.endedAt] = null
        }
        SessionRecord(id, examId, supervisorId, code, SessionStatus.PENDING, null, null)
    }

    override fun updateStatus(id: UUID, status: SessionStatus): Boolean = transaction {
        val now = Instant.now()
        Sessions.update({ Sessions.id eq id }) {
            it[Sessions.status] = status
            when (status) {
                SessionStatus.ACTIVE -> it[Sessions.startedAt] = now
                SessionStatus.ENDED  -> it[Sessions.endedAt] = now
                else                 -> Unit
            }
        } > 0
    }

    private fun ResultRow.toRecord() = SessionRecord(
        id           = this[Sessions.id].value,
        examId       = this[Sessions.examId].value,
        supervisorId = this[Sessions.supervisorId].value,
        code         = this[Sessions.code],
        status       = this[Sessions.status],
        startedAt    = this[Sessions.startedAt],
        endedAt      = this[Sessions.endedAt]
    )
}
