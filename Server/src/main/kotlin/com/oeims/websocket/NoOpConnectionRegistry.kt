package com.oeims.websocket

import com.oeims.models.dto.EventResponse
import com.oeims.models.dto.ParticipantStatusUpdate
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.util.UUID

// Placeholder used in tests. Broadcasts and flows are silently no-ops.
object NoOpConnectionRegistry : IConnectionRegistry {
    override fun flowForSession(sessionId: UUID): SharedFlow<String> = MutableSharedFlow()
    override suspend fun broadcastEventToSession(sessionId: UUID, event: EventResponse) = Unit
    override suspend fun broadcastStatusUpdate(sessionId: UUID, update: ParticipantStatusUpdate) = Unit
}
