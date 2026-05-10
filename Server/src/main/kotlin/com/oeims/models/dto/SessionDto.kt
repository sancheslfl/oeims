package com.oeims.models.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateSessionRequest(
    val examId: String
)

@Serializable
data class SessionResponse(
    val id: String,
    val examId: String,
    val supervisorId: String,
    val code: String,
    val status: String,
    val startedAt: String?,
    val endedAt: String?
)

@Serializable
data class JoinSessionRequest(
    val code: String
)

@Serializable
data class JoinSessionResponse(
    val participantId: String,
    val sessionId: String,
    val examTitle: String,
    val durationMins: Int
)
