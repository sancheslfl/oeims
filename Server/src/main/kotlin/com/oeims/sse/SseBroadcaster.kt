package com.oeims.sse

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter

data class SseMessage(
    val channel: SseChannel,
    val event: SseEvent,
    val data: String
)

class SseBroadcaster {
    private val messages = MutableSharedFlow<SseMessage>(
        extraBufferCapacity = 64
    )

    suspend fun publish(
        channel: SseChannel,
        event: SseEvent,
        data: String
    ) {
        messages.emit(
            SseMessage(
                channel = channel,
                event = event,
                data = data
            )
        )
    }

    fun subscribe(channel: SseChannel) =
        messages.filter { it.channel == channel }
}