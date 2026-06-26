package com.oeims.services

import com.auth0.jwt.JWT
import com.oeims.models.ConflictException
import com.oeims.models.UnauthorizedException
import com.oeims.models.ValidationException
import com.oeims.models.Email
import com.oeims.models.Password
import com.oeims.models.UserRole
import com.oeims.models.dto.AuthResponse
import com.oeims.models.ids.UserId
import com.oeims.models.ids.toUserId
import com.oeims.repositories.interfaces.IUserRepository
import org.mindrot.jbcrypt.BCrypt
import java.time.Instant

class AuthService(
    private val userRepository: IUserRepository,
    private val jwtSettings: JwtSettings
) {

    suspend fun register(email: Email, password: Password, role: String): AuthResponse {
        if (userRepository.existsByEmail(email.address))
            throw ConflictException("Email already registered")

        val userRole = runCatching { UserRole.valueOf(role.uppercase()) }
            .getOrElse { throw ValidationException("Invalid role: $role") }

        val hash = BCrypt.hashpw(password.value, BCrypt.gensalt())
        val user = userRepository.create(email.address, userRole, hash)

        return AuthResponse(
            token = createAuthToken(user.id.toUserId(), user.role.name, user.email),
            userId = user.id.toString(),
            email = user.email,
            role = user.role.name
        )
    }

    suspend fun login(email: Email, password: Password): AuthResponse {
        val user = userRepository.findByEmail(email.address)
            ?: throw UnauthorizedException("Invalid credentials")

        if (!BCrypt.checkpw(password.value, user.passwordHash))
            throw UnauthorizedException("Invalid credentials")

        return AuthResponse(
            token = createAuthToken(user.id.toUserId(), user.role.name, user.email),
            userId = user.id.toString(),
            email = user.email,
            role = user.role.name
        )
    }

    private fun createAuthToken(userId: UserId, role: String, email: String): String =
        JWT.create()
            .withIssuer(jwtSettings.issuer)
            .withAudience(jwtSettings.audience)
            .withClaim("userId", userId.value.toString())
            .withClaim("role", role)
            .withClaim("email", email)
            .withExpiresAt(Instant.now() + jwtSettings.expiration)
            .sign(jwtSettings.algorithm)
}
