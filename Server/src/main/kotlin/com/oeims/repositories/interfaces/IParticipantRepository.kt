package com.oeims.repositories.interfaces

import com.oeims.models.ConnectionStatus
import com.oeims.repositories.ParticipantRecord
import java.time.Instant
import java.util.UUID

interface IParticipantRepository {
    fun findById(id: UUID): ParticipantRecord?
    fun findBySession(sessionId: UUID): List<ParticipantRecord>
    fun findByUserAndSession(userId: UUID, sessionId: UUID): ParticipantRecord?
    fun create(sessionId: UUID, userId: UUID): ParticipantRecord
    fun updateHeartbeat(id: UUID): Boolean
    fun updateConnectionStatus(id: UUID, status: ConnectionStatus): Boolean
    fun markTimedOut(threshold: Instant): Int
}
