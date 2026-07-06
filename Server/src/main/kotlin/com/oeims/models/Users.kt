package com.oeims.models

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp

enum class UserRole {
    STUDENT,
    PROFESSOR;

    companion object {
        fun from(value: String): UserRole =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: throw ValidationException("Invalid role: $value")
    }
}

object Users : UUIDTable("users") {
    val email = varchar("email", 254).uniqueIndex()
    val role = enumerationByName("role", 16, UserRole::class)
    val passwordHash = varchar("password_hash", 60)
    val createdAt = timestamp("created_at")
}

@JvmInline
value class Password(val value: String) {

    init {
        validate(value.length > 8) { "Password must be at least 8 characters long." }
    }
}

fun String.toPassword() = Password(this)