package dev.ryazha.sassist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.ryazha.sassist.data.PublicProfileUi
import dev.ryazha.sassist.ui.theme.*

private fun publicProfileColor(hex: String): Color = runCatching { Color(0xFF000000 or hex.toLong(16)) }.getOrDefault(Blurple)

@Composable
fun PublicProfileScreen(profile: PublicProfileUi, mediaUrl: (String) -> String, onMessage: () -> Unit, onBack: () -> Unit) {
    val accent = publicProfileColor(profile.color)
    val avatarUrl = profile.avatarId.takeIf { it.isNotBlank() }?.let(mediaUrl)
    val bannerUrl = profile.bannerId.takeIf { it.isNotBlank() }?.let(mediaUrl)
    Column(Modifier.fillMaxSize().background(BgDarkest)) {
        Row(Modifier.fillMaxWidth().background(BgDark).padding(horizontal = 6.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Назад", "Back"), tint = TextPrimary) }
            Text(tr("Профиль пользователя", "User profile"), color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        if (profile.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Blurple, trackColor = BgPanel)
        profile.error?.let { Text(it, color = Color(0xFFED4245), modifier = Modifier.padding(16.dp)) }
        if (!profile.busy && profile.error == null) {
            Card(Modifier.padding(16.dp).fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = BgPanel)) {
                Box(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height(126.dp).background(accent)) {
                        if (bannerUrl != null) AsyncImage(bannerUrl, tr("Баннер профиля", "Profile banner"), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    Box(Modifier.padding(start = 18.dp, top = 78.dp).size(92.dp).clip(CircleShape).background(accent).border(5.dp, BgPanel, CircleShape), contentAlignment = Alignment.Center) {
                        if (avatarUrl != null) AsyncImage(avatarUrl, tr("Аватар профиля", "Profile avatar"), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Text(profile.displayName.take(1).uppercase().ifBlank { "?" }, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
                    }
                    Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 180.dp, bottom = 18.dp)) {
                        Text(profile.displayName, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black)
                        Text(if (profile.handle.isBlank()) "@sassist_member" else "@${profile.handle}", color = TextMuted, fontSize = 13.sp)
                        if (profile.bio.isNotBlank()) Text(profile.bio, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.padding(top = 14.dp))
                        Button(onClick = onMessage, modifier = Modifier.fillMaxWidth().padding(top = 20.dp), colors = ButtonDefaults.buttonColors(containerColor = Blurple)) {
                            Icon(Icons.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("  " + tr("Написать", "Message"))
                        }
                    }
                }
            }
        }
    }
}
