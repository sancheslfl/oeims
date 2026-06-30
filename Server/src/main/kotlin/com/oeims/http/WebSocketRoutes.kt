package com.oeims.http

import com.oeims.connections.WebSocketBroadcaster
import com.oeims.models.dto.ParticipantResponse
import com.oeims.models.dto.SentinelEventMessage
import com.oeims.models.dto.toDomainSeverity
import com.oeims.models.toParticipantId
import com.oeims.services.EventService
import com.oeims.services.ParticipantService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.slf4j.Logger
import java.util.UUID

fun Route.webSocketRoutes(
    eventService: EventService,
    participantService: ParticipantService,
    webSocketBroadcaster: WebSocketBroadcaster,
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
                webSocketBroadcaster.register(UUID.fromString(participant.id), this@webSocket) {
                    receiveFrames(
                        participant = participant,
                        eventService = eventService,
                        log = application.log,
                    )
                }
            }
        }
    }
}

private suspend fun DefaultWebSocketServerSession.receiveFrames(
    participant: ParticipantResponse,
    eventService: EventService,
    log: Logger,
    json: Json = Json,
) {
    try {
        for (frame in incoming) {
            if (frame !is Frame.Text) continue

            val msg = try {
                json.decodeFromString<SentinelEventMessage>(frame.readText())
            } catch (e: SerializationException) {
                log.debug(
                    "Sentinel frame ignored for participant {}: malformed payload ({})",
                    participant.id,
                    e.message,
                )
                continue
            }

            val severity = msg.severity.toDomainSeverity()
            if (severity == null) {
                log.warn(
                    "Sentinel frame ignored for participant {}: unknown severity '{}'",
                    participant.id,
                    msg.severity,
                )
                continue
            }

            eventService.create(
                participantId = participant.id.toParticipantId(),
                monitorName = msg.monitorName,
                message = msg.message,
                severity = severity,
            )
        }
    } catch (_: ClosedReceiveChannelException) {
        log.debug(
            "Sentinel WebSocket closed for participant {}: {}",
            participant.id,
            closeReason.await(),
        )
    } catch (e: Throwable) {
        log.warn(
            "Sentinel WebSocket failed for participant {}",
            participant.id,
            e,
        )
    }
}
