package com.oeims.http

import com.oeims.connections.WebSocketService
import com.oeims.models.toParticipantId
import com.oeims.services.ParticipantService
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext

fun Route.webSockets(
    participantService: ParticipantService,
    webSocketService: WebSocketService,
) {
    authenticate("auth-student") {
        webSocket("/ws/daemon/{participantId}") {
            val authenticatedParticipantId = call.participantId()
            val participantId = call.uuidParam("participantId")

            if (participantId != authenticatedParticipantId) {
                return@webSocket close(
                    CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Connection not accepted")
                )
            }

            val participant = participantService.getParticipantById(participantId.toParticipantId())
                ?: return@webSocket close(
                    CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Connection not accepted")
                )

            // SLF4J logging context
            withContext(
                MDCContext(
                    mapOf(
                        "participant-id" to participant.id,
                        "participant-email" to participant.email,
                    )
                )
            ) {
                webSocketService.handleConnection(
                    participant = participant,
                    session = this@webSocket,
                )
            }
        }
    }
}