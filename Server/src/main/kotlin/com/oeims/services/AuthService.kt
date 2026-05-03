package com.oeims.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.oeims.dto.AuthResponse
import com.oeims.exceptions.ConflictException
import com.oeims.exceptions.UnauthorizedException
import com.oeims.exceptions.ValidationException
import com.oeims.models.UserRole
import com.oeims.repositories.interfaces.IUserRepository
import org.mindrot.jbcrypt.BCrypt
import java.util.Date

class AuthService(
    private val userRepository: IUserRepository,
    private val jwtConfig: JwtConfig
) {

    suspend fun register(email: String, password: String, role: String): AuthResponse {
        if (userRepository.existsByEmail(email))
            throw ConflictException("Email already registered")

        val userRole = runCatching { UserRole.valueOf(role.uppercase()) }
            .getOrElse { throw ValidationException("Invalid role: $role") }

        val hash = BCrypt.hashpw(password, BCrypt.gensalt())
        val user = userRepository.create(email, userRole, hash)

        return AuthResponse(
            token  = issueToken(user.id.toString(), user.role.name),
            userId = user.id.toString(),
            email  = user.email,
            role   = user.role.name
        )
    }

    suspend fun login(email: String, password: String): AuthResponse {
        val user = userRepository.findByEmail(email)
            ?: throw UnauthorizedException("Invalid credentials")

        if (!BCrypt.checkpw(password, user.passwordHash))
            throw UnauthorizedException("Invalid credentials")

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
