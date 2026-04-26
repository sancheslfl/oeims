package com.oeims.plugins

import com.oeims.dto.ErrorResponse
import com.oeims.routes.authRoutes
import com.oeims.routes.examRoutes
import com.oeims.routes.participantRoutes
import com.oeims.routes.sessionRoutes
import com.oeims.services.AuthService
import com.oeims.services.EventService
import com.oeims.services.ExamService
import com.oeims.services.SessionService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting(
    authService: AuthService,
    examService: ExamService,
    sessionService: SessionService,
    eventService: EventService
) {
    // ── Global error handling ─────────────────────────────────────────────────
    // Services throw typed exceptions — map them to HTTP status codes once here
    // so individual route handlers stay clean.
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "Bad request"))
        }
        exception<IllegalStateException> { call, cause ->
            call.respond(HttpStatusCode.Conflict, ErrorResponse(cause.message ?: "Conflict"))
        }
        exception<NoSuchElementException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse(cause.message ?: "Not found"))
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Internal server error"))
        }
    }

    // ── Routes ────────────────────────────────────────────────────────────────
    routing {
        authRoutes(authService)
        examRoutes(examService)
        sessionRoutes(sessionService, eventService)
        participantRoutes(sessionService)
    }
}
