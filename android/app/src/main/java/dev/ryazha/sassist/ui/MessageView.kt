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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
    onReact: (String, String) -> Unit = { _, _ -> },
    onReply: (ChatMessage) -> Unit = {},
    onRetry: (String) -> Unit = {}
) {
    val clipboard = LocalClipboardManager.current
    val time = remember(msg.ts) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.ts)) }
    var menuOpen by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = { menuOpen = true })
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .alpha(if (msg.isPending) 0.6f else 1f)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(msg.username, color = userColor(msg), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (msg.premium) Text(" ★", color = Color(0xFFFEE75C), fontSize = 12.sp)
            if (msg.handle.isNotBlank()) Text(" @" + msg.handle, color = TextMuted, fontSize = 11.sp)
            Text("  $time", color = TextMuted, fontSize = 11.sp)
            if (msg.isPending) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.Schedule, contentDescription = "Sending", tint = TextMuted, modifier = Modifier.size(12.dp))
            }
        }

        // Quoted reply
        msg.replyTo?.let { rid ->
            val original = findMessage(rid)
            Row(
                Modifier.padding(top = 2.dp).clip(RoundedCornerShape(6.dp)).background(BgPanel)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Box(Modifier.width(3.dp).height(28.dp).clip(RoundedCornerShape(2.dp)).background(TgAccent))
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(original?.username ?: "…", color = TgAccent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        (original?.let { if (it.text.isBlank() && it.media != null) "[" + it.media.kind + "]" else it.text } ?: "message unavailable")
                            .replace("\n", " ").take(64),
                        color = TextMuted, fontSize = 11.sp, maxLines = 1
                    )
                }
            }
        }

        // Media
        msg.media?.let { media ->
            if (media.kind == "image") {
                AsyncImage(
                    model = mediaUrl(media.id),
                    contentDescription = media.name,
                    modifier = Modifier.padding(top = 4.dp)
                        .widthIn(max = 260.dp).heightIn(max = 320.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                Row(
                    Modifier.padding(top = 4.dp).clip(RoundedCornerShape(8.dp)).background(BgPanel)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.InsertDriveFile, contentDescription = null, tint = TgAccent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(media.name, color = TextPrimary, fontSize = 13.sp, maxLines = 1)
                        Text(formatSize(media.size) + " · " + media.kind, color = TextMuted, fontSize = 11.sp)
                    }
                }
            }
        }

        parseBlocks(msg.text).forEach { block ->
            when (block) {
                is Block.Plain -> {
                    val t = block.text.trim('\n')
                    if (t.isNotBlank())
                        Text(inlineMarkdown(t), color = TextPrimary, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp))
                }
                is Block.Code -> CodeBlock(block.lang, block.code) {
                    clipboard.setText(AnnotatedString(block.code))
                }
            }
        }

        // Failed status with tap-to-retry
        if (msg.isFailed) {
            Row(
                Modifier.padding(top = 2.dp).clickable { onRetry(msg.id) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = Color(0xFFED4245), modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text("Not sent — tap to retry", color = Color(0xFFED4245), fontSize = 11.sp)
            }
        }

        // Reaction chips
        if (msg.reactions.isNotEmpty()) {
            Row(
                Modifier.padding(top = 4.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                msg.reactions.forEach { (emoji, users) ->
                    val mine = myUserId.isNotBlank() && users.contains(myUserId)
                    Row(
                        Modifier.clip(RoundedCornerShape(10.dp))
                            .background(if (mine) Blurple.copy(alpha = 0.35f) else BgPanel)
                            .clickable { onReact(msg.id, emoji) }
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(emoji, fontSize = 12.sp)
                        Spacer(Modifier.width(3.dp))
                        Text(users.size.toString(), color = if (mine) TextPrimary else TextMuted, fontSize = 11.sp)
                    }
                }
            }
        }

        // Long-press action menu: react or reply
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                REACTION_CHOICES.forEach { emoji ->
                    Text(emoji, fontSize = 20.sp, modifier = Modifier.clickable {
                        menuOpen = false
                        onReact(msg.id, emoji)
                    })
                }
                Text("↩ Reply", color = TgAccent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        menuOpen = false
                        onReply(msg)
                    })
            }
        }
    }
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
