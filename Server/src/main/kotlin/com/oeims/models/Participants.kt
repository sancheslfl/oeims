package com.oeims.models

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.security.SecureRandom

enum class ConnectionStatus { CONNECTED, DISCONNECTED, TIMED_OUT }

object Participants : UUIDTable("participants") {
    val sessionId = reference("session_id", Sessions)
    val email = varchar("email", 254)
    val examIdentityCode = varchar("exam_identity_code", 10).uniqueIndex().nullable()
    val connectionStatus = enumerationByName("connection_status", 16, ConnectionStatus::class)
    val lastHeartbeat = timestamp("last_heartbeat").nullable()
    val joinedAt = timestamp("joined_at")

    init {
        uniqueIndex("participants_session_email_index", sessionId, email)
        index(
            "participants_session_connection_status_index",
            false,
            sessionId, connectionStatus)
    }
}

@Serializable
@JvmInline
value class ExamIdentityCode(val value: String) {
    init {
        validate(value.length == LENGTH) { "Exam identity code must have $LENGTH characters" }
        validate(value.all { it in ALPHABET }) { "Exam identity code contains invalid characters" }
    }

    companion object {
        private const val LENGTH = 8
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        private val random = SecureRandom()

        fun generate(): ExamIdentityCode =
            ExamIdentityCode(
                buildString(capacity = LENGTH) {
                    repeat(LENGTH) {
                        append(ALPHABET[random.nextInt(ALPHABET.length)])
                    }
                }
            )
    }
}

fun String.toExamIdentityCode(): ExamIdentityCode = ExamIdentityCode(trim().uppercase())
