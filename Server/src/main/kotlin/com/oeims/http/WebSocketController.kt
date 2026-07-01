package com.oeims.http

import com.oeims.connections.SentinelWebSocketManager
import com.oeims.models.dto.toDomainSeverity
import com.oeims.models.toParticipantId
import com.oeims.services.EventService
import com.oeims.services.ParticipantService
import io.ktor.server.application.log
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext

fun Route.webSockets(
    participantService: ParticipantService,
    eventService: EventService,
    webSocketManager: SentinelWebSocketManager
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
                webSocketManager.receiveEventMessages(
                    participantId = participantId,
                    session = this@webSocket,
                ) { message ->
                    val severity = message.severity.toDomainSeverity()

                    if (severity == null) {
                        application.log.warn(
                            "Sentinel frame ignored for participant {}: unknown severity '{}'",
                            participant.id,
                            message.severity,
                        )
                        return@receiveEventMessages
                    }

                    eventService.create(
                        participantId = participant.id.toParticipantId(),
                        monitorName = message.monitorName,
                        message = message.message,
                        severity = severity,
                    )
                }
            }
        }
    }
}