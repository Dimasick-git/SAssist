package dev.ryazha.sassist.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import dev.ryazha.sassist.ui.theme.CodeComment
import dev.ryazha.sassist.ui.theme.CodeKeyword
import dev.ryazha.sassist.ui.theme.CodeNumber
import dev.ryazha.sassist.ui.theme.CodeString
import dev.ryazha.sassist.ui.theme.TextPrimary

/** Tiny multi-language syntax highlighter producing an AnnotatedString. */
object CodeHighlight {

    private val keywords = setOf(
        "fun","val","var","class","object","interface","return","if","else","when",
        "for","while","do","import","package","private","public","internal","override",
        "const","function","let","async","await","new","this","null","true","false",
        "def","import","from","print","lambda","int","float","str","void","auto","template",
        "include","namespace","struct","enum","typedef","using","const","static","public",
        "in","is","as","try","catch","finally","throw","break","continue"
    )

    fun highlight(code: String): AnnotatedString = buildAnnotatedString {
        var i = 0
        val n = code.length
        while (i < n) {
            val c = code[i]
            // line comment // or #
            if ((c == '/' && i + 1 < n && code[i + 1] == '/') || c == '#') {
                val end = code.indexOf('\n', i).let { if (it == -1) n else it }
                withStyle(SpanStyle(color = CodeComment)) { append(code.substring(i, end)) }
                i = end
                continue
            }
            // string literal
            if (c == '"' || c == '\'' || c == '`') {
                val quote = c
                var j = i + 1
                while (j < n && code[j] != quote) { if (code[j] == '\\') j++; j++ }
                j = (j + 1).coerceAtMost(n)
                withStyle(SpanStyle(color = CodeString)) { append(code.substring(i, j)) }
                i = j
                continue
            }
            // number
            if (c.isDigit()) {
                var j = i
                while (j < n && (code[j].isLetterOrDigit() || code[j] == '.')) j++
                withStyle(SpanStyle(color = CodeNumber)) { append(code.substring(i, j)) }
                i = j
                continue
            }
            // identifier / keyword
            if (c.isLetter() || c == '_') {
                var j = i
                while (j < n && (code[j].isLetterOrDigit() || code[j] == '_')) j++
                val word = code.substring(i, j)
                if (word in keywords)
                    withStyle(SpanStyle(color = CodeKeyword)) { append(word) }
                else
                    withStyle(SpanStyle(color = TextPrimary)) { append(word) }
                i = j
                continue
            }
            withStyle(SpanStyle(color = TextPrimary)) { append(c.toString()) }
            i++
        }
    }
}
