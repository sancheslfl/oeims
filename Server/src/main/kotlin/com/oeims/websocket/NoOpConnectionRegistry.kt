package com.oeims.websocket

import com.oeims.models.dto.EventResponse
import com.oeims.models.dto.ParticipantStatusUpdate
import kotlinx.coroutines.flow.SharedFlow
import java.util.UUID

/**
 * Placeholder used as impl of [IConnectionRegistry].
 * Broadcasts are silently dropped and safe to use during development.
*/
object NoOpConnectionRegistry : IConnectionRegistry {
    override fun flowForSession(sessionId: UUID): SharedFlow<String> =
        throw NotImplementedError("NoOpConnectionRegistry does not support flowForSession")

    override suspend fun broadcastEventToSession(sessionId: UUID, event: EventResponse) = Unit
    override suspend fun broadcastStatusUpdate(sessionId: UUID, update: ParticipantStatusUpdate) = Unit
}
