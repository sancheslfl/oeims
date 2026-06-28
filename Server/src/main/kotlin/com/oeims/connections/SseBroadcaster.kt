package com.oeims.connections

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap

data class SseMessage(
    val event: SseEvent,
    val data: String
)

class SseBroadcaster {
    private val channelFlows = ConcurrentHashMap<SseChannel, MutableSharedFlow<SseMessage>>()

    fun subscribe(channel: SseChannel): SharedFlow<SseMessage> =
        flowForChannel(channel).asSharedFlow()

    suspend fun publish(
        channel: SseChannel,
        event: SseEvent,
        data: String
    ) {
        channelFlows[channel]?.emit(
            SseMessage(
                event = event,
                data = data
            )
        )
    }

    private fun flowForChannel(channel: SseChannel): MutableSharedFlow<SseMessage> =
        channelFlows.getOrPut(channel) {
            MutableSharedFlow(extraBufferCapacity = 64)
        }
}