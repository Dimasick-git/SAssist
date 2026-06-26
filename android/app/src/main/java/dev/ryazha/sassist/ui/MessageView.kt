package dev.ryazha.sassist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import dev.ryazha.sassist.model.ChatMessage
import dev.ryazha.sassist.ui.theme.Blurple
import dev.ryazha.sassist.ui.theme.BgInput
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
    val fence = "\u0060\u0060\u0060"
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
        if (c == '\u0060') {
            val end = text.indexOf('\u0060', i + 1)
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

private fun avatarColor(name: String): Color {
    val palette = listOf(Blurple, TgAccent, Color(0xFFEB459E), Color(0xFFFEE75C), Color(0xFF57F287), Color(0xFFED4245))
    return palette[(name.hashCode() and 0x7fffffff) % palette.size]
}

@Composable
fun MessageView(msg: ChatMessage) {
    val clipboard = LocalClipboardManager.current
    val time = remember(msg.ts) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.ts)) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(msg.username, color = avatarColor(msg.username), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("  $time", color = TextMuted, fontSize = 11.sp)
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
    }
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
