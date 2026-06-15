package com.oeims.http

import com.oeims.models.ids.toProfessorId
import com.oeims.services.SessionService
import com.oeims.sse.SseBroadcaster
import com.oeims.sse.SseChannel
import com.oeims.sse.SseChannels
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import io.ktor.util.cio.ChannelWriteException
import io.ktor.utils.io.ClosedWriteChannelException

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