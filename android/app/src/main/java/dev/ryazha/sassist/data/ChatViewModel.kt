package dev.ryazha.sassist.data

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dev.ryazha.sassist.crypto.E2ee
import dev.ryazha.sassist.model.AuthMethod
import dev.ryazha.sassist.model.ChatMessage
import dev.ryazha.sassist.model.ConnState
import dev.ryazha.sassist.model.MediaRef
import dev.ryazha.sassist.model.Stage
import dev.ryazha.sassist.net.AuthApi
import dev.ryazha.sassist.net.ChatClient
import dev.ryazha.sassist.net.ConnectivityObserver
import dev.ryazha.sassist.net.MediaApi
import dev.ryazha.sassist.work.SendQueueWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.random.Random

data class ProfileUi(
    val displayName: String = "",
    val handle: String = "",
    val premium: Boolean = false,
    val color: String = "5865F2",
    val bio: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val handleCheck: String? = null
)

data class ChatState(
    val stage: Stage = Stage.Welcome,
    val connState: ConnState = ConnState.Disconnected,
    val channels: List<String> = listOf("general", "code-help", "showtime"),
    val currentChannel: String = "general",
    val messagesByChannel: Map<String, List<ChatMessage>> = emptyMap(),
    val presenceByChannel: Map<String, Int> = emptyMap(),
    val typingByChannel: Map<String, String?> = emptyMap(), // channel -> username
    val username: String = "",
    val userId: String = "",
    val authMethod: AuthMethod = AuthMethod.Email,
    val pendingIdentifier: String = "",
    val pendingUsername: String = "",
    val devCode: String? = null,
    val authBusy: Boolean = false,
    val authError: String? = null,
    val codeMode: Boolean = false,
    val e2ee: Boolean = true,
    val returnStage: Stage = Stage.Chats,
    val replyingTo: ChatMessage? = null,
    val uploadBusy: Boolean = false,
    val profile: ProfileUi = ProfileUi(),
    val customKeyChannels: Set<String> = emptySet()
) {
    val messages: List<ChatMessage> get() = messagesByChannel[currentChannel] ?: emptyList()
    val presence: Int get() = presenceByChannel[currentChannel] ?: 0
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val session = Session(app)
    private val db = AppDatabase.getDatabase(app)
    private val messageDao = db.messageDao()
    private val connectivity = ConnectivityObserver(app)
    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()
    private var client: ChatClient? = null
    private var messageJob: Job? = null
    private var reconnectJob: Job? = null
    private var backoffMs = 1_000L

    val serverUrl: String get() = session.serverUrl

    companion object {
        private const val MAX_SEND_ATTEMPTS = 5
        private const val BACKOFF_MAX_MS = 30_000L
    }

    init {
        val token = session.token
        if (!token.isNullOrBlank()) {
            _state.update { it.copy(stage = Stage.Chats, username = session.username ?: "") }
            connect()
        }
        observeLocalMessages()
        observeConnectivity()
        refreshCustomKeyFlags()
    }

    private fun observeLocalMessages() {
        messageJob?.cancel()
        messageJob = viewModelScope.launch {
            _state.map { it.currentChannel }.distinctUntilChanged()
                .flatMapLatest { ch -> messageDao.getMessages(ch).map { ch to it } }
                .collect { (ch, rows) ->
                    val msgs = rows.map { it.toChatMessage() }
                    _state.update { s -> s.copy(messagesByChannel = s.messagesByChannel + (ch to msgs)) }
                }
        }
    }

    private fun observeConnectivity() {
        viewModelScope.launch {
            connectivity.online.collect { online ->
                if (online) {
                    if (session.token != null && _state.value.connState != ConnState.Connected) {
                        reconnectJob?.cancel()
                        backoffMs = 1_000L
                        connect(force = true)
                    }
                } else {
                    _state.update { it.copy(connState = ConnState.Disconnected) }
                }
            }
        }
    }

    private fun refreshCustomKeyFlags() {
        _state.update { s ->
            s.copy(customKeyChannels = s.channels.filter { session.hasCustomRoomKey(it) }.toSet())
        }
    }

    // ---- navigation ----
    fun goWelcome() { _state.update { it.copy(stage = Stage.Welcome, authError = null) } }
    fun startAuth() { _state.update { it.copy(stage = Stage.EnterIdentifier, authError = null) } }
    fun setMethod(m: AuthMethod) { _state.update { it.copy(authMethod = m) } }
    fun setServerUrl(url: String) { if (url.isNotBlank()) session.serverUrl = url.trim() }

    // ---- auth ----
    fun requestCode(method: AuthMethod, identifier: String, username: String) {
        val id = identifier.trim()
        if (session.serverUrl.isBlank()) {
            _state.update { it.copy(authError = "Enter your server URL first") }
            return
        }
        if (id.isBlank()) {
            _state.update { it.copy(authError = "Enter your " + (if (method == AuthMethod.Phone) "phone" else "email")) }
            return
        }
        _state.update { it.copy(authBusy = true, authError = null, authMethod = method) }
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                AuthApi.requestCode(session.serverUrl, if (method == AuthMethod.Phone) "phone" else "email", id)
            }
            if (res.ok) {
                _state.update {
                    it.copy(
                        authBusy = false, stage = Stage.EnterCode,
                        pendingIdentifier = id, pendingUsername = username.trim(),
                        devCode = res.devCode, authError = null
                    )
                }
            } else {
                _state.update { it.copy(authBusy = false, authError = res.error ?: "Could not send code") }
            }
        }
    }

    fun verifyCode(code: String) {
        val s = _state.value
        _state.update { it.copy(authBusy = true, authError = null) }
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
                _state.update { it.copy(authBusy = false, stage = Stage.Chats, username = session.username ?: "", authError = null) }
                connect(force = true)
            } else {
                _state.update { it.copy(authBusy = false, authError = res.error ?: "Invalid or expired code") }
            }
        }
    }

    fun resendCode() {
        val s = _state.value
        requestCode(s.authMethod, s.pendingIdentifier, s.pendingUsername)
    }

    // ---- websocket ----
    private fun connect(force: Boolean = false) {
        if (client != null) {
            if (!force) return
            client?.close(); client = null
        }
        val token = session.token ?: return
        if (session.serverUrl.isBlank()) return
        _state.update { it.copy(connState = ConnState.Connecting) }

        var url = session.serverUrl
        if (url.startsWith("http")) {
            url = url.replace("http://", "ws://").replace("https://", "wss://")
        }
        if (!url.contains("://")) {
            url = "wss://$url"
        }

        val c = ChatClient(
            onOpen = {
                client?.send(JSONObject().put("type", "join").put("token", token).toString())
            },
            onText = { handle(it) },
            onClosed = {
                _state.update { it.copy(connState = ConnState.Disconnected) }
                scheduleReconnect()
            },
            onFailure = {
                _state.update { it.copy(connState = ConnState.Error) }
                scheduleReconnect()
            }
        )
        client = c
        c.connect(url)
    }

    private fun scheduleReconnect() {
        client = null
        if (session.token == null) return
        reconnectJob?.cancel()
        val delayMs = backoffMs + Random.nextLong(0, backoffMs / 2 + 1)
        backoffMs = (backoffMs * 2).coerceAtMost(BACKOFF_MAX_MS)
        reconnectJob = viewModelScope.launch {
            delay(delayMs)
            if (session.token != null && connectivity.isOnlineNow()) connect()
        }
    }

    private fun processPendingQueue() {
        viewModelScope.launch(Dispatchers.IO) {
            val pending = messageDao.getPendingMessages()
            for (m in pending) {
                if (m.attempts >= MAX_SEND_ATTEMPTS) { messageDao.markFailed(m.id); continue }
                messageDao.bumpAttempts(m.id)
                sendFrame(m)
            }
        }
    }

    /** Re-sends a queued row with its ORIGINAL clientId -- the server dedupes. */
    private fun sendFrame(m: LocalMessage) {
        val body = if (_state.value.e2ee && m.text.isNotEmpty()) E2ee.encrypt(m.text, session.roomKey(m.channel)) else m.text
        val o = JSONObject().put("type", "send").put("channel", m.channel).put("text", body)
        m.clientId?.let { o.put("clientId", it) }
        m.replyTo?.let { o.put("replyTo", it) }
        m.mediaJson?.let { o.put("media", JSONObject(it)) }
        client?.send(o.toString())
    }

    private fun syncSince() {
        viewModelScope.launch(Dispatchers.IO) {
            for (ch in _state.value.channels) {
                val since = messageDao.latestServerTs(ch)
                val o = JSONObject().put("type", "history").put("channel", ch)
                if (since != null) o.put("since", since)
                client?.send(o.toString())
            }
        }
    }

    private fun parseMessage(mo: JSONObject, channelHint: String? = null): LocalMessage {
        val ch = channelHint ?: mo.optString("channel")
        val rawText = mo.optString("text", "")
        val text = E2ee.decrypt(rawText, session.roomKey(ch))
        return LocalMessage(
            id = mo.optString("id"),
            clientId = null,
            channel = ch,
            userId = mo.optString("userId"),
            username = mo.optString("username"),
            handle = mo.optString("handle"),
            premium = mo.optBoolean("premium"),
            color = mo.optString("color", "5865F2"),
            text = text,
            ts = mo.optLong("ts"),
            mediaJson = mo.optJSONObject("media")?.toString(),
            replyTo = if (mo.isNull("replyTo")) null else mo.optString("replyTo", null),
            reactionsJson = mo.optJSONObject("reactions")?.toString()
        )
    }

    private fun handle(raw: String) {
        val o = try { JSONObject(raw) } catch (e: Exception) { return }
        when (o.optString("type")) {
            "welcome" -> {
                backoffMs = 1_000L
                val chans = jsonStrings(o.optJSONArray("channels"))
                val user = o.optJSONObject("user")
                _state.update {
                    it.copy(
                        connState = ConnState.Connected,
                        username = o.optString("username", it.username),
                        userId = user?.optString("id") ?: o.optString("userId", it.userId),
                        channels = if (chans.isEmpty()) it.channels else chans
                    )
                }
                refreshCustomKeyFlags()
                processPendingQueue()
                syncSince()
            }
            "history" -> {
                val ch = o.optString("channel")
                val arr = o.optJSONArray("messages")
                val list = mutableListOf<LocalMessage>()
                if (arr != null) for (i in 0 until arr.length()) {
                    list.add(parseMessage(arr.getJSONObject(i), ch))
                }
                if (list.isNotEmpty()) viewModelScope.launch(Dispatchers.IO) { messageDao.insertAll(list) }
            }
            "message" -> {
                val mo = o.optJSONObject("message") ?: return
                val msg = parseMessage(mo)
                val cid = mo.optString("clientId", "")
                viewModelScope.launch(Dispatchers.IO) {
                    if (cid.isNotEmpty()) messageDao.reconcile(cid, msg) else messageDao.insert(msg)
                }
            }
            "reaction" -> {
                val messageId = o.optString("messageId")
                val reactions = o.optJSONObject("reactions")?.toString()
                viewModelScope.launch(Dispatchers.IO) { messageDao.updateReactions(messageId, reactions) }
            }
            "presence" -> {
                val ch = o.optString("channel")
                val count = o.optJSONArray("users")?.length() ?: 0
                _state.update { it.copy(presenceByChannel = it.presenceByChannel + (ch to count)) }
            }
            "typing" -> {
                val ch = o.optString("channel")
                val user = o.optJSONObject("user")?.optString("displayName")
                if (user != _state.value.username) {
                    _state.update { it.copy(typingByChannel = it.typingByChannel + (ch to user)) }
                    viewModelScope.launch {
                        delay(3000)
                        if (_state.value.typingByChannel[ch] == user) {
                            _state.update { it.copy(typingByChannel = it.typingByChannel + (ch to null)) }
                        }
                    }
                }
            }
            "channels" -> {
                val chans = jsonStrings(o.optJSONArray("channels"))
                if (chans.isNotEmpty()) _state.update { it.copy(channels = chans) }
            }
        }
    }

    private fun jsonStrings(arr: JSONArray?): List<String> {
        val out = mutableListOf<String>()
        if (arr != null) for (i in 0 until arr.length()) out.add(arr.getString(i))
        return out
    }

    // ---- chat actions ----
    fun openChannel(ch: String) {
        _state.update { it.copy(currentChannel = ch, stage = Stage.Chat, replyingTo = null) }
        client?.send(JSONObject().put("type", "switchChannel").put("channel", ch).toString())
    }
    fun backToChats() { _state.update { it.copy(stage = Stage.Chats, replyingTo = null) } }
    fun openScripts() { _state.update { it.copy(returnStage = it.stage, stage = Stage.Scripts) } }
    fun closeScripts() { _state.update { it.copy(stage = it.returnStage) } }
    fun toggleCode() { _state.update { it.copy(codeMode = !it.codeMode) } }

    fun sendTyping() {
        val ch = _state.value.currentChannel
        client?.send(JSONObject().put("type", "typing").put("channel", ch).toString())
    }

    fun startReply(m: ChatMessage) { _state.update { it.copy(replyingTo = m) } }
    fun cancelReply() { _state.update { it.copy(replyingTo = null) } }

    fun send(text: String) {
        if (text.isBlank()) return
        enqueueAndSend(text = text, media = null)
        if (_state.value.codeMode) _state.update { it.copy(codeMode = false) }
    }

    private fun enqueueAndSend(text: String, media: MediaRef?) {
        val s = _state.value
        val ch = s.currentChannel
        val clientId = UUID.randomUUID().toString()
        val replyTo = s.replyingTo?.id
        val local = LocalMessage(
            id = "local_$clientId", clientId = clientId, channel = ch,
            userId = s.userId, username = s.username, text = text,
            ts = System.currentTimeMillis(),
            mediaJson = media?.let { mediaRefToJson(it).toString() },
            replyTo = replyTo, isPending = true
        )
        _state.update { it.copy(replyingTo = null) }
        viewModelScope.launch(Dispatchers.IO) {
            messageDao.insert(local)
            if (_state.value.connState == ConnState.Connected) {
                messageDao.bumpAttempts(local.id)
                sendFrame(local)
            } else {
                scheduleBackgroundFlush()
            }
        }
    }

    fun retryMessage(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            messageDao.markRetrying(id, System.currentTimeMillis())
            if (_state.value.connState == ConnState.Connected) processPendingQueue()
            else scheduleBackgroundFlush()
        }
    }

    /** Flush the send queue from the background when connectivity returns. */
    fun scheduleBackgroundFlush() {
        val work = OneTimeWorkRequestBuilder<SendQueueWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(getApplication())
            .enqueueUniqueWork(SendQueueWorker.WORK_NAME, ExistingWorkPolicy.KEEP, work)
    }

    // ---- media ----
    fun sendMedia(uri: Uri) {
        val app = getApplication<Application>()
        val token = session.token ?: return
        _state.update { it.copy(uploadBusy = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resolver = app.contentResolver
                val mime = resolver.getType(uri) ?: "application/octet-stream"
                var name = "file"
                resolver.query(uri, null, null, null, null)?.use { cur ->
                    val idx = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && cur.moveToFirst()) name = cur.getString(idx) ?: name
                }
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null || bytes.isEmpty()) {
                    _state.update { it.copy(uploadBusy = false) }
                    return@launch
                }
                val kind = when {
                    mime.startsWith("image/") -> "image"
                    mime.startsWith("video/") -> "video"
                    mime.startsWith("audio/") -> "audio"
                    else -> "file"
                }
                val r = MediaApi.upload(session.serverUrl, token, bytes, mime, name, kind)
                _state.update { it.copy(uploadBusy = false) }
                if (r.media != null) {
                    enqueueAndSend(text = "", media = r.media)
                }
            } catch (e: Exception) {
                _state.update { it.copy(uploadBusy = false) }
            }
        }
    }

    fun mediaUrl(id: String): String = MediaApi.mediaUrl(session.serverUrl, id)

    // ---- reactions ----
    fun react(messageId: String, emoji: String) {
        val ch = _state.value.currentChannel
        client?.send(
            JSONObject().put("type", "react").put("channel", ch)
                .put("messageId", messageId).put("emoji", emoji).toString()
        )
    }

    // ---- profile ----
    fun openProfile() {
        _state.update { it.copy(stage = Stage.Profile, profile = it.profile.copy(busy = true, error = null, notice = null)) }
        val token = session.token ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val r = AuthApi.getProfile(session.serverUrl, token)
            _state.update {
                it.copy(profile = if (r.ok) ProfileUi(
                    displayName = r.displayName ?: it.username,
                    handle = r.handle ?: "", premium = r.premium,
                    color = r.color ?: "5865F2", bio = r.bio ?: ""
                ) else it.profile.copy(busy = false, error = r.error))
            }
        }
    }
    fun closeProfile() { _state.update { it.copy(stage = Stage.Chats) } }

    private fun applyProfile(r: AuthApi.ProfileResult, notice: String?) {
        _state.update {
            if (r.ok) {
                session.username = r.displayName ?: it.username
                it.copy(
                    username = r.displayName ?: it.username,
                    profile = it.profile.copy(
                        displayName = r.displayName ?: it.profile.displayName,
                        handle = r.handle ?: it.profile.handle,
                        premium = r.premium, color = r.color ?: it.profile.color,
                        bio = r.bio ?: it.profile.bio,
                        busy = false, error = null, notice = notice
                    )
                )
            } else it.copy(profile = it.profile.copy(busy = false, error = r.error, notice = null))
        }
    }

    fun saveProfile(displayName: String, bio: String, color: String) {
        val token = session.token ?: return
        _state.update { it.copy(profile = it.profile.copy(busy = true, error = null, notice = null)) }
        viewModelScope.launch(Dispatchers.IO) {
            applyProfile(AuthApi.updateProfile(session.serverUrl, token, displayName, bio, color), "Profile saved")
        }
    }

    fun checkHandle(handle: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val st = AuthApi.checkHandle(session.serverUrl, handle)
            val msg = when {
                !st.valid -> st.reason ?: "invalid username"
                !st.available -> "@$handle is taken"
                st.premiumOnly -> "@$handle is available (Premium only)"
                else -> "@$handle is available"
            }
            _state.update { it.copy(profile = it.profile.copy(handleCheck = msg)) }
        }
    }

    fun claimHandle(handle: String) {
        val token = session.token ?: return
        _state.update { it.copy(profile = it.profile.copy(busy = true, error = null, notice = null)) }
        viewModelScope.launch(Dispatchers.IO) {
            applyProfile(AuthApi.claimHandle(session.serverUrl, token, handle), "Username claimed")
        }
    }

    fun claimPremium(code: String) {
        val token = session.token ?: return
        _state.update { it.copy(profile = it.profile.copy(busy = true, error = null, notice = null)) }
        viewModelScope.launch(Dispatchers.IO) {
            applyProfile(AuthApi.claimPremium(session.serverUrl, token, code), "Premium activated 🎉")
        }
    }

    fun lastPreview(ch: String): String {
        val m = _state.value.messagesByChannel[ch]?.lastOrNull() ?: return "Tap to start chatting"
        val text = if (m.text.isBlank() && m.media != null) "[" + mediaKindLabel(m.media.kind) + "] " + m.media.name else m.text
        return (m.username + ": " + text).replace("\n", " ").take(48)
    }

    // ---- e2ee room key ----
    fun hasCustomRoomKey(ch: String): Boolean = session.hasCustomRoomKey(ch)

    fun setRoomKey(key: String) {
        val ch = _state.value.currentChannel
        if (key.isBlank()) return
        session.setRoomKey(ch, key.trim())
        refreshCustomKeyFlags()
        // Locally cached plaintext was decrypted with the old key; wipe and
        // re-pull so history is re-decrypted with the new one.
        viewModelScope.launch(Dispatchers.IO) {
            messageDao.clearChannel(ch)
            client?.send(JSONObject().put("type", "history").put("channel", ch).toString())
        }
    }

    fun logout() {
        client?.close(); client = null
        reconnectJob?.cancel()
        session.clear()
        viewModelScope.launch(Dispatchers.IO) {
            for (ch in _state.value.channels) messageDao.clearChannel(ch)
            _state.value = ChatState()
        }
    }
}

// ---- mapping helpers ----
fun LocalMessage.toChatMessage(): ChatMessage = ChatMessage(
    id = id, channel = channel, userId = userId, username = username,
    handle = handle, premium = premium, color = color,
    text = text, ts = ts,
    media = mediaJson?.let { jsonToMediaRef(it) },
    replyTo = replyTo,
    reactions = reactionsJson?.let { jsonToReactions(it) } ?: emptyMap(),
    isPending = isPending, isFailed = isFailed
)

fun mediaRefToJson(m: MediaRef): JSONObject = JSONObject()
    .put("id", m.id).put("kind", m.kind).put("mime", m.mime)
    .put("name", m.name).put("size", m.size)
    .apply {
        m.width?.let { put("width", it) }
        m.height?.let { put("height", it) }
    }

fun jsonToMediaRef(s: String): MediaRef? = try {
    val o = JSONObject(s)
    MediaRef(
        id = o.getString("id"), kind = o.optString("kind", "file"),
        mime = o.optString("mime", "application/octet-stream"),
        name = o.optString("name", "file"), size = o.optLong("size"),
        width = if (o.has("width")) o.optInt("width") else null,
        height = if (o.has("height")) o.optInt("height") else null
    )
} catch (e: Exception) { null }

fun jsonToReactions(s: String): Map<String, List<String>> = try {
    val o = JSONObject(s)
    val out = mutableMapOf<String, List<String>>()
    for (k in o.keys()) {
        val arr = o.optJSONArray(k) ?: continue
        out[k] = (0 until arr.length()).map { arr.getString(it) }
    }
    out
} catch (e: Exception) { emptyMap() }

private fun mediaKindLabel(kind: String): String = when (kind) {
    "image" -> "photo"
    "video" -> "video"
    "audio" -> "voice"
    else -> "file"
}
