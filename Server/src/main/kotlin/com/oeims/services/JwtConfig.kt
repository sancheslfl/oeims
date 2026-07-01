package com.oeims.services

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import java.time.Duration

data class JwtSettings(
    val issuer: String,
    val audience: String,
    val realm: String,
    val expiration: Duration,
    val algorithm: Algorithm,
    val purpose: String? = null,
) {
    val verifier: JWTVerifier
        get() = JWT
            .require(algorithm)
            .withIssuer(issuer)
            .withAudience(audience)
            .let { verification ->
                if (purpose == null) verification
                else verification.withClaim("purpose", purpose)
            }
            .build()
}

data class SessionJwtSettings(
    val emailVerification: JwtSettings,
    val sentinel: JwtSettings,
)

fun Application.configureAuthJwt() = JwtSettings(
    issuer = environment.config.property("jwt.issuer").getString(),
    audience = environment.config.property("jwt.auth.audience").getString(),
    realm = environment.config.property("jwt.auth.realm").getString(),
    expiration = Duration.ofMillis(
        environment.config.property("jwt.auth.expiration-ms").getString().toLong()
    ),
    algorithm = Algorithm.HMAC256(jwtSecret()),
)

fun Application.configureEmailVerificationJwt() = JwtSettings(
    issuer = environment.config.property("jwt.issuer").getString(),
    audience = environment.config.property("jwt.email-verification.audience").getString(),
    realm = environment.config.property("jwt.email-verification.realm").getString(),
    expiration = Duration.ofMillis(
        environment.config.property("jwt.email-verification.expiration-ms").getString().toLong()
    ),
    algorithm = Algorithm.HMAC256(jwtSecret()),
    purpose = "email_join",
)

private fun Application.jwtSecret(): String =
    System.getenv("JWT_SECRET")
        ?: environment.config.property("jwt.secret").getString()
