package com.oeims.repositories.interfaces

import com.oeims.models.Severity
import com.oeims.repositories.EventRecord
import java.util.UUID

interface IEventRepository {
    fun create(participantId: UUID, monitorName: String, message: String, severity: Severity): EventRecord
    fun findByParticipant(participantId: UUID): List<EventRecord>
    fun findBySession(sessionId: UUID): List<EventRecord>
}
