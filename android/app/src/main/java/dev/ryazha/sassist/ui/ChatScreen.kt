package dev.ryazha.sassist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ryazha.sassist.model.ChatMessage
import dev.ryazha.sassist.ui.theme.BgDark
import dev.ryazha.sassist.ui.theme.BgDarkest
import dev.ryazha.sassist.ui.theme.BgInput
import dev.ryazha.sassist.ui.theme.BgPanel
import dev.ryazha.sassist.ui.theme.Blurple
import dev.ryazha.sassist.ui.theme.OnlineGreen
import dev.ryazha.sassist.ui.theme.TextMuted
import dev.ryazha.sassist.ui.theme.TextPrimary

@Composable
fun ChatScreen(
    channels: List<String>,
    currentChannel: String,
    messages: List<ChatMessage>,
    presence: Int,
    codeMode: Boolean,
    onChannel: (String) -> Unit,
    onToggleCode: () -> Unit,
    onSend: (String) -> Unit,
    onOpenScripts: () -> Unit,
    onDisconnect: () -> Unit
) {
    Row(Modifier.fillMaxSize().background(BgDark)) {
        // ----- channel rail -----
        Column(
            Modifier.fillMaxHeight().width(96.dp).background(BgDarkest).padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("CHANNELS", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            channels.forEach { ch ->
                val active = ch == currentChannel
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) Blurple else Color.Transparent)
                        .clickable { onChannel(ch) }
                        .padding(vertical = 8.dp, horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "#" + ch, color = if (active) Color.White else TextMuted,
                        fontSize = 11.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }

        // ----- main column -----
        Column(Modifier.fillMaxSize()) {
            // top bar
            Row(
                Modifier.fillMaxWidth().background(BgPanel).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("#", color = TextMuted, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(4.dp))
                Text(currentChannel, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.size(8.dp).clip(CircleShape).background(OnlineGreen))
                Spacer(Modifier.width(4.dp))
                Text("$presence online", color = TextMuted, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onOpenScripts) {
                    Icon(Icons.Filled.Terminal, contentDescription = "Scripts", tint = TextMuted)
                }
                IconButton(onClick = onDisconnect) {
                    Icon(Icons.Filled.Logout, contentDescription = "Disconnect", tint = TextMuted)
                }
            }

            // messages
            val listState = rememberLazyListState()
            LaunchedEffect(messages.size) {
                if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(messages, key = { it.id.ifEmpty { it.ts.toString() + it.username } }) { msg ->
                    MessageView(msg)
                }
            }

            // input bar
            InputBar(codeMode = codeMode, onToggleCode = onToggleCode, onSend = onSend)
        }
    }
}

@Composable
private fun InputBar(codeMode: Boolean, onToggleCode: () -> Unit, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(
        Modifier.fillMaxWidth().background(BgPanel).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // code-mode toggle
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))
                .background(if (codeMode) Blurple else BgInput)
                .clickable { onToggleCode() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Code, contentDescription = "Code mode",
                tint = if (codeMode) Color.White else TextMuted)
        }

        TextField(
            value = text, onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(if (codeMode) "paste code…  (sent as ```block```)" else "Message",
                    color = TextMuted)
            },
            textStyle = androidx.compose.ui.text.TextStyle(
                color = TextPrimary,
                fontFamily = if (codeMode) FontFamily.Monospace else FontFamily.Default,
                fontSize = 15.sp
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = !codeMode,
            keyboardOptions = KeyboardOptions.Default,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = BgInput, unfocusedContainerColor = BgInput,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                cursorColor = Blurple
            )
        )

        val send = {
            val t = text.trim()
            if (t.isNotEmpty()) {
                val payload = if (codeMode) "```\n" + text + "\n```" else t
                onSend(payload)
                text = ""
            }
        }
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(Blurple).clickable { send() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
        }
    }
}
