package com.oeims.models.dto

import com.oeims.exceptions.ValidationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateSessionRequest(
    val examId: String,

    @SerialName("allowedEmailDomain")
    private val nullableAllowedEmailDomain: String? = null,
) {
    val allowedEmailDomain: String
        get() = nullableAllowedEmailDomain
            ?: throw ValidationException("Set an allowed email domain in Settings before creating a session.")
}

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

@Serializable
data class EmailJoinRequest(
    val email: String,
)

@Serializable
data class EmailJoinResponse(
    val message: String,
)

@Serializable
data class VerifyJoinRequest(
    val token: String,
)

@Serializable
data class VerifyJoinResponse(
    val participantId: String,
    val sessionId: String,
    val email: String,
    val status: String,
)