package com.oeims.connections

import kotlinx.serialization.Serializable

object WebSocketMessageTypes {
    const val EXAM_IDENTITY_CODE = "ExamIdentityCode"
}

@Serializable
data class WebSocketOutboundMessage<T>(
    val type: String,
    val data: T,
)