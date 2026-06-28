package com.oeims.services

import com.oeims.models.SessionStatus
import com.oeims.models.dto.ParticipantStatusUpdate
import com.oeims.models.ids.toSessionId
import com.oeims.repositories.interfaces.IParticipantRepository
import com.oeims.repositories.interfaces.ISessionRepository
import com.oeims.connections.SseBroadcaster
import com.oeims.connections.SseChannels
import com.oeims.connections.SseEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.Instant
import kotlin.time.toKotlinDuration

class HeartbeatService(
    private val participantRepository: IParticipantRepository,
    private val sessionRepository: ISessionRepository,
    private val sseBroadcaster: SseBroadcaster,
    private val config: HeartbeatConfig
) {

    fun start(scope: CoroutineScope) {
        scope.launch {
            while (true) {
                delay(config.interval.toKotlinDuration())
                checkHeartbeats()
            }
        }
    }

    private suspend fun checkHeartbeats() {
        val threshold = Instant.now() - config.timeout
        val timedOut = participantRepository.updateTimedOut(threshold)

        timedOut.forEach { participant ->
            val session = sessionRepository.findById(participant.sessionId)
                ?: return@forEach

            if (session.status != SessionStatus.ACTIVE) {
                return@forEach
            }

            val update = ParticipantStatusUpdate(
                participantId = participant.id.toString(),
                connectionStatus = "TIMED_OUT"
            )

            sseBroadcaster.publish(
                channel = SseChannels.session(participant.sessionId.toSessionId()),
                event = SseEvent.PARTICIPANT_STATUS_UPDATED,
                data = Json.encodeToString(update)
            )
        }
    }
}