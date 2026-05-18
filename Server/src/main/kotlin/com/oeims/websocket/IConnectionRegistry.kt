package com.oeims.websocket

import com.oeims.models.dto.EventResponse
import com.oeims.models.dto.ParticipantStatusUpdate
import kotlinx.coroutines.flow.SharedFlow
import java.util.UUID

interface IConnectionRegistry {
    // Returns the SharedFlow for a session, creating it if it doesn't exist yet.
    // Console WebSocket connections collect from this to receive live updates.
    fun flowForSession(sessionId: UUID): SharedFlow<String>

    // Broadcast a monitor event to the professor watching a session
    suspend fun broadcastEventToSession(sessionId: UUID, event: EventResponse)

    // Broadcast a participant connection status change to the professor
    suspend fun broadcastStatusUpdate(sessionId: UUID, update: ParticipantStatusUpdate)
}
