package com.oeims.repositories.interfaces

import com.oeims.models.UserRole
import com.oeims.repositories.UserRecord
import java.util.UUID

interface IUserRepository {
    fun findById(id: UUID): UserRecord?
    fun findByEmail(email: String): UserRecord?
    fun existsByEmail(email: String): Boolean
    fun create(email: String, role: UserRole, passwordHash: String): UserRecord
}
