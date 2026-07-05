package com.oeims.models.dto

import kotlinx.serialization.Serializable


@Serializable
data class SentinelEventMessage(
    val monitorName: String,
    val message: String,
    val severity: String,   // "Info" | "Warning" | "Critical"
    val occurredAt: String
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
