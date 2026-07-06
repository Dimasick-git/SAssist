package dev.ryazha.sassist.net

import android.util.Base64
import dev.ryazha.sassist.model.MediaRef
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Upload/download helper for photos, videos and files. NOTE: media is not E2EE. */
object MediaApi {
    const val MAX_BYTES = 30 * 1024 * 1024

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()
    private val JSON = "application/json".toMediaType()

    data class UploadResult(val media: MediaRef?, val error: String?)

    fun mediaUrl(serverUrl: String, id: String): String =
        AuthApi.httpBase(serverUrl) + "/media/" + id

    fun upload(serverUrl: String, token: String, bytes: ByteArray, mime: String, name: String, kind: String, durationMs: Long? = null): UploadResult {
        if (bytes.size > MAX_BYTES) return UploadResult(null, "file too large (max 30MB)")
        return try {
            val payload = JSONObject()
                .put("dataBase64", Base64.encodeToString(bytes, Base64.NO_WRAP))
                .put("mime", mime).put("name", name).put("kind", kind)
                .apply { durationMs?.let { put("durationMs", it) } }
                .toString()
            val req = Request.Builder()
                .url(AuthApi.httpBase(serverUrl) + "/upload")
                .header("Authorization", "Bearer $token")
                .post(payload.toRequestBody(JSON)).build()
            client.newCall(req).execute().use { resp ->
                val j = JSONObject(resp.body?.string() ?: "{}")
                if (!j.optBoolean("ok")) return UploadResult(null, j.optString("error", "upload failed"))
                val m = j.getJSONObject("media")
                UploadResult(
                    MediaRef(
                        id = m.getString("id"), kind = m.optString("kind", kind),
                        mime = m.optString("mime", mime), name = m.optString("name", name),
                        size = m.optLong("size", bytes.size.toLong()),
                        width = if (m.has("width")) m.optInt("width") else null,
                        height = if (m.has("height")) m.optInt("height") else null,
                        durationMs = if (m.has("durationMs")) m.optLong("durationMs") else durationMs
                    ), null
                )
            }
        } catch (e: Exception) {
            UploadResult(null, e.message ?: "network error")
        }
    }
}
