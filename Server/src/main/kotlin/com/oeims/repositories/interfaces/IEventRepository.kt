package com.oeims.repositories.interfaces

import com.oeims.models.Severity
import com.oeims.repositories.EventRecord
import java.time.Instant
import java.util.UUID

interface IEventRepository {
    suspend fun create(participantId: UUID, monitorName: String, message: String, severity: Severity, occurredAt: Instant? = null): EventRecord
    suspend fun findByParticipant(participantId: UUID): List<EventRecord>
    suspend fun findBySession(sessionId: UUID): List<EventRecord>
}
