package com.oeims.repositories.interfaces

import com.oeims.models.SessionJoinRecord
import com.oeims.models.SessionRecord
import com.oeims.models.SessionStatus
import java.time.Instant
import java.util.*

interface ISessionRepository {
    suspend fun findById(id: UUID): SessionRecord?
    suspend fun findByCode(code: String): SessionRecord?
    suspend fun findBySupervisor(supervisorId: UUID): List<SessionRecord>
    suspend fun findLatestOpenBySupervisor(supervisorId: UUID): SessionRecord?

    /**
     * Creates a session using the provided code.
     *
     * Returns null when the database rejects the insert because the generated code
     * already exists. The caller is expected to retry with a new code.
     */
    suspend fun create(
        examId: UUID,
        supervisorId: UUID,
        code: String,
        allowedEmailDomain: String,
    ): SessionRecord?

    suspend fun updateStatus(id: UUID, status: SessionStatus): Boolean
    suspend fun addSupervisor(sessionId: UUID, userId: UUID)
    suspend fun isSupervisor(sessionId: UUID, userId: UUID): Boolean
    suspend fun findAllActive(): List<SessionRecord>

    suspend fun updateJoinVerification(id: UUID, verifiedAt: Instant): Boolean
    suspend fun findJoinRequestByJwtId(jwtId: String): SessionJoinRecord?
    suspend fun createJoinRequest(sessionId: UUID, email: String, jwtId: String, expiresAt: Instant): SessionJoinRecord
}
