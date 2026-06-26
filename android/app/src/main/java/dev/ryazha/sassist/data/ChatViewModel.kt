package dev.ryazha.sassist.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ryazha.sassist.model.ChatMessage
import dev.ryazha.sassist.model.ConnState
import dev.ryazha.sassist.net.ChatClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class ChatState(
    val conn: ConnState = ConnState.Disconnected,
    val username: String = "",
    val userId: String = "",
    val channels: List<String> = listOf("general", "code-help", "showtime"),
    val currentChannel: String = "general",
    val messages: List<ChatMessage> = emptyList(),
    val presence: List<String> = emptyList(),
    val error: String? = null
)

class ChatViewModel : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private val perChannel = mutableMapOf<String, MutableList<ChatMessage>>()
    private var client: ChatClient? = null
    private var pendingUsername = ""

    fun connect(url: String, username: String) {
        pendingUsername = username.ifBlank { "anon" }
        _state.update { it.copy(conn = ConnState.Connecting, username = pendingUsername, error = null) }
        client = ChatClient(
            onOpen = {
                val join = buildJsonObject {
                    put("type", "join"); put("username", pendingUsername)
                }
                client?.sendRaw(join.toString())
            },
            onText = { handle(it) },
            onClosed = { reason -> post { _state.update { it.copy(conn = ConnState.Disconnected) } } },
            onFailure = { msg -> post { _state.update { it.copy(conn = ConnState.Error, error = msg) } } }
        )
        client?.connect(url)
    }

    fun send(text: String) {
        val s = _state.value
        if (text.isBlank()) return
        val msg = buildJsonObject {
            put("type", "send"); put("channel", s.currentChannel); put("text", text)
        }
        client?.sendRaw(msg.toString())
    }

    fun switchChannel(channel: String) {
        if (channel == _state.value.currentChannel) return
        _state.update { it.copy(currentChannel = channel, messages = perChannel[channel]?.toList() ?: emptyList()) }
        val msg = buildJsonObject { put("type", "switchChannel"); put("channel", channel) }
        client?.sendRaw(msg.toString())
    }

    fun disconnect() {
        client?.close(); client = null
        _state.update { it.copy(conn = ConnState.Disconnected) }
    }

    override fun onCleared() { disconnect() }

    private fun post(block: () -> Unit) { viewModelScope.launch { block() } }

    private fun handle(raw: String) {
        val obj = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return
        when (obj["type"]?.jsonPrimitive?.contentOrNull) {
            "welcome" -> {
                val uid = obj["userId"]?.jsonPrimitive?.contentOrNull ?: ""
                val chans = obj["channels"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                post { _state.update { it.copy(conn = ConnState.Connected, userId = uid, channels = chans, error = null) } }
            }
            "channels" -> {
                val chans = obj["channels"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                post { _state.update { it.copy(channels = chans) } }
            }
            "history" -> {
                val ch = obj["channel"]?.jsonPrimitive?.contentOrNull ?: return
                val msgs = obj["messages"]?.jsonArray?.mapNotNull { parseMsg(it.jsonObject) } ?: emptyList()
                perChannel[ch] = msgs.toMutableList()
                post {
                    if (_state.value.currentChannel == ch)
                        _state.update { it.copy(messages = msgs) }
                }
            }
            "message" -> {
                val m = obj["message"]?.jsonObject?.let { parseMsg(it) } ?: return
                val buf = perChannel.getOrPut(m.channel) { mutableListOf() }
                buf.add(m)
                post {
                    if (_state.value.currentChannel == m.channel)
                        _state.update { it.copy(messages = buf.toList()) }
                }
            }
            "presence" -> {
                val ch = obj["channel"]?.jsonPrimitive?.contentOrNull ?: return
                val users = obj["users"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                post {
                    if (_state.value.currentChannel == ch)
                        _state.update { it.copy(presence = users) }
                }
            }
            "error" -> {
                val reason = obj["reason"]?.jsonPrimitive?.contentOrNull ?: "error"
                post { _state.update { it.copy(error = reason) } }
            }
        }
    }

    private fun parseMsg(o: JsonObject): ChatMessage? = runCatching {
        ChatMessage(
            id = o["id"]?.jsonPrimitive?.contentOrNull ?: "",
            channel = o["channel"]?.jsonPrimitive?.contentOrNull ?: "",
            username = o["username"]?.jsonPrimitive?.contentOrNull ?: "",
            text = o["text"]?.jsonPrimitive?.contentOrNull ?: "",
            ts = o["ts"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
        )
    }.getOrNull()
}
