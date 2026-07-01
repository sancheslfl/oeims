package com.oeims.connections

import com.oeims.models.ExamIdentityCode
import com.oeims.models.dto.SentinelEventMessage
import io.ktor.server.websocket.DefaultWebSocketServerSession
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.UUID

const val MAX_FRAME_BYTES: Long = 8 * 1024    // 8 KB

/**
 * Sentinel-specific WebSocket manager.
 *
 * This class adapts the generic WebSocket connection manager to the OEIMS
 * Sentinel protocol. Each connected Sentinel is indexed by its participant ID.
 *
 * It owns Sentinel WebSocket protocol messages in both directions:
 *
 * - outbound server-to-Sentinel messages;
 * - inbound Sentinel-to-server text message decoding.
 *
 * Authentication, ownership checks, participant lookup, and event creation
 * remain in the WebSocket route/application flow.
 *
 * @param connections generic WebSocket session store.
 * @param json serializer used for Sentinel WebSocket messages.
 */
class SentinelWebSocketManager(
    private val connections: WebSocketManager<UUID> = WebSocketManager(),
    private val json: Json = Json { encodeDefaults = true },
) {
    private val log = LoggerFactory.getLogger(SentinelWebSocketManager::class.java)

    /**
     * Registers the Sentinel WebSocket connection for [participantId].
     *
     * @param participantId participant authenticated by the student/Sentinel JWT.
     * @param session Ktor WebSocket session created by the route.
     * @param block suspends while the route receives Sentinel frames.
     */
    suspend fun register(
        participantId: UUID,
        session: DefaultWebSocketServerSession,
        block: suspend () -> Unit,
    ) {
        connections.register(
            key = participantId,
            session = session,
            block = block,
        )
    }

    /**
     * Registers the Sentinel WebSocket connection and receives Sentinel event
     * messages until the socket closes.
     *
     * The Sentinel protocol currently uses one text WebSocket frame per event.
     * Binary frames are rejected by the generic WebSocket manager. Oversized
     * frames are closed before JSON decoding.
     *
     * Malformed JSON frames are ignored so one bad event does not terminate the
     * whole Sentinel connection.
     *
     * @param participantId participant authenticated by the student/Sentinel JWT.
     * @param session Ktor WebSocket session created by the route.
     * @param onEventMessage called once per valid Sentinel event message.
     */
    suspend fun receiveEventMessages(
        participantId: UUID,
        session: DefaultWebSocketServerSession,
        onEventMessage: suspend (SentinelEventMessage) -> Unit,
    ) {
        connections.receiveText(
            key = participantId,
            session = session,
            maxFrameBytes = MAX_FRAME_BYTES,
        ) { text ->
            val message = decodeEventMessage(
                participantId = participantId,
                text = text,
            ) ?: return@receiveText

            onEventMessage(message)
        }
    }

    /**
     * Sends the exam identity code to a connected Sentinel.
     *
     * Protocol shape:
     *
     * ```json
     * {
     *   "type": "EXAM_IDENTITY_CODE",
     *   "data": "ABCDEFGH"
     * }
     * ```
     *
     * The value class is unwrapped with [ExamIdentityCode.value] because the
     * WebSocket contract uses a plain string payload, not a domain object.
     *
     * @param participantId participant whose Sentinel should receive the code.
     * @param examIdentityCode validated 8-character exam identity code.
     *
     * @return `true` when the participant had an active Sentinel connection and
     *         the frame was sent; `false` otherwise.
     */
    suspend fun sendExamIdentityCode(
        participantId: UUID,
        examIdentityCode: ExamIdentityCode,
    ): Boolean =
        send(
            participantId = participantId,
            message = WebSocketOutboundMessage(
                type = WebSocketMessageTypes.EXAM_IDENTITY_CODE,
                data = examIdentityCode.value,
            ),
        )

    /**
     * Decodes one Sentinel event message from a text frame payload.
     *
     * @return decoded message, or `null` when the frame payload is not valid
     *         Sentinel JSON.
     */
    private fun decodeEventMessage(
        participantId: UUID,
        text: String,
    ): SentinelEventMessage? =
        try {
            json.decodeFromString<SentinelEventMessage>(text)
        } catch (e: SerializationException) {
            log.debug(
                "Sentinel frame ignored for participant {}: malformed payload ({})",
                participantId,
                e.message,
            )
            null
        }

    /**
     * Serializes and sends one Sentinel outbound message.
     *
     * @param participantId participant used as the WebSocket connection key.
     * @param message serializable protocol message.
     *
     * @return `true` when the frame was sent; `false` when the Sentinel is not
     *         connected or the connection failed.
     */
    private suspend inline fun <reified T> send(
        participantId: UUID,
        message: T,
    ): Boolean =
        connections.sendText(
            key = participantId,
            text = json.encodeToString(message),
        )
}