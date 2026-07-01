package com.oeims.config

import com.oeims.connections.SentinelWebSocketManager
import com.oeims.http.*
import com.oeims.models.dto.ErrorResponse
import com.oeims.services.AuthService
import com.oeims.services.EventService
import com.oeims.services.ExamService
import com.oeims.services.SessionService
import com.oeims.connections.SseBroadcaster
import com.oeims.models.ConflictException
import com.oeims.models.ForbiddenException
import com.oeims.models.NotFoundException
import com.oeims.models.UnauthorizedException
import com.oeims.models.ValidationException
import com.oeims.services.ParticipantService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting(
    authService: AuthService,
    examService: ExamService,
    sessionService: SessionService,
    participantService: ParticipantService,
    eventService: EventService,
    sseBroadcaster: SseBroadcaster,
    webSocketManager: SentinelWebSocketManager,
) {
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

    val basePath = environment.config
        .propertyOrNull("app.api.base-path")
        ?.getString()
        ?: throw IllegalStateException("API base path is not configured in config file")

    routing {
        route(basePath) {
            authentication(authService)
            exams(examService)
            participants(participantService)
            sessions(sessionService, participantService, eventService)
            sse(sessionService, sseBroadcaster)
            webSockets(participantService, eventService, webSocketManager)
        }
    }
}
