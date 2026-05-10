package com.oeims.plugins

import com.oeims.models.dto.ErrorResponse
import com.oeims.exceptions.ConflictException
import com.oeims.exceptions.ForbiddenException
import com.oeims.exceptions.NotFoundException
import com.oeims.exceptions.UnauthorizedException
import com.oeims.exceptions.ValidationException
import com.oeims.http.authRoutes
import com.oeims.http.examRoutes
import com.oeims.http.participantRoutes
import com.oeims.http.sessionRoutes
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
    install(StatusPages) {
        exception<ValidationException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.message ?: "Bad request"))
        }
        exception<UnauthorizedException> { call, cause ->
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse(cause.message ?: "Unauthorized"))
        }
        exception<ForbiddenException> { call, cause ->
            call.respond(HttpStatusCode.Forbidden, ErrorResponse(cause.message ?: "Forbidden"))
        }
        exception<NotFoundException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse(cause.message ?: "Not found"))
        }
        exception<ConflictException> { call, cause ->
            call.respond(HttpStatusCode.Conflict, ErrorResponse(cause.message ?: "Conflict"))
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
