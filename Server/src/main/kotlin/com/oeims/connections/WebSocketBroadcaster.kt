package com.oeims.connections

import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class WebSocketBroadcaster(
    val json: Json = Json { encodeDefaults = true }
) {
    @PublishedApi
    internal val sessions = ConcurrentHashMap<UUID, DefaultWebSocketServerSession>()

    suspend fun register(
        participantId: UUID,
        session: DefaultWebSocketServerSession,
        block: suspend () -> Unit
    ) {
        sessions[participantId] = session

        try {
            block()
        } finally {
            sessions.remove(participantId, session)
        }
    }

    suspend inline fun <reified T> send(
        participantId: UUID,
        message: T
    ): Boolean {
        val session = sessions[participantId] ?: return false

        return try {
            session.send(Frame.Text(json.encodeToString(message)))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            sessions.remove(participantId, session)
            false
        }
    }
}