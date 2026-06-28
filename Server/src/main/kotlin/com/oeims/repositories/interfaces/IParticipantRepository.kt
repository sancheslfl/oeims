package com.oeims.repositories.interfaces

import com.oeims.models.ConnectionStatus
import com.oeims.repositories.ParticipantRecord
import java.time.Instant
import java.util.*

interface IParticipantRepository {
    suspend fun findById(id: UUID): ParticipantRecord?
    suspend fun findBySession(sessionId: UUID): List<ParticipantRecord>
    suspend fun findByExamIdentityCode(examIdentityCode: String): ParticipantRecord?
    suspend fun findByEmailAndSession(email: String, sessionId: UUID): ParticipantRecord?
    suspend fun findConnectedBySession(sessionId: UUID): List<ParticipantRecord>
    suspend fun create(sessionId: UUID, email: String): ParticipantRecord
    suspend fun updateHeartbeat(id: UUID): Boolean
    suspend fun updateConnectionStatus(id: UUID, status: ConnectionStatus): Boolean
    suspend fun updateTimedOut(threshold: Instant): List<ParticipantRecord>
    suspend fun updateExamIdentityCode(participantId: UUID, examIdentityCode: String): Boolean
}
