package com.oeims.repositories.interfaces

import com.oeims.models.ConnectionStatus
import com.oeims.repositories.ParticipantRecord
import java.time.Instant
import java.util.UUID

interface IParticipantRepository {
    suspend fun findById(id: UUID): ParticipantRecord?
    suspend fun findBySession(sessionId: UUID): List<ParticipantRecord>
    suspend fun findByUserAndSession(userId: UUID, sessionId: UUID): ParticipantRecord?
    suspend fun create(sessionId: UUID, userId: UUID): ParticipantRecord
    suspend fun updateHeartbeat(id: UUID): Boolean
    suspend fun updateConnectionStatus(id: UUID, status: ConnectionStatus): Boolean
    suspend fun markTimedOut(threshold: Instant): List<ParticipantRecord>
}
