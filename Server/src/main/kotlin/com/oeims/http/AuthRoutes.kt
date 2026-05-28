package com.oeims.http

import com.oeims.models.dto.LoginRequest
import com.oeims.models.dto.RegisterRequest
import com.oeims.models.toEmail
import com.oeims.models.toPassword
import com.oeims.services.AuthService
import io.ktor.http.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.authRoutes(authService: AuthService) {
    route("/auth") {
        rateLimit(RateLimitName("auth")) {

            post("/register") {
                val req = call.receive<RegisterRequest>()
                val response = authService.register(req.email.toEmail(), req.password.toPassword(), req.role)
                call.respond(HttpStatusCode.Created, response)
            }

            post("/login") {
                val req = call.receive<LoginRequest>()
                val response = authService.login(req.email.toEmail(), req.password.toPassword())

                call.setAuthCookie(
                    token = response.token,
                    secure = call.request.origin.scheme == "https"
                )

                call.respond(HttpStatusCode.OK, response)
            }

            post("/logout") {
                call.clearAuthCookie(secure = false)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
