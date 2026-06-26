package dev.ryazha.sassist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ryazha.sassist.model.CHANNEL_META
import dev.ryazha.sassist.model.ChannelMeta
import dev.ryazha.sassist.model.ConnState
import dev.ryazha.sassist.ui.theme.*

@Composable
fun ChatsListScreen(
    username: String,
    channels: List<String>,
    connState: ConnState,
    presence: Map<String, Int>,
    preview: (String) -> String,
    onOpen: (String) -> Unit,
    onScripts: () -> Unit,
    onLogout: () -> Unit,
    onServer: (String) -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    var serverDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(BgDarkest)) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().background(BgDark).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Blurple, TgAccent))),
                contentAlignment = Alignment.Center
            ) {
                Text(initials(username), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("SAssist", color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Black)
                Text(connLabel(connState), color = if (connState == ConnState.Connected) OnlineGreen else TextMuted, fontSize = 12.sp)
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Menu", tint = TextPrimary)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Scripts") }, onClick = { menu = false; onScripts() })
                    DropdownMenuItem(text = { Text("Change server URL") }, onClick = { menu = false; serverDialog = true })
                    DropdownMenuItem(text = { Text("Log out") }, onClick = { menu = false; onLogout() })
                }
            }
        }

        Text(
            "  Channels", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp)
        )

        LazyColumn(Modifier.fillMaxSize()) {
            items(channels) { ch ->
                val meta = CHANNEL_META[ch] ?: ChannelMeta(ch, ch.replaceFirstChar { it.uppercase() }, "Channel", "#")
                ChannelRow(meta, preview(ch), presence[ch] ?: 0) { onOpen(ch) }
            }
        }
    }

    if (serverDialog) {
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { serverDialog = false },
            confirmButton = { TextButton(onClick = { onServer(url); serverDialog = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { serverDialog = false }) { Text("Cancel") } },
            title = { Text("Server URL") },
            text = {
                OutlinedTextField(value = url, onValueChange = { url = it }, singleLine = true,
                    placeholder = { Text("ws://10.0.2.2:8080") })
            },
            containerColor = BgPanel
        )
    }
}

@Composable
private fun ChannelRow(meta: ChannelMeta, preview: String, online: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(Blurple, TgAccent))),
            contentAlignment = Alignment.Center
        ) { Text(meta.emoji, fontSize = 24.sp) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(meta.title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(preview, color = TextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (online > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(OnlineGreen))
                Spacer(Modifier.width(5.dp))
                Text(online.toString(), color = OnlineGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    Divider(color = BgPanel, thickness = 0.5.dp, modifier = Modifier.padding(start = 82.dp))
}

private fun initials(name: String): String {
    val n = name.trim()
    if (n.isEmpty()) return "U"
    val parts = n.split(" ", "@", ".").filter { it.isNotBlank() }
    return (parts.firstOrNull()?.take(1) ?: "U").uppercase()
}

private fun connLabel(s: ConnState): String = when (s) {
    ConnState.Connected -> "● online"
    ConnState.Connecting -> "connecting…"
    ConnState.Error -> "connection error"
    ConnState.Disconnected -> "offline"
}
