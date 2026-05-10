package com.oeims.websocket

import com.oeims.dto.EventResponse
import com.oeims.dto.ParticipantStatusUpdate
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ConnectionRegistry : IConnectionRegistry {

    private val sessionFlows = ConcurrentHashMap<UUID, MutableSharedFlow<String>>()

    override fun flowForSession(sessionId: UUID): SharedFlow<String> =
        sessionFlows.getOrPut(sessionId) {
            MutableSharedFlow(extraBufferCapacity = 64)
        }.asSharedFlow()

    override suspend fun broadcastEventToSession(sessionId: UUID, event: EventResponse) {
        sessionFlows[sessionId]?.emit(Json.encodeToString(event))
    }

    override suspend fun broadcastStatusUpdate(sessionId: UUID, update: ParticipantStatusUpdate) {
        sessionFlows[sessionId]?.emit(Json.encodeToString(update))
    }
}
