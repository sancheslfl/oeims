package com.oeims.repositories.interfaces

import com.oeims.models.SessionStatus
import com.oeims.repositories.SessionRecord
import java.util.UUID

interface ISessionRepository {
    fun findById(id: UUID): SessionRecord?
    fun findByCode(code: String): SessionRecord?
    fun findBySupervisor(supervisorId: UUID): List<SessionRecord>
    fun create(examId: UUID, supervisorId: UUID, code: String): SessionRecord
    fun updateStatus(id: UUID, status: SessionStatus): Boolean
}
