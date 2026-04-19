package com.oeims.websocket

import com.oeims.dto.EventResponse
import com.oeims.dto.ParticipantStatusUpdate
import java.util.UUID

interface IConnectionRegistry {
    // Broadcast a monitor event to the professor watching a session
    suspend fun broadcastEventToSession(sessionId: UUID, event: EventResponse)

    // Broadcast a participant connection status change to the professor
    suspend fun broadcastStatusUpdate(sessionId: UUID, update: ParticipantStatusUpdate)
}
