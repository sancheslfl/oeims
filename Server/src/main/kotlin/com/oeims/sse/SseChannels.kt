package com.oeims.sse

import com.oeims.models.ids.SessionId
import com.oeims.models.ids.toSessionId

@JvmInline
value class SseChannel(val value: String)


enum class SseEvent(val code: String) {
    PARTICIPANT_JOINED("participant.joined"),
    PARTICIPANT_STATUS_UPDATED("participant.status.updated"),
    PARTICIPANT_EVENT_RECEIVED("participant.event.received"),
    SESSION_CREATED("session.created"),
    SESSION_STATUS_UPDATED("session.status.updated"),
}

// TODO: Evaluate this
object SseChannels {
    private const val SESSION_PREFIX = "session."
    private const val SESSIONS_CHANNEL = "sessions"

    fun session(sessionId: SessionId): SseChannel =
        SseChannel("$SESSION_PREFIX${sessionId.value}")

    fun sessions(): SseChannel = SseChannel(SESSIONS_CHANNEL)

    fun sessionId(channel: SseChannel): SessionId? {
        if (!channel.value.startsWith(SESSION_PREFIX)) {
            return null
        }

        return runCatching {
            channel.value.removePrefix(SESSION_PREFIX).toSessionId()
        }.getOrNull()
    }
}