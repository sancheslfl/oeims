package com.oeims.http

import com.oeims.models.dto.SentinelEventMessage
import com.oeims.models.dto.toDomainSeverity
import com.oeims.models.ids.toParticipantId
import com.oeims.repositories.interfaces.IParticipantRepository
import com.oeims.services.EventService
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

fun Route.webSocketRoutes(
    eventService: EventService,
    participantRepository: IParticipantRepository
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

            val participant = participantRepository.findById(participantId)
                ?: return@webSocket close(
                    CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Connection not accepted")
                )

            // SLF4J logging context
            withContext(
                MDCContext(
                    mapOf(
                        "participant-id" to participant.id.toString(),
                        "participant-email" to participant.email,
                    )
                )
            ) {
                try {
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue

                        try {
                            val msg = Json.decodeFromString<SentinelEventMessage>(frame.readText())
                            val severity = msg.severity.toDomainSeverity()

                            if (severity == null) {
                                application.log.warn(
                                    "Sentinel frame ignored for participant {}: unknown severity '{}'",
                                    participantId,
                                    msg.severity,
                                )
                                continue
                            }

                            eventService.handleEvent(
                                participantId = participant.id.toParticipantId(),
                                monitorName = msg.monitorName,
                                message = msg.message,
                                severity = severity,
                            )
                        } catch (e: SerializationException) {
                            application.log.debug(
                                "Sentinel frame ignored for participant {}: malformed payload ({})",
                                participantId,
                                e.message,
                            )
                        }
                    }
                } catch (_: ClosedReceiveChannelException) {
                    application.log.debug(
                        "Sentinel WebSocket closed for participant {}: {}",
                        participantId,
                        closeReason.await(),
                    )
                } catch (e: Throwable) {
                    application.log.warn(
                        "Sentinel WebSocket failed for participant {}",
                        participantId,
                        e,
                    )
                }
            }
        }
    }
}
