package com.oeims.routes

import com.oeims.dto.LoginRequest
import com.oeims.dto.RegisterRequest
import com.oeims.services.AuthService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.authRoutes(authService: AuthService) {
    route("/auth") {

        post("/register") {
            val req = call.receive<RegisterRequest>()
            val response = authService.register(req.email, req.password, req.role)
            call.respond(HttpStatusCode.Created, response)
        }

        post("/login") {
            val req = call.receive<LoginRequest>()
            val response = authService.login(req.email, req.password)
            call.respond(HttpStatusCode.OK, response)
        }
    }
}
