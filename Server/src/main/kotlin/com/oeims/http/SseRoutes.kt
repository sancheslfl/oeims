package com.oeims.http

import com.oeims.models.toProfessorId
import com.oeims.services.SessionService
import com.oeims.connections.SseBroadcaster
import com.oeims.connections.SseChannel
import com.oeims.connections.SseChannels
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.sse.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*

fun Route.sseRoutes(
    sessionService: SessionService,
    sseBroadcaster: SseBroadcaster
) {
    authenticate("auth-professor") {
        sse("/events/{channel}/listen") {
            val professorId = call.userId()
            val channelValue = call.parameters["channel"] ?: return@sse close()
            val channel = SseChannel(channelValue)

            if (channel != SseChannels.sessions()) {
                val sessionId = SseChannels.sessionId(channel) ?: return@sse close()

                if (!sessionService.canSupervise(sessionId, professorId.toProfessorId())) {
                    close()
                    return@sse
                }
            }

            try {
                send(
                    ServerSentEvent(
                        event = "connected",
                        data = "{}"
                    )
                )

                sseBroadcaster
                    .subscribe(channel)
                    .collect { message ->
                        send(
                            ServerSentEvent(
                                event = message.event.code,
                                data = message.data
                            )
                        )
                    }
            } catch (_: ChannelWriteException) {
                // client disconnected
            } catch (_: ClosedWriteChannelException) {
                // client disconnected
            }
        }
    }
}