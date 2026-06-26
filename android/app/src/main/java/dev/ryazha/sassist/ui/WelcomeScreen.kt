package dev.ryazha.sassist.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ryazha.sassist.ui.theme.*

@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    val infinite = rememberInfiniteTransition(label = "pulse")
    val scale by infinite.animateFloat(
        initialValue = 1f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(animation = tween(1400), repeatMode = RepeatMode.Reverse),
        label = "scale"
    )
    Box(Modifier.fillMaxSize().background(BgDarkest), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Box(
                Modifier.size(116.dp).scale(scale).clip(RoundedCornerShape(30.dp))
                    .background(Brush.linearGradient(listOf(Blurple, TgAccent))),
                contentAlignment = Alignment.Center
            ) { Text("</>", color = TextPrimary, fontSize = 40.sp, fontWeight = FontWeight.Black) }
            Spacer(Modifier.height(30.dp))
            Text("SAssist", color = TextPrimary, fontSize = 42.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(10.dp))
            Text(
                "Chat built for coders — encrypted, fast, yours.",
                color = TextMuted, fontSize = 15.sp, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(54.dp))
            Button(
                onClick = onStart,
                modifier = Modifier.height(54.dp).fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Blurple)
            ) { Text("Get started", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary) }
            Spacer(Modifier.height(14.dp))
            Text("🔒 End-to-end encrypted", color = TextMuted, fontSize = 12.sp)
        }
    }
}
