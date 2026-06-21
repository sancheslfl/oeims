package com.oeims.plugins

import com.oeims.http.AUTH_COOKIE_NAME
import com.oeims.models.dto.ErrorResponse
import com.oeims.services.JwtSettings
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*

fun Application.configureSecurity(jwtSettings: JwtSettings) {
    val verifier = jwtSettings.verifier

    install(Authentication) {
        jwtWithCookie("auth-jwt", jwtSettings) {
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

        jwtWithCookie("auth-professor", jwtSettings) {
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

        jwtWithCookie("auth-student", jwtSettings) {
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
    jwtSettings: JwtSettings,
    configure: JWTAuthenticationProvider.Config.() -> Unit
) {
    jwt(name) {
        realm = jwtSettings.realm

        authHeader { call ->
            call.request.parseAuthorizationHeader()
                ?: call.request.cookies[AUTH_COOKIE_NAME]?.let { token ->
                    HttpAuthHeader.Single("Bearer", token)
                }
        }

        configure()
    }
}