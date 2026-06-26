package dev.ryazha.sassist.ui

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
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ryazha.sassist.model.ChatMessage
import dev.ryazha.sassist.model.CHANNEL_META
import dev.ryazha.sassist.ui.theme.*

@Composable
fun ChatScreen(
    channels: List<String>,
    currentChannel: String,
    messages: List<ChatMessage>,
    presence: Int,
    codeMode: Boolean,
    e2ee: Boolean,
    onChannel: (String) -> Unit,
    onToggleCode: () -> Unit,
    onSend: (String) -> Unit,
    onOpenScripts: () -> Unit,
    onBack: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val title = CHANNEL_META[currentChannel]?.title ?: currentChannel

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
                    Modifier.clip(RoundedCornerShape(8.dp)).background(BgPanel).padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = "Encrypted", tint = OnlineGreen, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("E2EE", color = OnlineGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(4.dp))
            }
            IconButton(onClick = onOpenScripts) {
                Icon(Icons.Filled.Terminal, contentDescription = "Scripts", tint = TextPrimary)
            }
        }

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
                Box(Modifier.animateItem()) { MessageView(msg) }
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
            OutlinedTextField(
                value = input, onValueChange = { input = it },
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
}
