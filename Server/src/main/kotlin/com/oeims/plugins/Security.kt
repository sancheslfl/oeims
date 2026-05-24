package com.oeims.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
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

        // Any authenticated user — professor or student
        jwt("auth-jwt") {
            realm = jwtConfig.realm
            this.verifier(verifier)
            // Browser WebSocket APIs cannot set Authorization header; fall back to ?token= query param.
            authHeader { call ->
                call.request.parseAuthorizationHeader()
                    ?: call.request.queryParameters["token"]?.let { token ->
                        HttpAuthHeader.Single("Bearer", token)
                    }
            }
            validate { credential ->
                val userId = credential.payload.getClaim("userId").asString()
                val role   = credential.payload.getClaim("role").asString()
                if (userId != null && role != null) JWTPrincipal(credential.payload)
                else null
            }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("Missing or invalid token")
                )
            }
        }

        // Professors only
        jwt("auth-professor") {
            realm = jwtConfig.realm
            this.verifier(verifier)
            authHeader { call ->
                call.request.parseAuthorizationHeader()
                    ?: call.request.queryParameters["token"]?.let { token ->
                        HttpAuthHeader.Single("Bearer", token)
                    }
            }
            validate { credential ->
                val userId = credential.payload.getClaim("userId").asString()
                val role   = credential.payload.getClaim("role").asString()
                if (userId != null && role == "PROFESSOR") JWTPrincipal(credential.payload)
                else null
            }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse("Professor access required")
                )
            }
        }

        // Students only
        jwt("auth-student") {
            realm = jwtConfig.realm
            this.verifier(verifier)
            authHeader { call ->
                call.request.parseAuthorizationHeader()
                    ?: call.request.queryParameters["token"]?.let { token ->
                        HttpAuthHeader.Single("Bearer", token)
                    }
            }
            validate { credential ->
                val userId = credential.payload.getClaim("userId").asString()
                val role   = credential.payload.getClaim("role").asString()
                if (userId != null && role == "STUDENT") JWTPrincipal(credential.payload)
                else null
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
