package com.oeims.models.dto

import kotlinx.serialization.Serializable

@Serializable
data class ParticipantResponse(
    val id: String,
    val sessionId: String,
    val userId: String,
    val email: String,
    val connectionStatus: String,
    val lastHeartbeat: String?,
    val joinedAt: String
)
