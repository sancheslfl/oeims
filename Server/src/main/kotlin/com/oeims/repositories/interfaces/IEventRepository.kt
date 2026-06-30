package com.oeims.repositories.interfaces

import com.oeims.models.EventRecord
import com.oeims.models.Severity
import java.util.*

interface IEventRepository {
    suspend fun create(participantId: UUID, monitorName: String, message: String, severity: Severity): EventRecord
    suspend fun findByParticipant(participantId: UUID): List<EventRecord>
    suspend fun findByParticipants(participantIds: List<UUID>): List<EventRecord>
    suspend fun findBySession(sessionId: UUID): List<EventRecord>
}
