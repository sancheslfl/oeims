package com.oeims.models.dto

import com.oeims.models.Severity
import kotlinx.serialization.Serializable

// Received from the daemon over WebSocket.
// Mirrors Daemon.Abstractions.MonitorEvent (PascalCase severity mapped below).
@Serializable
data class SentinelEventMessage(
    val monitorName: String,
    val message: String,
    val severity: String,  // "Info" | "Warning" | "Critical" — daemon casing
    val occurredAt: String? = null // present for buffered events, absent for live ones
)

// Stored in DB and broadcast to the professor console.
@Serializable
data class EventResponse(
    val id: String,
    val participantId: String,
    val monitorName: String,
    val message: String,
    val severity: String,
    val occurredAt: String
)

// Sent over the professor WebSocket to update a participant's connection status.
@Serializable
data class ParticipantStatusUpdate(
    val participantId: String,
    val connectionStatus: String  // "CONNECTED" | "DISCONNECTED" | "TIMED_OUT"
)

// Maps the daemon's PascalCase severity to the server's uppercase enum.
// Returns null for unrecognised values so the call site can log and drop the frame.
fun String.toDomainSeverity(): Severity? = when (this) {
    "Info" -> Severity.INFO
    "Warning" -> Severity.WARNING
    "Critical" -> Severity.CRITICAL
    else -> null
}
