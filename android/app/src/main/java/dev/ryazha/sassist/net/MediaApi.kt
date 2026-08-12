package dev.ryazha.sassist.net

import android.util.Base64
import dev.ryazha.sassist.model.MediaRef
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.BufferedSink
import org.json.JSONObject
import java.io.InputStream
import java.util.concurrent.TimeUnit

/** Upload/download helper. Raw uploads avoid base64's size and CPU overhead. Media is not E2EE. */
object MediaApi {
    const val MAX_BYTES = 30 * 1024 * 1024

    private val client = OkHttpClient.Builder()
        .connectTimeout(75, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .callTimeout(240, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val JSON = "application/json".toMediaTypeOrNull()!!

    data class UploadResult(val media: MediaRef?, val error: String?)

    fun mediaUrl(serverUrl: String, id: String): String = AuthApi.httpBase(serverUrl) + "/media/" + id

    fun upload(
        serverUrl: String, token: String, bytes: ByteArray, mime: String, name: String, kind: String,
        durationMs: Long? = null, onProgress: ((Int) -> Unit)? = null
    ): UploadResult = uploadStream(serverUrl, token, { bytes.inputStream() }, bytes.size.toLong(), mime, name, kind, durationMs, onProgress)

    /** Streams a content URI source once to the HTTP request body; no whole-file read or base64 allocation. */
    fun uploadStream(
        serverUrl: String,
        token: String,
        open: () -> InputStream?,
        size: Long,
        mime: String,
        name: String,
        kind: String,
        durationMs: Long? = null,
        onProgress: ((Int) -> Unit)? = null
    ): UploadResult {
        if (size <= 0) return UploadResult(null, "file is empty")
        if (size > MAX_BYTES) return UploadResult(null, "file too large (max 30MB)")
        val body = object : RequestBody() {
            override fun contentType() = mime.toMediaTypeOrNull() ?: "application/octet-stream".toMediaTypeOrNull()
            override fun contentLength() = size
            override fun writeTo(sink: BufferedSink) {
                open()?.use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var sent = 0L
                    var lastPercent = -1
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        sink.write(buffer, 0, count)
                        sent += count
                        val percent = ((sent * 100L) / size).toInt().coerceIn(0, 100)
                        if (percent != lastPercent) { lastPercent = percent; onProgress?.invoke(percent) }
                    }
                } ?: throw java.io.IOException("cannot open media")
            }
        }
        return try {
            val urlBuilder = (AuthApi.httpBase(serverUrl) + "/upload/raw").toHttpUrl().newBuilder()
                .addQueryParameter("mime", mime).addQueryParameter("name", name).addQueryParameter("kind", kind)
            durationMs?.let { urlBuilder.addQueryParameter("durationMs", it.toString()) }
            val response = client.newCall(
                Request.Builder().url(urlBuilder.build()).header("Authorization", "Bearer $token").post(body).build()
            ).execute()
            response.use { resp ->
                if (resp.code == 404) return uploadLegacy(serverUrl, token, open, size, mime, name, kind, durationMs)
                val json = JSONObject(resp.body?.string() ?: "{}")
                if (!resp.isSuccessful || !json.optBoolean("ok")) return UploadResult(null, json.optString("error", "upload failed"))
                resultFromJson(json, mime, name, kind, size, durationMs)
            }
        } catch (e: Exception) {
            UploadResult(null, e.message ?: "network error")
        }
    }

    /** Legacy fallback prevents broken uploads during a rolling backend deploy. */
    private fun uploadLegacy(
        serverUrl: String, token: String, open: () -> InputStream?, size: Long, mime: String, name: String, kind: String, durationMs: Long?
    ): UploadResult = try {
        val bytes = open()?.use { it.readBytes() } ?: return UploadResult(null, "cannot open media")
        val payload = JSONObject().put("dataBase64", Base64.encodeToString(bytes, Base64.NO_WRAP))
            .put("mime", mime).put("name", name).put("kind", kind)
            .apply { durationMs?.let { put("durationMs", it) } }.toString()
        client.newCall(
            Request.Builder().url(AuthApi.httpBase(serverUrl) + "/upload").header("Authorization", "Bearer $token").post(payload.toRequestBody(JSON)).build()
        ).execute().use { resp ->
            val json = JSONObject(resp.body?.string() ?: "{}")
            if (!resp.isSuccessful || !json.optBoolean("ok")) UploadResult(null, json.optString("error", "upload failed"))
            else resultFromJson(json, mime, name, kind, size, durationMs)
        }
    } catch (e: Exception) { UploadResult(null, e.message ?: "network error") }

    private fun resultFromJson(json: JSONObject, mime: String, name: String, kind: String, bytes: Long, durationMs: Long?): UploadResult {
        val media = json.getJSONObject("media")
        return UploadResult(
            MediaRef(
                id = media.getString("id"), kind = media.optString("kind", kind), mime = media.optString("mime", mime),
                name = media.optString("name", name), size = media.optLong("size", bytes),
                width = if (media.has("width")) media.optInt("width") else null,
                height = if (media.has("height")) media.optInt("height") else null,
                durationMs = if (media.has("durationMs")) media.optLong("durationMs") else durationMs
            ), null
        )
    }
}
