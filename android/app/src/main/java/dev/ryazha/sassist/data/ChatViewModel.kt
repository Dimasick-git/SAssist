package dev.ryazha.sassist.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.ryazha.sassist.crypto.E2ee
import dev.ryazha.sassist.model.AuthMethod
import dev.ryazha.sassist.model.ChatMessage
import dev.ryazha.sassist.model.ConnState
import dev.ryazha.sassist.model.Stage
import dev.ryazha.sassist.net.AuthApi
import dev.ryazha.sassist.net.ChatClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class ChatState(
    val stage: Stage = Stage.Welcome,
    val connState: ConnState = ConnState.Disconnected,
    val channels: List<String> = listOf("general", "code-help", "showtime"),
    val currentChannel: String = "general",
    val messagesByChannel: Map<String, List<ChatMessage>> = emptyMap(),
    val presenceByChannel: Map<String, Int> = emptyMap(),
    val username: String = "",
    val authMethod: AuthMethod = AuthMethod.Email,
    val pendingIdentifier: String = "",
    val pendingUsername: String = "",
    val devCode: String? = null,
    val authBusy: Boolean = false,
    val authError: String? = null,
    val codeMode: Boolean = false,
    val e2ee: Boolean = true,
    val returnStage: Stage = Stage.Chats
) {
    val messages: List<ChatMessage> get() = messagesByChannel[currentChannel] ?: emptyList()
    val presence: Int get() = presenceByChannel[currentChannel] ?: 0
}

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val session = Session(app)
    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()
    private var client: ChatClient? = null

    val serverUrl: String get() = session.serverUrl

    init {
        val token = session.token
        if (!token.isNullOrBlank()) {
            _state.value = _state.value.copy(stage = Stage.Chats, username = session.username ?: "")
            connect()
        }
    }

    // ---- navigation ----
    fun goWelcome() { _state.value = _state.value.copy(stage = Stage.Welcome, authError = null) }
    fun startAuth() { _state.value = _state.value.copy(stage = Stage.EnterIdentifier, authError = null) }
    fun setMethod(m: AuthMethod) { _state.value = _state.value.copy(authMethod = m) }
    fun setServerUrl(url: String) { if (url.isNotBlank()) session.serverUrl = url.trim() }

    // ---- auth ----
    fun requestCode(method: AuthMethod, identifier: String, username: String) {
        val id = identifier.trim()
        if (id.isBlank()) {
            _state.value = _state.value.copy(authError = "Enter your " + (if (method == AuthMethod.Phone) "phone" else "email"))
            return
        }
        _state.value = _state.value.copy(authBusy = true, authError = null, authMethod = method)
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                AuthApi.requestCode(session.serverUrl, if (method == AuthMethod.Phone) "phone" else "email", id)
            }
            if (res.ok) {
                _state.value = _state.value.copy(
                    authBusy = false, stage = Stage.EnterCode,
                    pendingIdentifier = id, pendingUsername = username.trim(),
                    devCode = res.devCode, authError = null
                )
            } else {
                _state.value = _state.value.copy(authBusy = false, authError = res.error ?: "Could not send code")
            }
        }
    }

    fun verifyCode(code: String) {
        val s = _state.value
        _state.value = s.copy(authBusy = true, authError = null)
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                AuthApi.verifyCode(
                    session.serverUrl,
                    if (s.authMethod == AuthMethod.Phone) "phone" else "email",
                    s.pendingIdentifier, code.trim(), s.pendingUsername
                )
            }
            if (res.ok && res.token != null) {
                session.token = res.token
                session.username = res.username ?: s.pendingUsername
                _state.value = _state.value.copy(authBusy = false, stage = Stage.Chats, username = session.username ?: "", authError = null)
                connect()
            } else {
                _state.value = _state.value.copy(authBusy = false, authError = res.error ?: "Invalid or expired code")
            }
        }
    }

    fun resendCode() {
        val s = _state.value
        requestCode(s.authMethod, s.pendingIdentifier, s.pendingUsername)
    }

    // ---- websocket ----
    private fun connect() {
        if (client != null) return
        _state.value = _state.value.copy(connState = ConnState.Connecting)
        val c = ChatClient(
            onOpen = {
                val join = JSONObject().put("type", "join").put("token", session.token ?: "")
                client?.send(join.toString())
            },
            onText = { handle(it) },
            onClosed = { _state.value = _state.value.copy(connState = ConnState.Disconnected) },
            onFailure = { _state.value = _state.value.copy(connState = ConnState.Error) }
        )
        client = c
        c.connect(session.serverUrl)
    }

    private fun handle(raw: String) {
        val o = try { JSONObject(raw) } catch (e: Exception) { return }
        when (o.optString("type")) {
            "welcome" -> {
                val chans = jsonStrings(o.optJSONArray("channels"))
                _state.value = _state.value.copy(
                    connState = ConnState.Connected,
                    username = o.optString("username", _state.value.username),
                    channels = if (chans.isEmpty()) _state.value.channels else chans
                )
            }
            "history" -> {
                val ch = o.optString("channel")
                val arr = o.optJSONArray("messages")
                val list = mutableListOf<ChatMessage>()
                if (arr != null) for (i in 0 until arr.length()) parseMsg(arr.getJSONObject(i), ch)?.let { list.add(it) }
                val map = _state.value.messagesByChannel.toMutableMap(); map[ch] = list
                _state.value = _state.value.copy(messagesByChannel = map)
            }
            "message" -> {
                val mo = o.optJSONObject("message") ?: return
                val ch = mo.optString("channel")
                val msg = parseMsg(mo, ch) ?: return
                val map = _state.value.messagesByChannel.toMutableMap()
                val cur = (map[ch] ?: emptyList()).toMutableList(); cur.add(msg); map[ch] = cur
                _state.value = _state.value.copy(messagesByChannel = map)
            }
            "presence" -> {
                val ch = o.optString("channel")
                val count = o.optJSONArray("users")?.length() ?: 0
                val map = _state.value.presenceByChannel.toMutableMap(); map[ch] = count
                _state.value = _state.value.copy(presenceByChannel = map)
            }
            "channels" -> {
                val chans = jsonStrings(o.optJSONArray("channels"))
                if (chans.isNotEmpty()) _state.value = _state.value.copy(channels = chans)
            }
        }
    }

    private fun jsonStrings(arr: org.json.JSONArray?): List<String> {
        val out = mutableListOf<String>()
        if (arr != null) for (i in 0 until arr.length()) out.add(arr.getString(i))
        return out
    }

    private fun parseMsg(o: JSONObject, channel: String): ChatMessage? {
        return try {
            val raw = o.optString("text", "")
            val text = E2ee.decrypt(raw, session.roomKey(channel))
            ChatMessage(
                id = o.optString("id"),
                channel = o.optString("channel", channel),
                username = o.optString("username"),
                text = text,
                ts = o.optLong("ts")
            )
        } catch (e: Exception) { null }
    }

    // ---- chat actions ----
    fun openChannel(ch: String) {
        _state.value = _state.value.copy(currentChannel = ch, stage = Stage.Chat)
        client?.send(JSONObject().put("type", "switchChannel").put("channel", ch).toString())
    }
    fun backToChats() { _state.value = _state.value.copy(stage = Stage.Chats) }
    fun openScripts() { _state.value = _state.value.copy(returnStage = _state.value.stage, stage = Stage.Scripts) }
    fun closeScripts() { _state.value = _state.value.copy(stage = _state.value.returnStage) }
    fun toggleCode() { _state.value = _state.value.copy(codeMode = !_state.value.codeMode) }

    fun send(text: String) {
        if (text.isBlank()) return
        val ch = _state.value.currentChannel
        val body = if (_state.value.e2ee) E2ee.encrypt(text, session.roomKey(ch)) else text
        client?.send(JSONObject().put("type", "send").put("channel", ch).put("text", body).toString())
        if (_state.value.codeMode) _state.value = _state.value.copy(codeMode = false)
    }

    fun lastPreview(ch: String): String {
        val m = _state.value.messagesByChannel[ch]?.lastOrNull() ?: return "Tap to start chatting"
        return (m.username + ": " + m.text).replace("\n", " ").take(48)
    }

    fun setRoomKey(key: String) { if (key.isNotBlank()) session.setRoomKey(_state.value.currentChannel, key.trim()) }

    fun logout() {
        client?.close(); client = null
        session.clear()
        _state.value = ChatState()
    }
}
