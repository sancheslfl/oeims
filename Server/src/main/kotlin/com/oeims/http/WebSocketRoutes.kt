package com.oeims.http

import com.oeims.models.dto.DaemonEventMessage
import com.oeims.models.dto.toDomainSeverity
import com.oeims.models.ConnectionStatus
import com.oeims.models.ids.toParticipantId
import com.oeims.repositories.interfaces.IParticipantRepository
import com.oeims.services.EventService
import io.ktor.server.application.log
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

fun Route.webSocketRoutes(
    eventService: EventService,
    participantRepository: IParticipantRepository
) {
    authenticate("auth-student") {
        webSocket("/ws/daemon/{participantId}") {
            val authenticatedUserId = call.userId()
            val participantId       = call.uuidParam("participantId")

            val participant = participantRepository.findById(participantId)
                ?: return@webSocket close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Participant not found"))

            if (participant.userId != authenticatedUserId)
                return@webSocket close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Forbidden"))

            participantRepository.updateConnectionStatus(participantId, ConnectionStatus.CONNECTED)

            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        try {
                            val msg      = Json.decodeFromString<DaemonEventMessage>(frame.readText())
                            val severity = msg.severity.toDomainSeverity()

                            if (severity == null) {
                                application.log.warn("Service sent unknown severity '${msg.severity}' for participant $participantId — frame dropped")
                            } else {
                                eventService.handleEvent(
                                    participantId = participant.id.toParticipantId(),
                                    monitorName   = msg.monitorName,
                                    message       = msg.message,
                                    severity      = severity
                                )
                            }
                        } catch (e: SerializationException) {
                            // suppress because malformed frame and keep connection alive
                            application.log.debug(
                                "Daemon sent malformed frame for {} - ignored: {}",
                                participantId,
                                e.message
                            )
                        }
                    }
                }
            } catch (_: ClosedReceiveChannelException) {
                application.log.debug("Daemon disconnected: {} — {}", participantId, closeReason.await())
            } catch (e: Throwable) {
                application.log.warn("Daemon WebSocket error for $participantId", e)
            } finally {
                participantRepository.updateConnectionStatus(participantId, ConnectionStatus.DISCONNECTED)
            }
        }
    }
}
