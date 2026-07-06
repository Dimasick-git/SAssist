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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ryazha.sassist.model.ChatMessage
import dev.ryazha.sassist.model.CHANNEL_META
import dev.ryazha.sassist.model.ConnState
import dev.ryazha.sassist.ui.theme.*

@Composable
fun ConnBanner(connState: ConnState) {
    if (connState == ConnState.Connected) return
    val (text, color) = when (connState) {
        ConnState.Connecting -> "Connecting…" to Color(0xFFFAA61A)
        else -> "Offline — messages will send when back online" to TextMuted
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
    mediaUrl: (String) -> String,
    onChannel: (String) -> Unit,
    onToggleCode: () -> Unit,
    onSend: (String) -> Unit,
    onSendMedia: (Uri) -> Unit,
    onTyping: () -> Unit,
    onReact: (String, String) -> Unit,
    onReply: (ChatMessage) -> Unit,
    onCancelReply: () -> Unit,
    onRetry: (String) -> Unit,
    onSetRoomKey: (String) -> Unit,
    onOpenScripts: () -> Unit,
    onBack: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var keyDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val title = CHANNEL_META[currentChannel]?.title ?: currentChannel
    val byId = remember(messages) { messages.associateBy { it.id } }

    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onSendMedia(uri)
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onSendMedia(uri)
    }
    val pickVoice = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onSendMedia(uri)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text("# " + title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(presence.toString() + " online", color = OnlineGreen, fontSize = 11.sp)
            }
            if (e2ee) {
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(BgPanel)
                        .clickable { keyDialog = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val keyColor = if (hasCustomKey) OnlineGreen else Color(0xFFFAA61A)
                    Icon(Icons.Filled.Lock, contentDescription = "Encrypted", tint = keyColor, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("E2EE", color = keyColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(4.dp))
            }
            IconButton(onClick = onOpenScripts) {
                Icon(Icons.Filled.Terminal, contentDescription = "Scripts", tint = TextPrimary)
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
                    Text((meta?.emoji ?: "#") + " " + (meta?.title ?: ch), color = if (sel) TextPrimary else TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
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
                        onReact = onReact, onReply = onReply, onRetry = onRetry
                    )
                }
            }
        }

        if (typingUser != null) {
            Text(
                text = "$typingUser is typing...",
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
                    Text("Reply to " + replyingTo.username, color = TgAccent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        (if (replyingTo.text.isBlank() && replyingTo.media != null) "[" + replyingTo.media.kind + "]" else replyingTo.text)
                            .replace("\n", " ").take(64),
                        color = TextMuted, fontSize = 11.sp, maxLines = 1
                    )
                }
                IconButton(onClick = onCancelReply) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel reply", tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }
        }

        // Input
        Row(
            Modifier.fillMaxWidth().background(BgDark).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleCode) {
                Icon(Icons.Filled.Code, contentDescription = "Code mode", tint = if (codeMode) TgAccent else TextMuted)
            }
            IconButton(
                onClick = {
                    pickMedia.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                },
                enabled = !uploadBusy
            ) {
                if (uploadBusy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = TgAccent)
                else Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "Attach photo/video", tint = TextMuted)
            }
            IconButton(onClick = { pickVoice.launch("audio/*") }, enabled = !uploadBusy) {
                Icon(Icons.Filled.Mic, contentDescription = "Attach voice", tint = TextMuted)
            }
            IconButton(onClick = { pickFile.launch("*/*") }, enabled = !uploadBusy) {
                Icon(Icons.Filled.AttachFile, contentDescription = "Attach file", tint = TextMuted)
            }
            OutlinedTextField(
                value = input, onValueChange = {
                    input = it
                    if (it.isNotEmpty()) onTyping()
                },
                placeholder = { Text(if (codeMode) "Paste code…" else "Message…", color = TextMuted) },
                modifier = Modifier.weight(1f), colors = tf, shape = RoundedCornerShape(20.dp),
                maxLines = if (codeMode) 6 else 4,
                keyboardOptions = KeyboardOptions(autoCorrect = !codeMode)
            )
            Spacer(Modifier.width(6.dp))
            IconButton(
                onClick = {
                    val t = input.trim()
                    if (t.isNotEmpty()) {
                        val payload = if (codeMode) "\n```\n" + t + "\n```\n" else t
                        onSend(payload); input = ""
                    }
                }
            ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Blurple) }
        }
    }

    // E2EE room key dialog
    if (keyDialog) {
        var key by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { keyDialog = false },
            containerColor = BgDark,
            title = { Text("Room encryption key", color = TextPrimary) },
            text = {
                Column {
                    if (!hasCustomKey) {
                        Text(
                            "This room uses the default key — anyone on this server can read it. Set a passphrase and share it with your chat partners off-band.",
                            color = Color(0xFFFAA61A), fontSize = 12.sp
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    OutlinedTextField(
                        value = key, onValueChange = { key = it },
                        placeholder = { Text("Passphrase for #$currentChannel", color = TextMuted) },
                        colors = tf, shape = RoundedCornerShape(12.dp), singleLine = true
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Changing the key clears the local cache and re-syncs history.",
                        color = TextMuted, fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (key.isNotBlank()) { onSetRoomKey(key); keyDialog = false }
                }) { Text("Set key", color = TgAccent) }
            },
            dismissButton = {
                TextButton(onClick = { keyDialog = false }) { Text("Cancel", color = TextMuted) }
            }
        )
    }
}
