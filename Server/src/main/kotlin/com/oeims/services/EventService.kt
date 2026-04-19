package com.oeims.services

import com.oeims.dto.EventResponse
import com.oeims.models.Severity
import com.oeims.repositories.EventRecord
import com.oeims.repositories.interfaces.IEventRepository
import com.oeims.repositories.interfaces.IParticipantRepository
import com.oeims.websocket.IConnectionRegistry
import java.util.UUID

class EventService(
    private val eventRepository: IEventRepository,
    private val participantRepository: IParticipantRepository,
    private val connectionRegistry: IConnectionRegistry
) {

    suspend fun handleEvent(
        participantId: UUID,
        monitorName: String,
        message: String,
        severity: Severity
    ): EventResponse {
        val participant = participantRepository.findById(participantId)
            ?: throw NoSuchElementException("Participant not found")

        val record = eventRepository.create(participantId, monitorName, message, severity)
        val response = record.toResponse()

        connectionRegistry.broadcastEventToSession(participant.sessionId, response)

        return response
    }

    fun getSessionEvents(sessionId: UUID): List<EventResponse> =
        eventRepository.findBySession(sessionId).map { it.toResponse() }

    private fun EventRecord.toResponse() = EventResponse(
        id            = id.toString(),
        participantId = participantId.toString(),
        monitorName   = monitorName,
        message       = message,
        severity      = severity.name,
        occurredAt    = occurredAt.toString()
    )
}
