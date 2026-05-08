package com.oeims.websocket

import com.oeims.dto.EventResponse
import com.oeims.dto.ParticipantStatusUpdate
import java.util.UUID

// Placeholder used until the real ConnectionRegistry (backed by WebSocket sessions)
// is implemented. Broadcasts are silently dropped — safe to use during development.
object NoOpConnectionRegistry : IConnectionRegistry {
    override suspend fun broadcastEventToSession(sessionId: UUID, event: EventResponse) = Unit
    override suspend fun broadcastStatusUpdate(sessionId: UUID, update: ParticipantStatusUpdate) = Unit
}
