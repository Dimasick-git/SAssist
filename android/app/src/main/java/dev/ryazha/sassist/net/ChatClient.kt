package dev.ryazha.sassist.net

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/** Thin OkHttp WebSocket wrapper. Speaks raw JSON strings. */
class ChatClient(
    private val onOpen: () -> Unit,
    private val onText: (String) -> Unit,
    private val onClosed: () -> Unit,
    private val onFailure: (String) -> Unit
) {
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()
    private var ws: WebSocket? = null

    fun connect(url: String) {
        val req = Request.Builder().url(url).build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { onOpen() }
            override fun onMessage(webSocket: WebSocket, text: String) { onText(text) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { onClosed() }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onFailure(t.message ?: "ws failure")
            }
        })
    }

    fun send(text: String) { ws?.send(text) }
    fun close() { ws?.close(1000, "bye"); ws = null }
}
