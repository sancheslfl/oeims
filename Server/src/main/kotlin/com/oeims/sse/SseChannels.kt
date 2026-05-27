package com.oeims.sse

import com.oeims.models.ids.SessionId
import com.oeims.models.ids.toSessionId

@JvmInline
value class SseChannel(val value: String)


enum class SseEvent(val code: String) {
    PARTICIPANT_JOINED("participant.joined")
}

object SseChannels {
    private const val SESSION_PREFIX = "session."

    fun session(sessionId: SessionId): SseChannel =
        SseChannel("$SESSION_PREFIX${sessionId.value}")

    fun sessionId(channel: SseChannel): SessionId? {
        if (!channel.value.startsWith(SESSION_PREFIX)) {
            return null
        }

        return runCatching {
            channel.value.removePrefix(SESSION_PREFIX).toSessionId()
        }.getOrNull()
    }
}