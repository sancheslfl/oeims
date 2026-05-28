package com.oeims.http

import com.oeims.services.SessionService
import com.oeims.sse.SseBroadcaster
import com.oeims.sse.SseChannel
import com.oeims.sse.SseChannels
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import io.ktor.util.cio.ChannelWriteException

fun Route.sseRoutes(
    sessionService: SessionService,
    sseBroadcaster: SseBroadcaster
) {
    authenticate("auth-professor") {
        sse("/events/{channel}/listen") {
            val professorId = call.userId()
            val channelValue = call.parameters["channel"] ?: return@sse close()
            val channel = SseChannel(channelValue)

            val sessionId = SseChannels.sessionId(channel) ?: return@sse close()
            val session = sessionService.getSession(sessionId)

            if (session.supervisorId != professorId.toString()) {
                close()
                return@sse
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
                // suppress because client disconnected
            }
        }
    }
}