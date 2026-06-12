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


/**
 * public static getToken(String token) { this.token = token }
 * public static setToken()
 *
 *
 * AuthResponse.getToken()
 * AuthResponse.token
 *
 * AuthResponse(43627643267, 1, miguel@gmail.com, STUDENT).copy(email = tomas@gmail.com)
 */