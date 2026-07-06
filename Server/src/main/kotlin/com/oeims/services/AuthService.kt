package com.oeims.services

import com.auth0.jwt.JWT
import com.oeims.models.*
import com.oeims.models.dto.AuthResponse
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

        val userRole = UserRole.from(role)

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

    // TODO: Move this generate token functions elsewhere (associate with JwtSettings maybe)
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
