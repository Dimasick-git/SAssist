package dev.ryazha.sassist.script

/** Bridge object exposed to user scripts as the global `sa`. */
class ScriptHost(
    private val onSend: (String) -> Unit,
    private val lastMessageText: String
) {
    private val sb = StringBuilder()
    fun log(v: Any?) { sb.append(v?.toString() ?: "null").append('\n') }
    fun print(v: Any?) = log(v)
    fun send(v: Any?) { val s = v?.toString() ?: ""; onSend(s); log("[sent] " + s) }
    fun lastMessage(): String = lastMessageText
    fun output(): String = sb.toString()
}
