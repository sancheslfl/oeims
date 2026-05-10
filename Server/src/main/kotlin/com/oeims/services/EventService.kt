package com.oeims.services

import com.oeims.models.dto.EventResponse
import com.oeims.exceptions.NotFoundException
import com.oeims.models.ids.ParticipantId
import com.oeims.models.ids.SessionId
import com.oeims.models.Severity
import com.oeims.repositories.EventRecord
import com.oeims.repositories.interfaces.IEventRepository
import com.oeims.repositories.interfaces.IParticipantRepository
import com.oeims.websocket.IConnectionRegistry

class EventService(
    private val eventRepository: IEventRepository,
    private val participantRepository: IParticipantRepository,
    private val connectionRegistry: IConnectionRegistry
) {

    suspend fun handleEvent(
        participantId: ParticipantId,
        monitorName: String,
        message: String,
        severity: Severity
    ): EventResponse {
        val participant = participantRepository.findById(participantId.value)
            ?: throw NotFoundException("Participant not found")

        val record = eventRepository.create(participantId.value, monitorName, message, severity)
        val response = record.toResponse()

        connectionRegistry.broadcastEventToSession(participant.sessionId, response)

        return response
    }

    suspend fun getSessionEvents(sessionId: SessionId): List<EventResponse> =
        eventRepository.findBySession(sessionId.value).map { it.toResponse() }

    private fun EventRecord.toResponse() = EventResponse(
        id            = id.toString(),
        participantId = participantId.toString(),
        monitorName   = monitorName,
        message       = message,
        severity      = severity.name,
        occurredAt    = occurredAt.toString()
    )
}
