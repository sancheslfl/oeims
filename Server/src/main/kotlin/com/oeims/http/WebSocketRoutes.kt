package com.oeims.http

import com.oeims.models.dto.DaemonEventMessage
import com.oeims.models.dto.toDomainSeverity
import com.oeims.models.ConnectionStatus
import com.oeims.models.ids.toParticipantId
import com.oeims.repositories.interfaces.IParticipantRepository
import com.oeims.services.EventService
import com.oeims.websocket.ConnectionRegistry
import io.ktor.server.application.log
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

fun Route.webSocketRoutes(
    connectionRegistry: ConnectionRegistry,
    eventService: EventService,
    participantRepository: IParticipantRepository
) {
    // ── Daemon channel ────────────────────────────────────────────────────────
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
                            val msg = Json.decodeFromString<DaemonEventMessage>(frame.readText())
                            eventService.handleEvent(
                                participantId = participant.id.toParticipantId(),
                                monitorName   = msg.monitorName,
                                message       = msg.message,
                                severity      = msg.severity.toDomainSeverity()
                            )
                        } catch (_: Exception) {
                            // Malformed frame — ignore and keep connection alive
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

    // ── Professor console channel ─────────────────────────────────────────────
    authenticate("auth-professor") {
        webSocket("/ws/console/{sessionId}") {
            val sessionId = call.uuidParam("sessionId")
            val flow = connectionRegistry.flowForSession(sessionId)

            val job = launch {
                flow.collect { message -> send(message) }
            }

            runCatching {
                for (frame in incoming) { /* server -> client only; ignore any client frames */ }
            }.onFailure { e ->
                if (e !is ClosedReceiveChannelException)
                    application.log.warn("Console WebSocket error for session $sessionId", e)
            }.also {
                job.cancel()
            }
        }
    }
}
