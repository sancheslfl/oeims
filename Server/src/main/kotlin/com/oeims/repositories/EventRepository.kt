package com.oeims.repositories

import com.oeims.models.Events
import com.oeims.models.Participants
import com.oeims.models.Severity
import com.oeims.repositories.interfaces.IEventRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.util.*

data class EventRecord(
    val id: UUID,
    val participantId: UUID,
    val monitorName: String,
    val message: String,
    val severity: Severity,
    val occurredAt: Instant
)

class EventRepository : IEventRepository {

    override suspend fun create(
        participantId: UUID,
        monitorName: String,
        message: String,
        severity: Severity
    ): EventRecord = newSuspendedTransaction(Dispatchers.IO) {
        val id = UUID.randomUUID()
        val now = Instant.now()
        Events.insert {
            it[Events.id] = id
            it[Events.participantId] = participantId
            it[Events.monitorName] = monitorName
            it[Events.message] = message
            it[Events.severity] = severity
            it[Events.occurredAt] = now
        }
        EventRecord(id, participantId, monitorName, message, severity, now)
    }

    override suspend fun findByParticipant(participantId: UUID): List<EventRecord> =
        newSuspendedTransaction(Dispatchers.IO) {
            Events.selectAll()
                .where { Events.participantId eq participantId }
                .orderBy(Events.occurredAt, SortOrder.ASC)
                .map { it.toRecord() }
        }

    // Fetch the full event timeline for a session (all participants).
    override suspend fun findBySession(sessionId: UUID): List<EventRecord> = newSuspendedTransaction(Dispatchers.IO) {
        Events.join(Participants, JoinType.INNER, Events.participantId, Participants.id)
            .selectAll()
            .where { Participants.sessionId eq sessionId }
            .orderBy(Events.occurredAt, SortOrder.ASC)
            .map { it.toRecord() }
    }

    private fun ResultRow.toRecord() = EventRecord(
        id = this[Events.id].value,
        participantId = this[Events.participantId].value,
        monitorName = this[Events.monitorName],
        message = this[Events.message],
        severity = this[Events.severity],
        occurredAt = this[Events.occurredAt]
    )
}
