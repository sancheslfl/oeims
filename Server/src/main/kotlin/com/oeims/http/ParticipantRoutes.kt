package com.oeims.http

import com.oeims.models.ids.toParticipantId
import com.oeims.services.ParticipantService
import com.oeims.services.SessionService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.participantRoutes(participantService: ParticipantService) {
    authenticate("auth-student") {
        post("/participants/{id}/heartbeat") {
            val authenticatedParticipantId = call.participantId()
            val participantId = call.uuidParam("id")

            participantService.sendHeartbeat(
                pid = participantId.toParticipantId(),
                authenticatedPid= authenticatedParticipantId.toParticipantId()
            )

            call.respond(HttpStatusCode.NoContent)
        }
    }
}
