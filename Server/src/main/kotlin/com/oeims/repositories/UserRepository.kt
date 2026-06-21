package com.oeims.repositories

import com.oeims.models.UserRole
import com.oeims.models.Users
import com.oeims.repositories.interfaces.IUserRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant
import java.util.*

data class UserRecord(
    val id: UUID,
    val email: String,
    val role: UserRole,
    val passwordHash: String,
    val createdAt: Instant
)

class UserRepository : IUserRepository {

    override suspend fun findByEmail(email: String): UserRecord? = newSuspendedTransaction(Dispatchers.IO) {
        Users.selectAll()
            .where { Users.email eq email }
            .singleOrNull()
            ?.toRecord()
    }

    override suspend fun findById(id: UUID): UserRecord? = newSuspendedTransaction(Dispatchers.IO) {
        Users.selectAll()
            .where { Users.id eq id }
            .singleOrNull()
            ?.toRecord()
    }

    override suspend fun create(email: String, role: UserRole, passwordHash: String): UserRecord =
        newSuspendedTransaction(Dispatchers.IO) {
            val id = UUID.randomUUID()
            val now = Instant.now()
            Users.insert {
                it[Users.id] = id
                it[Users.email] = email
                it[Users.role] = role
                it[Users.passwordHash] = passwordHash
                it[Users.createdAt] = now
            }
            UserRecord(id, email, role, passwordHash, now)
        }

    override suspend fun existsByEmail(email: String): Boolean = newSuspendedTransaction(Dispatchers.IO) {
        Users.selectAll()
            .where { Users.email eq email }
            .any()
    }

    private fun ResultRow.toRecord() = UserRecord(
        id = this[Users.id].value,
        email = this[Users.email],
        role = this[Users.role],
        passwordHash = this[Users.passwordHash],
        createdAt = this[Users.createdAt]
    )
}
