package com.oeims.connections

import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks active WebSocket sessions by a connection key.
 *
 * This class owns active WebSocket sessions and raw text-frame
 * sending/receiving.
 *
 * @param K non-null stable key used to identify one logical WebSocket
 *          connection. The key must keep the same equals/hashCode behavior
 *          while registered.
 */
class WebSocketManager<K: Any> {
    private val log = LoggerFactory.getLogger(WebSocketManager::class.java)

    private val sessions = ConcurrentHashMap<K, DefaultWebSocketServerSession>()

    /**
     * Registers [session] for [key] while [block] is running.
     *
     * A later registration with the same [key] replaces the previous session.
     * The `finally` block removes only the same session that was registered,
     * so an old connection cannot unregister a newer replacement.
     *
     * @param key logical owner of the connection.
     * @param session active Ktor WebSocket session.
     * @param block suspends for the lifetime of the connection, usually while
     *              the route consumes incoming frames.
     */
    suspend fun register(
        key: K,
        session: DefaultWebSocketServerSession,
        block: suspend () -> Unit,
    ) {
        sessions[key] = session

        try {
            block()
        } finally {
            sessions.remove(key, session)
        }
    }

    /**
     * Registers [session] for [key] and receives text frames until the connection
     * closes or violates the text-frame contract.
     *
     * This method is still transport-only. It does not parse JSON and does not
     * know what each text frame means.
     *
     * Non-text frames are rejected because callers using this method are opting
     * into a text-only WebSocket protocol.
     *
     * @param key logical owner of the connection.
     * @param session active Ktor WebSocket session.
     * @param maxFrameBytes maximum accepted text frame payload size, measured
     *                      before UTF-8 decoding. `null` disables this check.
     * @param onTextFrame called once per accepted text frame.
     */
    suspend fun receiveText(
        key: K,
        session: DefaultWebSocketServerSession,
        maxFrameBytes: Long? = null,
        onTextFrame: suspend (String) -> Unit,
    ) {
        try {
            register(
                key = key,
                session = session,
            ) {
                for (frame in session.incoming) {
                    if (frame !is Frame.Text) {
                        session.close(
                            CloseReason(
                                CloseReason.Codes.CANNOT_ACCEPT,
                                "Only text frames are accepted",
                            )
                        )
                        return@register
                    }

                    if ((maxFrameBytes != null) && (frame.data.size > maxFrameBytes)) {
                        session.close(
                            CloseReason(
                                CloseReason.Codes.TOO_BIG,
                                "WebSocket frame too large",
                            )
                        )
                        return@register
                    }

                    onTextFrame(frame.readText())
                }
            }
        } catch (_: ClosedReceiveChannelException) {
            log.debug("WebSocket closed for key {}", key)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            log.warn("WebSocket failed for key {}", key, e)
        }
    }

    /**
     * Sends one text WebSocket frame to the session registered for [key].
     *
     * The caller is responsible for serializing [text] and enforcing any
     * protocol-level limits before calling this function.
     *
     * @return `true` when a session existed and the frame was queued/sent;
     *         `false` when no session exists or the session failed.
     *
     * @throws CancellationException when coroutine cancellation is the cause.
     */
    suspend fun sendText(
        key: K,
        text: String,
    ): Boolean {
        val session = sessions[key] ?: return false

        return try {
            session.send(Frame.Text(text))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            sessions.remove(key, session)
            false
        }
    }
}