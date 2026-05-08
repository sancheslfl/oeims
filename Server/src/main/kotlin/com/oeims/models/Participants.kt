package com.oeims.models

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp

enum class ConnectionStatus { CONNECTED, DISCONNECTED, TIMED_OUT }

object Participants : UUIDTable("participants") {
    val sessionId        = reference("session_id", Sessions)
    val userId           = reference("user_id", Users)
    val connectionStatus = enumerationByName("connection_status", 16, ConnectionStatus::class)
    val lastHeartbeat    = timestamp("last_heartbeat").nullable()
    val joinedAt         = timestamp("joined_at")
}
