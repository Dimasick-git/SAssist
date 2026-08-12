package dev.ryazha.sassist.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import dev.ryazha.sassist.audio.VoicePlayer
import dev.ryazha.sassist.model.ChatMessage
import dev.ryazha.sassist.model.CHANNEL_META
import dev.ryazha.sassist.model.ConnState
import dev.ryazha.sassist.ui.theme.*

private fun formatMillis(ms: Long): String {
    val s = (ms / 1000).toInt()
    return String.format(java.util.Locale.US, "%d:%02d", s / 60, s % 60)
}

@Composable
fun ConnBanner(connState: ConnState) {
    if (connState == ConnState.Connected) return
    val (text, color) = when (connState) {
        ConnState.Connecting -> tr("Подключение…", "Connecting…") to Color(0xFFFAA61A)
        else -> tr("Не в сети — сообщения отправятся после подключения", "Offline — messages will send when back online") to TextMuted
    }
    Box(
        Modifier.fillMaxWidth().background(BgPanel).padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ChatScreen(
    channels: List<String>,
    currentChannel: String,
    messages: List<ChatMessage>,
    presence: Int,
    typingUser: String?,
    codeMode: Boolean,
    e2ee: Boolean,
    connState: ConnState,
    myUserId: String,
    replyingTo: ChatMessage?,
    uploadBusy: Boolean,
    hasCustomKey: Boolean,
    recording: Boolean,
    recordingStartedAt: Long,
    voiceState: VoicePlayer.PlayState,
    mediaUrl: (String) -> String,
    nameOf: (String) -> String,
    onChannel: (String) -> Unit,
    onToggleCode: () -> Unit,
    onSend: (String) -> Unit,
    onSendMedia: (Uri) -> Unit,
    onTyping: () -> Unit,
    onReact: (String, String) -> Unit,
    onReply: (ChatMessage) -> Unit,
    onOpenUserProfile: (ChatMessage) -> Unit,
    onCancelReply: () -> Unit,
    onRetry: (String) -> Unit,
    onToggleVoice: (String, String) -> Unit,
    onStartVoice: () -> Unit,
    onStopVoice: () -> Unit,
    onCancelVoice: () -> Unit,
    onMarkRead: () -> Unit,
    onSetRoomKey: (String) -> Unit,
    onOpenScripts: () -> Unit,
    onBack: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var keyDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val isDirect = currentChannel.startsWith("dm:")
    val title = if (isDirect) tr("Личный чат", "Direct message") else (CHANNEL_META[currentChannel]?.title ?: currentChannel)
    val byId = remember(messages) { messages.associateBy { it.id } }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onSendMedia(uri)
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onSendMedia(uri)
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    var hasAudioPerm by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val audioPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasAudioPerm = granted
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
        onMarkRead()
    }

    val tf = TextFieldDefaults.colors(
        focusedContainerColor = BgInput, unfocusedContainerColor = BgInput,
        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
        cursorColor = Blurple, focusedIndicatorColor = BgInput, unfocusedIndicatorColor = BgInput
    )

    Column(Modifier.fillMaxSize().background(BgDarkest)) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().background(BgDark).padding(horizontal = 6.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Назад", "Back"), tint = TextPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text("# " + title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(presence.toString() + tr(" в сети", " online"), color = OnlineGreen, fontSize = 11.sp)
            }
            if (e2ee) {
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(BgPanel)
                        .clickable { keyDialog = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val keyColor = if (hasCustomKey) OnlineGreen else Color(0xFFFAA61A)
                    Icon(Icons.Filled.Lock, contentDescription = tr("Зашифровано", "Encrypted"), tint = keyColor, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("E2EE", color = keyColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(4.dp))
            }
            IconButton(onClick = onOpenScripts) {
                Icon(Icons.Filled.Terminal, contentDescription = tr("Скрипты", "Scripts"), tint = TextPrimary)
            }
        }

        ConnBanner(connState)

        // Channel rail
        Row(
            Modifier.fillMaxWidth().background(BgDark).horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            channels.forEach { ch ->
                val sel = ch == currentChannel
                val meta = CHANNEL_META[ch]
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp))
                        .background(if (sel) Brush.linearGradient(listOf(Blurple, TgAccent)) else Brush.linearGradient(listOf(BgPanel, BgPanel)))
                        .clickable { onChannel(ch) }.padding(horizontal = 12.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val label = if (ch.startsWith("dm:")) "✉ " + tr("Личный", "Direct") else ((meta?.emoji ?: "#") + " " + (meta?.title ?: ch))
                    Text(label, color = if (sel) TextPrimary else TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (isDirect && !hasCustomKey) {
            Text(
                tr("Установите одинаковый ключ комнаты на обоих устройствах, чтобы зашифровать этот личный чат.", "Set the same room key on both devices to encrypt this direct conversation."),
                color = Color(0xFFFAA61A), fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth().background(BgPanel).padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                Box(Modifier.animateItem()) {
                    MessageView(
                        msg = msg, myUserId = myUserId, mediaUrl = mediaUrl,
                        findMessage = { byId[it] },
                        voiceState = voiceState, onToggleVoice = onToggleVoice, nameOf = nameOf,
                        onReact = onReact, onReply = onReply, onRetry = onRetry, onOpenProfile = onOpenUserProfile
                    )
                }
            }
        }

        if (typingUser != null) {
            Text(
                text = typingUser + tr(" печатает…", " is typing..."),
                color = OnlineGreen,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // Reply strip
        if (replyingTo != null) {
            Row(
                Modifier.fillMaxWidth().background(BgPanel).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(3.dp).height(30.dp).clip(RoundedCornerShape(2.dp)).background(TgAccent))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("Ответ для ", "Reply to ") + replyingTo.username, color = TgAccent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        (if (replyingTo.text.isBlank() && replyingTo.media != null) "[" + replyingTo.media.kind + "]" else replyingTo.text)
                            .replace("\n", " ").take(64),
                        color = TextMuted, fontSize = 11.sp, maxLines = 1
                    )
                }
                IconButton(onClick = onCancelReply) {
                    Icon(Icons.Filled.Close, contentDescription = tr("Отменить ответ", "Cancel reply"), tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }
        }

        // Recording indicator
        if (recording) {
            var elapsed by remember { mutableStateOf(0L) }
            LaunchedEffect(recordingStartedAt) {
                while (true) { elapsed = System.currentTimeMillis() - recordingStartedAt; delay(200) }
            }
            Row(
                Modifier.fillMaxWidth().background(Color(0xFF3A1E1E)).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFED4245)))
                Spacer(Modifier.width(10.dp))
                Text(tr("Запись… ", "Recording… ") + formatMillis(elapsed), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(tr("отпустите для отправки · смахните для отмены", "release to send · slide off to cancel"), color = TextMuted, fontSize = 11.sp)
            }
        }

        // Input
        Row(
            Modifier.fillMaxWidth().background(BgDark).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleCode) {
                Icon(Icons.Filled.Code, contentDescription = tr("Режим кода", "Code mode"), tint = if (codeMode) TgAccent else TextMuted)
            }
            IconButton(
                onClick = {
                    pickMedia.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                },
                enabled = !uploadBusy
            ) {
                if (uploadBusy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = TgAccent)
                else Icon(Icons.Filled.AddPhotoAlternate, contentDescription = tr("Прикрепить фото или видео", "Attach photo/video"), tint = TextMuted)
            }
            IconButton(onClick = { pickFile.launch("*/*") }, enabled = !uploadBusy) {
                Icon(Icons.Filled.AttachFile, contentDescription = tr("Прикрепить файл", "Attach file"), tint = TextMuted)
            }
            OutlinedTextField(
                value = input, onValueChange = {
                    input = it
                    if (it.isNotEmpty()) onTyping()
                },
                placeholder = { Text(if (codeMode) tr("Вставьте код…", "Paste code…") else tr("Сообщение…", "Message…"), color = TextMuted) },
                modifier = Modifier.weight(1f), colors = tf, shape = RoundedCornerShape(20.dp),
                maxLines = if (codeMode) 6 else 4,
                keyboardOptions = KeyboardOptions(autoCorrect = !codeMode)
            )
            Spacer(Modifier.width(6.dp))
            if (input.isBlank()) {
                // Press-and-hold to record a voice message (Telegram-style).
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(23.dp))
                        .background(if (recording) Color(0xFFED4245) else Blurple)
                        .pointerInput(uploadBusy) {
                            detectTapGestures(
                                onPress = {
                                    if (uploadBusy) return@detectTapGestures
                                    if (!hasAudioPerm) {
                                        audioPermLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                        return@detectTapGestures
                                    }
                                    onStartVoice()
                                    val released = tryAwaitRelease()
                                    if (released) onStopVoice() else onCancelVoice()
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = tr("Удерживайте для записи голоса", "Hold to record voice"), tint = Color.White)
                }
            } else {
                IconButton(
                    onClick = {
                        val t = input.trim()
                        if (t.isNotEmpty()) {
                            val payload = if (codeMode) "\n```\n" + t + "\n```\n" else t
                            onSend(payload); input = ""
                        }
                    }
                ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = tr("Отправить", "Send"), tint = Blurple) }
            }
        }
    }

    // E2EE room key dialog
    if (keyDialog) {
        var key by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { keyDialog = false },
            containerColor = BgDark,
            title = { Text(tr("Ключ шифрования комнаты", "Room encryption key"), color = TextPrimary) },
            text = {
                Column {
                    if (!hasCustomKey) {
                        Text(
                            tr("В этой комнате используется стандартный ключ — её может прочитать любой пользователь сервера. Установите пароль и передайте его собеседникам другим способом.", "This room uses the default key — anyone on this server can read it. Set a passphrase and share it with your chat partners off-band."),
                            color = Color(0xFFFAA61A), fontSize = 12.sp
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    OutlinedTextField(
                        value = key, onValueChange = { key = it },
                        placeholder = { Text(tr("Пароль для #", "Passphrase for #") + currentChannel, color = TextMuted) },
                        colors = tf, shape = RoundedCornerShape(12.dp), singleLine = true
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tr("При смене ключа локальный кэш очистится, а история загрузится заново.", "Changing the key clears the local cache and re-syncs history."),
                        color = TextMuted, fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (key.isNotBlank()) { onSetRoomKey(key); keyDialog = false }
                }) { Text(tr("Установить ключ", "Set key"), color = TgAccent) }
            },
            dismissButton = {
                TextButton(onClick = { keyDialog = false }) { Text(tr("Отмена", "Cancel"), color = TextMuted) }
            }
        )
    }
}
