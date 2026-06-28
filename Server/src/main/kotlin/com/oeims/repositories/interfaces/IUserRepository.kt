package com.oeims.repositories.interfaces

import com.oeims.models.UserRole
import com.oeims.repositories.UserRecord
import java.util.*

interface IUserRepository {
    suspend fun findById(id: UUID): UserRecord?
    suspend fun findByEmail(email: String): UserRecord?
    suspend fun existsByEmail(email: String): Boolean
    suspend fun create(email: String, role: UserRole, passwordHash: String): UserRecord
}
