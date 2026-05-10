package com.oeims.http

import com.oeims.models.ids.toParticipantId
import com.oeims.models.ids.toUserId
import com.oeims.services.SessionService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.participantRoutes(sessionService: SessionService) {
    authenticate("auth-student") {

        // POST /participants/{id}/heartbeat
        post("/participants/{id}/heartbeat") {
            val userId        = call.userId()
            val participantId = call.uuidParam("id")
            sessionService.heartbeat(participantId.toParticipantId(), userId.toUserId())
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
