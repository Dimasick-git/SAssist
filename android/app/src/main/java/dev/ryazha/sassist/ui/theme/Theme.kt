package dev.ryazha.sassist.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

// --- Discord + Telegram inspired palette ---
val BgDarkest = Color(0xFF1E1F22)
val BgDark = Color(0xFF2B2D31)
val BgPanel = Color(0xFF313338)
val BgInput = Color(0xFF383A40)
val Blurple = Color(0xFF5865F2)
val TgAccent = Color(0xFF229ED9)
val OnlineGreen = Color(0xFF23A55A)
val TextPrimary = Color(0xFFF2F3F5)
val TextMuted = Color(0xFFB5BAC1)
val CodeBg = Color(0xFF1B1C20)
val CodeKeyword = Color(0xFFC586C0)
val CodeString = Color(0xFFCE9178)
val CodeNumber = Color(0xFFB5CEA8)
val CodeComment = Color(0xFF6A9955)
val CodeFunc = Color(0xFFDCDCAA)

private val SAssistColors = darkColorScheme(
    primary = Blurple,
    secondary = TgAccent,
    background = BgDark,
    surface = BgPanel,
    onPrimary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

private val SAssistType = Typography(
    bodyLarge = Typography().bodyLarge.copy(fontFamily = FontFamily.SansSerif)
)

@Composable
fun SAssistTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SAssistColors, typography = SAssistType, content = content)
}
