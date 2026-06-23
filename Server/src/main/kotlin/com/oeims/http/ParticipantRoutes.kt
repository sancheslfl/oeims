package com.oeims.http

import com.oeims.models.ids.toParticipantId
import com.oeims.services.SessionService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.participantRoutes(sessionService: SessionService) {
    authenticate("auth-student") {
        post("/participants/{id}/heartbeat") {
            val authenticatedParticipantId = call.participantId()
            val participantId = call.uuidParam("id")

            sessionService.heartbeat(
                pid = participantId.toParticipantId(),
                authenticatedPid= authenticatedParticipantId.toParticipantId()
            )

            call.respond(HttpStatusCode.NoContent)
        }
    }
}
