package dev.ryazha.sassist.script

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType

/** Bridge object exposed to user scripts as the global `sa`. */
class ScriptHost(
    private val onSend: (String) -> Unit,
    private val lastMessageText: String
) {
    private val sb = StringBuilder()
    private val client = OkHttpClient()

    fun log(v: Any?) { sb.append(v?.toString() ?: "null").append('\n') }
    fun print(v: Any?) = log(v)
    fun send(v: Any?) { val s = v?.toString() ?: ""; onSend(s); log("[sent] " + s) }
    fun lastMessage(): String = lastMessageText
    fun output(): String = sb.toString()

    /** 
     * Simple blocking fetch for scripts. 
     * Usage: var res = sa.fetch("https://api.github.com/repos/Atmosphere-NX/Atmosphere/releases/latest");
     */
    fun fetch(url: String): String? {
        return try {
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { it.body?.string() }
        } catch (e: Exception) {
            log("!! fetch error: " + e.message)
            null
        }
    }

    fun post(url: String, json: String): String? {
        return try {
            val body = json.toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url(url).post(body).build()
            client.newCall(req).execute().use { it.body?.string() }
        } catch (e: Exception) {
            log("!! post error: " + e.message)
            null
        }
    }
    
    /** Nintendo Switch Title ID validator */
    fun isTitleId(id: String): Boolean = id.matches(Regex("0100[0-9A-Fa-f]{12}"))
}
