package com.oeims.models.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val role: String   // "STUDENT" | "PROFESSOR"
)

@Serializable
data class AuthResponse(
    val token: String,
    val userId: String,
    val email: String,
    val role: String
)
