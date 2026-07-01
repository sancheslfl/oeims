package com.oeims.models.dto

import com.oeims.models.Severity
import kotlinx.serialization.Serializable


@Serializable
data class SentinelEventMessage(
    val monitorName: String,
    val message: String,
    val severity: String   // "Info" | "Warning" | "Critical"
)

@Serializable
data class EventResponse(
    val id: String,
    val participantId: String,
    val monitorName: String,
    val message: String,
    val severity: String,
    val occurredAt: String
)

@Serializable
data class ParticipantStatusUpdate(
    val participantId: String,
    val connectionStatus: String  // "CONNECTED" | "DISCONNECTED" | "TIMED_OUT"
)

fun String.toDomainSeverity(): Severity? = when (this) {
    "Info" -> Severity.INFO
    "Warning" -> Severity.WARNING
    "Critical" -> Severity.CRITICAL
    else -> null
}
