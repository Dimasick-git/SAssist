package dev.ryazha.sassist.net

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** REST auth client. Phone/email OTP, just like Telegram/WhatsApp. */
object AuthApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val JSON = "application/json".toMediaType()

    data class RequestResult(val ok: Boolean, val devCode: String?, val delivered: Boolean, val error: String?)
    data class VerifyResult(val ok: Boolean, val token: String?, val username: String?, val error: String?)

    fun httpBase(ws: String): String =
        ws.trim().replace("wss://", "https://").replace("ws://", "http://").trimEnd('/')

    fun requestCode(serverUrl: String, method: String, identifier: String): RequestResult {
        return try {
            val payload = JSONObject().put("method", method).put("identifier", identifier).toString()
            val req = Request.Builder()
                .url(httpBase(serverUrl) + "/auth/request")
                .post(payload.toRequestBody(JSON)).build()
            client.newCall(req).execute().use { resp ->
                val j = JSONObject(resp.body?.string() ?: "{}")
                RequestResult(
                    ok = j.optBoolean("ok"),
                    devCode = if (j.isNull("devCode")) null else j.optString("devCode", null),
                    delivered = j.optBoolean("delivered"),
                    error = if (j.isNull("error")) null else j.optString("error", null)
                )
            }
        } catch (e: Exception) {
            RequestResult(false, null, false, e.message ?: "network error")
        }
    }

    fun verifyCode(serverUrl: String, method: String, identifier: String, code: String, username: String): VerifyResult {
        return try {
            val payload = JSONObject()
                .put("method", method).put("identifier", identifier)
                .put("code", code).put("username", username).toString()
            val req = Request.Builder()
                .url(httpBase(serverUrl) + "/auth/verify")
                .post(payload.toRequestBody(JSON)).build()
            client.newCall(req).execute().use { resp ->
                val j = JSONObject(resp.body?.string() ?: "{}")
                val user = j.optJSONObject("user")
                VerifyResult(
                    ok = j.optBoolean("ok"),
                    token = if (j.isNull("token")) null else j.optString("token", null),
                    username = user?.optString("username"),
                    error = if (j.isNull("error")) null else j.optString("error", null)
                )
            }
        } catch (e: Exception) {
            VerifyResult(false, null, null, e.message ?: "network error")
        }
    }
}
