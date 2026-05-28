package com.oeims.repositories.interfaces

import com.oeims.models.SessionStatus
import com.oeims.repositories.SessionRecord
import java.util.UUID

interface ISessionRepository {
    suspend fun findById(id: UUID): SessionRecord?
    suspend fun findByCode(code: String): SessionRecord?
    suspend fun findBySupervisor(supervisorId: UUID): List<SessionRecord>
    suspend fun findLatestOpenBySupervisor(supervisorId: UUID): SessionRecord?
    suspend fun create(examId: UUID, supervisorId: UUID, code: String): SessionRecord
    suspend fun updateStatus(id: UUID, status: SessionStatus): Boolean
}
