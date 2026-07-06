package dev.ryazha.sassist.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.ryazha.sassist.audio.VoicePlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.material3.DropdownMenu
import coil.compose.AsyncImage
import dev.ryazha.sassist.model.ChatMessage
import dev.ryazha.sassist.ui.theme.Blurple
import dev.ryazha.sassist.ui.theme.BgInput
import dev.ryazha.sassist.ui.theme.BgPanel
import dev.ryazha.sassist.ui.theme.CodeBg
import dev.ryazha.sassist.ui.theme.TextMuted
import dev.ryazha.sassist.ui.theme.TextPrimary
import dev.ryazha.sassist.ui.theme.TgAccent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface Block {
    data class Plain(val text: String) : Block
    data class Code(val lang: String, val code: String) : Block
}

private fun parseBlocks(text: String): List<Block> {
    val blocks = mutableListOf<Block>()
    val fence = "```"
    var idx = 0
    while (true) {
        val start = text.indexOf(fence, idx)
        if (start == -1) { if (idx < text.length) blocks.add(Block.Plain(text.substring(idx))); break }
        if (start > idx) blocks.add(Block.Plain(text.substring(idx, start)))
        val afterFence = start + 3
        val nl = text.indexOf('\n', afterFence)
        val end = text.indexOf(fence, afterFence)
        if (end == -1) { blocks.add(Block.Plain(text.substring(start))); break }
        val lang = if (nl != -1 && nl < end) text.substring(afterFence, nl).trim() else ""
        val codeStart = if (nl != -1 && nl < end) nl + 1 else afterFence
        val code = text.substring(codeStart, end).trimEnd('\n')
        blocks.add(Block.Code(lang.ifBlank { "code" }, code))
        idx = end + 3
    }
    return blocks
}

private fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c == '`') {
            val end = text.indexOf('`', i + 1)
            if (end != -1) {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = CodeBg, color = TgAccent)) {
                    append(text.substring(i + 1, end))
                }
                i = end + 1; continue
            }
        }
        if (c == '*' && i + 1 < text.length && text[i + 1] == '*') {
            val end = text.indexOf("**", i + 2)
            if (end != -1) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }
                i = end + 2; continue
            }
        }
        if (c == '*') {
            val end = text.indexOf('*', i + 1)
            if (end != -1) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                i = end + 1; continue
            }
        }
        append(c.toString()); i++
    }
}

private fun userColor(msg: ChatMessage): Color {
    val hex = msg.color.removePrefix("#")
    return try { Color(0xFF000000 or hex.toLong(16)) } catch (e: Exception) { Blurple }
}

val REACTION_CHOICES = listOf("👍", "❤️", "😂", "🔥", "👀")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageView(
    msg: ChatMessage,
    myUserId: String = "",
    mediaUrl: (String) -> String = { "" },
    findMessage: (String) -> ChatMessage? = { null },
    voiceState: VoicePlayer.PlayState = VoicePlayer.PlayState(),
    onToggleVoice: (String, String) -> Unit = { _, _ -> },
    nameOf: (String) -> String = { it },
    onReact: (String, String) -> Unit = { _, _ -> },
    onReply: (ChatMessage) -> Unit = {},
    onRetry: (String) -> Unit = {}
) {
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val time = remember(msg.ts) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.ts)) }
    var menuOpen by remember { mutableStateOf(false) }
    var videoUrl by remember { mutableStateOf<String?>(null) }
    var readersOpen by remember { mutableStateOf(false) }
    val mine = myUserId.isNotBlank() && msg.userId == myUserId
    val bubbleColor = if (mine) Blurple else BgPanel
    val bubbleShape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (mine) 18.dp else 4.dp,
        bottomEnd = if (mine) 4.dp else 18.dp
    )

    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .alpha(if (msg.isPending) 0.65f else 1f),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!mine) Avatar(msg)
        if (!mine) Spacer(Modifier.width(7.dp))
        Column(
            Modifier.widthIn(max = 310.dp)
                .clip(bubbleShape)
                .background(bubbleColor)
                .combinedClickable(onClick = {}, onLongClick = { menuOpen = true })
                .padding(horizontal = 10.dp, vertical = 7.dp)
        ) {
            if (!mine) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(msg.username, color = userColor(msg), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    if (msg.premium) Text(" ★", color = Color(0xFFFEE75C), fontSize = 12.sp)
                    if (msg.handle.isNotBlank()) Text(" @" + msg.handle, color = TextMuted, fontSize = 11.sp)
                }
            }

            msg.replyTo?.let { rid ->
                val original = findMessage(rid)
                Row(
                    Modifier.padding(top = 3.dp, bottom = 4.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (mine) Color.White.copy(alpha = 0.15f) else BgInput)
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Box(Modifier.width(3.dp).height(30.dp).clip(RoundedCornerShape(2.dp)).background(TgAccent))
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(original?.username ?: "…", color = TgAccent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text((original?.let { mediaPreviewText(it) } ?: "message unavailable").replace("\n", " ").take(64), color = if (mine) TextPrimary.copy(alpha = 0.8f) else TextMuted, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }

            msg.media?.let { media ->
                val url = mediaUrl(media.id)
                when (media.kind) {
                    "image" -> AsyncImage(
                        model = url,
                        contentDescription = media.name,
                        modifier = Modifier.padding(top = 4.dp).widthIn(max = 280.dp).heightIn(max = 340.dp)
                            .clip(RoundedCornerShape(12.dp)).clickable { uriHandler.openUri(url) }
                    )
                    "audio" -> VoiceBubble(
                        mine = mine,
                        durationMs = media.durationMs ?: 0L,
                        playing = voiceState.messageId == msg.id && voiceState.playing,
                        progress = if (voiceState.messageId == msg.id) voiceState.progress else 0f,
                        onToggle = { onToggleVoice(msg.id, url) }
                    )
                    "video" -> Box(
                        Modifier.padding(top = 4.dp).widthIn(max = 280.dp).height(170.dp)
                            .clip(RoundedCornerShape(12.dp)).background(Color.Black)
                            .clickable { videoUrl = url },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.size(52.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Play video", tint = Color.White, modifier = Modifier.size(34.dp))
                        }
                        media.durationMs?.let {
                            Text(formatDuration(it), color = Color.White, fontSize = 11.sp,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                                    .clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 5.dp, vertical = 1.dp))
                        }
                    }
                    else -> Row(
                        Modifier.padding(top = 4.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (mine) Color.White.copy(alpha = 0.14f) else BgInput)
                            .clickable { uriHandler.openUri(url) }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.InsertDriveFile, contentDescription = null, tint = TgAccent, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f, fill = false)) {
                            Text(media.name, color = TextPrimary, fontSize = 13.sp, maxLines = 1)
                            Text(formatSize(media.size) + " · " + mediaKindLabel(media.kind) + " · tap to open/download", color = if (mine) TextPrimary.copy(alpha = 0.75f) else TextMuted, fontSize = 11.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Filled.Download, contentDescription = "Download", tint = TextMuted, modifier = Modifier.size(18.dp))
                    }
                }
            }

            parseBlocks(msg.text).forEach { block ->
                when (block) {
                    is Block.Plain -> block.text.trim('\n').takeIf { it.isNotBlank() }?.let { Text(inlineMarkdown(it), color = TextPrimary, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp)) }
                    is Block.Code -> CodeBlock(block.lang, block.code) { clipboard.setText(AnnotatedString(block.code)) }
                }
            }

            Row(Modifier.align(Alignment.End).padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                if (msg.isFailed) {
                    Row(Modifier.clickable { onRetry(msg.id) }, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = Color(0xFFED4245), modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("retry", color = Color(0xFFED4245), fontSize = 11.sp)
                    }
                    Spacer(Modifier.width(6.dp))
                }
                Text(time, color = if (mine) TextPrimary.copy(alpha = 0.72f) else TextMuted, fontSize = 10.sp)
                if (mine) {
                    Spacer(Modifier.width(3.dp))
                    // ◷ queued, ✓ delivered (nobody read yet), ✓✓ read by someone.
                    val read = msg.readBy.isNotEmpty()
                    Text(
                        if (msg.isPending) "◷" else if (read) "✓✓" else "✓",
                        color = if (msg.isPending) TextPrimary.copy(alpha = 0.6f) else if (read) Onlineish else TextPrimary.copy(alpha = 0.72f),
                        fontSize = 11.sp,
                        modifier = if (read) Modifier.clickable { readersOpen = true } else Modifier
                    )
                }
            }

            if (msg.reactions.isNotEmpty()) {
                Row(Modifier.padding(top = 4.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    msg.reactions.forEach { (emoji, users) ->
                        val reactedByMe = myUserId.isNotBlank() && users.contains(myUserId)
                        Row(Modifier.clip(RoundedCornerShape(10.dp)).background(if (reactedByMe) TgAccent.copy(alpha = 0.35f) else BgInput).clickable { onReact(msg.id, emoji) }.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(emoji, fontSize = 12.sp)
                            Spacer(Modifier.width(3.dp))
                            Text(users.size.toString(), color = TextPrimary, fontSize = 11.sp)
                        }
                    }
                }
            }

            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    REACTION_CHOICES.forEach { emoji -> Text(emoji, fontSize = 20.sp, modifier = Modifier.clickable { menuOpen = false; onReact(msg.id, emoji) }) }
                    Text("↩ Reply", color = TgAccent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { menuOpen = false; onReply(msg) })
                }
            }
        }
    }

    // Fullscreen video player
    videoUrl?.let { vurl ->
        Dialog(onDismissRequest = { videoUrl = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black).clickable { videoUrl = null }, contentAlignment = Alignment.Center) {
                AndroidView(factory = { ctx ->
                    android.widget.VideoView(ctx).apply {
                        setVideoURI(android.net.Uri.parse(vurl))
                        val mc = android.widget.MediaController(ctx)
                        mc.setAnchorView(this)
                        setMediaController(mc)
                        setOnPreparedListener { it.start() }
                    }
                }, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    // Who read this message
    if (readersOpen) {
        Dialog(onDismissRequest = { readersOpen = false }) {
            Column(Modifier.clip(RoundedCornerShape(14.dp)).background(BgPanel).padding(16.dp)) {
                Text("Read by", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                if (msg.readBy.isEmpty()) Text("No one yet", color = TextMuted, fontSize = 13.sp)
                else msg.readBy.forEach { uid ->
                    Text("✓✓ " + nameOf(uid), color = TextPrimary, fontSize = 14.sp, modifier = Modifier.padding(vertical = 3.dp))
                }
            }
        }
    }
}

@Composable
private fun VoiceBubble(mine: Boolean, durationMs: Long, playing: Boolean, progress: Float, onToggle: () -> Unit) {
    Row(
        Modifier.padding(top = 4.dp).widthIn(min = 180.dp, max = 260.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (mine) Color.White.copy(alpha = 0.14f) else BgInput)
            .clickable { onToggle() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(TgAccent), contentAlignment = Alignment.Center) {
            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (playing) "Pause" else "Play", tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            LinearProgressIndicator(
                progress = { if (playing || progress > 0f) progress else 0f },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = TgAccent, trackColor = if (mine) Color.White.copy(alpha = 0.25f) else BgPanel
            )
            Spacer(Modifier.height(4.dp))
            Text(formatDuration(durationMs), color = if (mine) TextPrimary.copy(alpha = 0.8f) else TextMuted, fontSize = 11.sp)
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).toInt()
    return String.format(Locale.US, "%d:%02d", totalSec / 60, totalSec % 60)
}

@Composable
private fun Avatar(msg: ChatMessage) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(userColor(msg)),
        contentAlignment = Alignment.Center
    ) {
        Text(msg.username.trim().take(1).ifBlank { "?" }.uppercase(Locale.getDefault()), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

private val Onlineish = Color(0xFF5AD1FF)

private fun mediaPreviewText(msg: ChatMessage): String =
    if (msg.text.isBlank() && msg.media != null) "[" + mediaKindLabel(msg.media.kind) + "] " + msg.media.name else msg.text

private fun mediaKindLabel(kind: String): String = when (kind) {
    "image" -> "photo"
    "video" -> "video"
    "audio" -> "voice"
    else -> "file"
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
    bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

@Composable
private fun CodeBlock(lang: String, code: String, onCopy: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 4.dp)
            .clip(RoundedCornerShape(8.dp)).background(CodeBg)
    ) {
        Row(
            Modifier.fillMaxWidth().background(BgInput).padding(start = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(lang, color = TgAccent, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            IconButton(onClick = onCopy) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "copy", tint = TextMuted)
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(10.dp)) {
            Text(CodeHighlight.highlight(code), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        }
    }
}
