package com.oeims.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.oeims.http.AUTH_COOKIE_NAME
import com.oeims.models.dto.ErrorResponse
import com.oeims.services.JwtConfig
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*

fun Application.configureSecurity(jwtConfig: JwtConfig) {
    val verifier = JWT
        .require(Algorithm.HMAC256(jwtConfig.secret))
        .withIssuer(jwtConfig.issuer)
        .withAudience(jwtConfig.audience)
        .build()

    install(Authentication) {
        jwtWithCookie("auth-jwt", jwtConfig) {
            this.verifier(verifier)

            validate { credential ->
                val userId = credential.payload.getClaim("userId").asString()
                val role = credential.payload.getClaim("role").asString()
                val email = credential.payload.getClaim("email").asString()

                if (userId != null && role != null && email != null) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }

            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("Missing or invalid token")
                )
            }
        }

        jwtWithCookie("auth-professor", jwtConfig) {
            this.verifier(verifier)

            validate { credential ->
                val userId = credential.payload.getClaim("userId").asString()
                val role = credential.payload.getClaim("role").asString()
                val email = credential.payload.getClaim("email").asString()

                if (userId != null && role == "PROFESSOR" && email != null) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }

            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse("Professor access required")
                )
            }
        }

        jwtWithCookie("auth-student", jwtConfig) {
            this.verifier(verifier)

            validate { credential ->
                val userId = credential.payload.getClaim("userId").asString()
                val role = credential.payload.getClaim("role").asString()
                val email = credential.payload.getClaim("email").asString()

                if (userId != null && role == "STUDENT" && email != null) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }

            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse("Student access required")
                )
            }
        }
    }
}

private fun AuthenticationConfig.jwtWithCookie(
    name: String,
    jwtConfig: JwtConfig,
    configure: JWTAuthenticationProvider.Config.() -> Unit
) {
    jwt(name) {
        realm = jwtConfig.realm

        authHeader { call ->
            call.request.parseAuthorizationHeader()
                ?: call.request.cookies[AUTH_COOKIE_NAME]?.let { token ->
                    HttpAuthHeader.Single("Bearer", token)
                }
        }

        configure()
    }
}