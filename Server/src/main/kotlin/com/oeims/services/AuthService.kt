package com.oeims.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.oeims.dto.AuthResponse
import com.oeims.models.UserRole
import com.oeims.repositories.interfaces.IUserRepository
import org.mindrot.jbcrypt.BCrypt
import java.util.Date

class AuthService(
    private val userRepository: IUserRepository,
    private val jwtConfig: JwtConfig
) {

    fun register(email: String, password: String, role: String): AuthResponse {
        if (userRepository.existsByEmail(email))
            throw IllegalStateException("Email already registered")

        val userRole = runCatching { UserRole.valueOf(role.uppercase()) }
            .getOrElse { throw IllegalArgumentException("Invalid role: $role") }

        val hash = BCrypt.hashpw(password, BCrypt.gensalt())
        val user = userRepository.create(email, userRole, hash)

        return AuthResponse(
            token  = issueToken(user.id.toString(), user.role.name),
            userId = user.id.toString(),
            email  = user.email,
            role   = user.role.name
        )
    }

    fun login(email: String, password: String): AuthResponse {
        val user = userRepository.findByEmail(email)
            ?: throw IllegalArgumentException("Invalid credentials")

        if (!BCrypt.checkpw(password, user.passwordHash))
            throw IllegalArgumentException("Invalid credentials")

        return AuthResponse(
            token  = issueToken(user.id.toString(), user.role.name),
            userId = user.id.toString(),
            email  = user.email,
            role   = user.role.name
        )
    }

    private fun issueToken(userId: String, role: String): String =
        JWT.create()
            .withIssuer(jwtConfig.issuer)
            .withAudience(jwtConfig.audience)
            .withClaim("userId", userId)
            .withClaim("role", role)
            .withExpiresAt(Date(System.currentTimeMillis() + jwtConfig.expirationMs))
            .sign(Algorithm.HMAC256(jwtConfig.secret))
}
