package com.oeims.services

import com.oeims.models.NotFoundException
import com.oeims.models.SessionStatus
import com.oeims.models.Severity
import com.oeims.models.dto.EventResponse
import com.oeims.models.ids.ParticipantId
import com.oeims.models.ids.SessionId
import com.oeims.models.ids.toSessionId
import com.oeims.repositories.EventRecord
import com.oeims.repositories.interfaces.IEventRepository
import com.oeims.repositories.interfaces.IParticipantRepository
import com.oeims.repositories.interfaces.ISessionRepository
import com.oeims.connections.SseBroadcaster
import com.oeims.connections.SseChannels
import com.oeims.connections.SseEvent
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

class EventService(
    private val eventRepository: IEventRepository,
    private val participantRepository: IParticipantRepository,
    private val sessionRepository: ISessionRepository,
    private val sseBroadcaster: SseBroadcaster
) {
    private val log = LoggerFactory.getLogger(EventService::class.java)

    suspend fun handleEvent(
        participantId: ParticipantId,
        monitorName: String,
        message: String,
        severity: Severity
    ): EventResponse? {
        val participant = participantRepository.findById(participantId.value)
            ?: throw NotFoundException("Participant not found")

        val session = sessionRepository.findById(participant.sessionId)
            ?: throw NotFoundException("Session not found")

        if (session.status != SessionStatus.ACTIVE) {
            return null
        }

        val record = eventRepository.create(
            participantId.value,
            monitorName,
            message,
            severity
        )

        val response = record.toResponse()

        log.info("[{}] [{}] {}", monitorName, severity.name, message)

        sseBroadcaster.publish(
            channel = SseChannels.session(participant.sessionId.toSessionId()),
            event = SseEvent.PARTICIPANT_EVENT_RECEIVED,
            data = Json.encodeToString(response)
        )

        return response
    }

    suspend fun getSessionEvents(sessionId: SessionId): List<EventResponse> =
        eventRepository.findBySession(sessionId.value).map { it.toResponse() }

    private fun EventRecord.toResponse() = EventResponse(
        id = id.toString(),
        participantId = participantId.toString(),
        monitorName = monitorName,
        message = message,
        severity = severity.name,
        occurredAt = occurredAt.toString()
    )
}
