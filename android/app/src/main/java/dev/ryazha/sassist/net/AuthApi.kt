package dev.ryazha.sassist.net

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
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
    data class ProfileResult(
        val ok: Boolean, val error: String?,
        val userId: String? = null,
        val displayName: String? = null, val handle: String? = null,
        val premium: Boolean = false, val color: String? = null, val bio: String? = null,
        val avatarId: String? = null, val bannerId: String? = null
    )
    data class HandleStatus(val valid: Boolean, val available: Boolean, val premiumOnly: Boolean, val reason: String?)

    fun httpBase(ws: String): String =
        ws.trim().replace("wss://", "https://").replace("ws://", "http://").trimEnd('/')


    private fun parseJsonResponse(resp: Response): JSONObject {
        val raw = resp.body?.string().orEmpty()
        val trimmed = raw.trimStart()
        if (!resp.isSuccessful) {
            val detail = if (trimmed.startsWith("{")) {
                runCatching { JSONObject(trimmed).optString("error") }.getOrNull().orEmpty()
            } else ""
            throw IllegalStateException(detail.ifBlank { "Server returned HTTP ${resp.code}. Check Server URL." })
        }
        if (trimmed.isBlank()) return JSONObject()
        if (!trimmed.startsWith("{")) {
            val kind = when {
                trimmed.startsWith("<!doctype", ignoreCase = true) || trimmed.startsWith("<html", ignoreCase = true) -> "HTML page"
                else -> "non-JSON response"
            }
            throw IllegalStateException("Server returned $kind instead of SAssist API JSON. Open Server settings and use the backend URL, not a website page.")
        }
        return JSONObject(trimmed)
    }

    fun requestCode(serverUrl: String, method: String, identifier: String): RequestResult {
        return try {
            val payload = JSONObject().put("method", method).put("identifier", identifier).toString()
            val req = Request.Builder()
                .url(httpBase(serverUrl) + "/auth/request")
                .post(payload.toRequestBody(JSON)).build()
            client.newCall(req).execute().use { resp ->
                val j = parseJsonResponse(resp)
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
                val j = parseJsonResponse(resp)
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

    // ---- profile / handle / premium ----
    private fun parseProfile(j: JSONObject): ProfileResult {
        val u = j.optJSONObject("user")
        return ProfileResult(
            ok = j.optBoolean("ok"),
            error = if (j.isNull("error")) null else j.optString("error", null),
            userId = u?.optString("id")?.takeIf { it.isNotBlank() },
            displayName = u?.optString("displayName"),
            handle = u?.optString("handle"),
            premium = u?.optBoolean("premium") ?: false,
            color = u?.optString("color"),
            bio = u?.optString("bio"),
            avatarId = u?.optString("avatar")?.takeIf { it.isNotBlank() },
            bannerId = u?.optString("banner")?.takeIf { it.isNotBlank() }
        )
    }

    private fun postAuthed(serverUrl: String, path: String, token: String, body: JSONObject): ProfileResult {
        return try {
            val req = Request.Builder()
                .url(httpBase(serverUrl) + path)
                .header("Authorization", "Bearer $token")
                .post(body.toString().toRequestBody(JSON)).build()
            client.newCall(req).execute().use { resp ->
                parseProfile(parseJsonResponse(resp))
            }
        } catch (e: Exception) {
            ProfileResult(false, e.message ?: "network error")
        }
    }

    fun getProfile(serverUrl: String, token: String): ProfileResult {
        return try {
            val req = Request.Builder()
                .url(httpBase(serverUrl) + "/profile")
                .header("Authorization", "Bearer $token")
                .get().build()
            client.newCall(req).execute().use { resp ->
                parseProfile(parseJsonResponse(resp))
            }
        } catch (e: Exception) {
            ProfileResult(false, e.message ?: "network error")
        }
    }

    fun getUserProfile(serverUrl: String, token: String, userId: String): ProfileResult {
        return try {
            val req = Request.Builder()
                .url(httpBase(serverUrl) + "/users/" + java.net.URLEncoder.encode(userId, "UTF-8"))
                .header("Authorization", "Bearer $token")
                .get().build()
            client.newCall(req).execute().use { response -> parseProfile(parseJsonResponse(response)) }
        } catch (e: Exception) {
            ProfileResult(false, e.message ?: "network error")
        }
    }

    fun updateProfile(serverUrl: String, token: String, displayName: String?, bio: String?, color: String?, avatarId: String? = null, bannerId: String? = null): ProfileResult {
        val b = JSONObject()
        displayName?.let { b.put("displayName", it) }
        bio?.let { b.put("bio", it) }
        color?.let { b.put("color", it) }
        avatarId?.let { b.put("avatar", it) }
        bannerId?.let { b.put("banner", it) }
        return postAuthed(serverUrl, "/profile", token, b)
    }

    fun checkHandle(serverUrl: String, handle: String): HandleStatus {
        return try {
            val req = Request.Builder()
                .url(httpBase(serverUrl) + "/handle/check?handle=" + java.net.URLEncoder.encode(handle, "UTF-8"))
                .get().build()
            client.newCall(req).execute().use { resp ->
                val j = parseJsonResponse(resp)
                HandleStatus(
                    valid = j.optBoolean("valid"), available = j.optBoolean("available"),
                    premiumOnly = j.optBoolean("premiumOnly"),
                    reason = if (j.isNull("reason")) null else j.optString("reason", null)
                )
            }
        } catch (e: Exception) {
            HandleStatus(false, false, false, e.message ?: "network error")
        }
    }

    fun claimHandle(serverUrl: String, token: String, handle: String): ProfileResult =
        postAuthed(serverUrl, "/handle/claim", token, JSONObject().put("handle", handle))

    fun claimPremium(serverUrl: String, token: String, code: String): ProfileResult =
        postAuthed(serverUrl, "/premium/claim", token, JSONObject().put("code", code))
}
