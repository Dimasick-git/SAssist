package dev.ryazha.sassist.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.ryazha.sassist.crypto.E2ee
import dev.ryazha.sassist.data.AppDatabase
import dev.ryazha.sassist.data.Session
import dev.ryazha.sassist.data.LocalMessage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Flushes the offline send queue while the app is backgrounded: opens a short
 * WebSocket session, joins with the saved token, re-sends every pending row
 * with its original clientId (the server dedupes) and reconciles echoes into
 * the local DB. Runs only when WorkManager reports connectivity.
 */
class SendQueueWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "sassist_send_queue"
        private const val MAX_SEND_ATTEMPTS = 5
        private const val SESSION_TIMEOUT_MS = 25_000L
    }

    override suspend fun doWork(): Result {
        val session = Session(applicationContext)
        val token = session.token ?: return Result.success()
        var url = session.serverUrl
        if (url.isBlank()) return Result.success()
        val dao = AppDatabase.getDatabase(applicationContext).messageDao()

        val pending = dao.getPendingMessages()
        if (pending.isEmpty()) return Result.success()
        val sendable = pending.filter { it.attempts < MAX_SEND_ATTEMPTS }
        for (m in pending) if (m.attempts >= MAX_SEND_ATTEMPTS) dao.markFailed(m.id)
        if (sendable.isEmpty()) return Result.success()

        if (url.startsWith("http")) url = url.replace("http://", "ws://").replace("https://", "wss://")
        if (!url.contains("://")) url = "wss://$url"

        val client = OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .build()

        val remaining = sendable.map { it.clientId }.filterNotNull().toMutableSet()
        val echoes = java.util.Collections.synchronizedMap(mutableMapOf<String, JSONObject>())
        var ws: WebSocket? = null

        val flushed = withTimeoutOrNull(SESSION_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                fun finish(ok: Boolean) { if (cont.isActive) cont.resume(ok) }
                ws = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        webSocket.send(JSONObject().put("type", "join").put("token", token).toString())
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val o = try { JSONObject(text) } catch (e: Exception) { return }
                        when (o.optString("type")) {
                            "welcome" -> {
                                for (m in sendable) {
                                    val body = if (m.text.isNotEmpty()) E2ee.encrypt(m.text, session.roomKey(m.channel)) else m.text
                                    val frame = JSONObject().put("type", "send").put("channel", m.channel).put("text", body)
                                    m.clientId?.let { frame.put("clientId", it) }
                                    m.replyTo?.let { frame.put("replyTo", it) }
                                    m.mediaJson?.let { frame.put("media", JSONObject(it)) }
                                    webSocket.send(frame.toString())
                                }
                            }
                            "message" -> {
                                val mo = o.optJSONObject("message") ?: return
                                val cid = mo.optString("clientId", "")
                                if (cid.isNotEmpty() && remaining.remove(cid)) {
                                    echoes[cid] = mo
                                    if (remaining.isEmpty()) finish(true)
                                }
                            }
                            "error" -> finish(false)
                        }
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { finish(false) }
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { finish(false) }
                })
                cont.invokeOnCancellation { ws?.cancel() }
            }
        } ?: false

        // Replace optimistic rows with the confirmed server copies.
        for ((cid, mo) in echoes.toMap()) {
            val ch = mo.optString("channel")
            dao.reconcile(cid, LocalMessage(
                id = mo.optString("id"), clientId = null, channel = ch,
                userId = mo.optString("userId"), username = mo.optString("username"),
                handle = mo.optString("handle"), premium = mo.optBoolean("premium"),
                color = mo.optString("color", "5865F2"),
                text = E2ee.decrypt(mo.optString("text", ""), session.roomKey(ch)),
                ts = mo.optLong("ts"),
                mediaJson = mo.optJSONObject("media")?.toString(),
                replyTo = if (mo.isNull("replyTo")) null else mo.optString("replyTo", null),
                reactionsJson = mo.optJSONObject("reactions")?.toString()
            ))
        }
        if (!flushed) for (m in sendable) if (echoes[m.clientId] == null) dao.bumpAttempts(m.id)

        ws?.close(1000, "done")
        client.dispatcher.executorService.shutdown()
        return if (flushed) Result.success() else Result.retry()
    }
}
