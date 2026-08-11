package dev.ryazha.sassist.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.ryazha.sassist.data.ProfileUi
import dev.ryazha.sassist.ui.theme.*

private val PROFILE_COLORS = listOf("5865F2", "229ED9", "23A55A", "F23F43", "FAA61A", "EB459E", "9B59B6", "1ABC9C")

private fun profileColor(hex: String): Color = runCatching { Color(0xFF000000 or hex.toLong(16)) }.getOrDefault(Blurple)

@Composable
private fun ProfileAvatar(
    name: String,
    avatarUrl: String?,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(color)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "Profile avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = name.trim().firstOrNull()?.uppercase() ?: "?",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 34.sp
            )
        }
        if (onClick != null) {
            Box(
                Modifier.align(Alignment.BottomEnd).size(28.dp).clip(CircleShape)
                    .background(BgDark).border(2.dp, BgPanel, CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.AddAPhoto, contentDescription = "Change avatar", tint = TextPrimary, modifier = Modifier.size(15.dp)) }
        }
    }
}

@Composable
fun ProfileScreen(
    profile: ProfileUi,
    avatarUrl: (String) -> String,
    onUploadAvatar: (Uri) -> Unit,
    onSave: (displayName: String, bio: String, color: String) -> Unit,
    onCheckHandle: (String) -> Unit,
    onClaimHandle: (String) -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit
) {
    var name by remember(profile.displayName) { mutableStateOf(profile.displayName) }
    var bio by remember(profile.bio) { mutableStateOf(profile.bio) }
    var color by remember(profile.color) { mutableStateOf(profile.color) }
    var handle by remember(profile.handle) { mutableStateOf(profile.handle) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(onUploadAvatar) }
    val accent = profileColor(color)
    val avatar = profile.avatarId.takeIf { it.isNotBlank() }?.let(avatarUrl)
    val textFieldColors = TextFieldDefaults.colors(
        focusedContainerColor = BgInput, unfocusedContainerColor = BgInput,
        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
        cursorColor = Blurple, focusedIndicatorColor = Blurple, unfocusedIndicatorColor = BgPanel
    )

    Column(Modifier.fillMaxSize().background(BgDarkest)) {
        Row(
            Modifier.fillMaxWidth().background(BgDark).padding(horizontal = 6.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary) }
            Text("My profile", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (profile.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Blurple, trackColor = BgPanel)
            profile.error?.let { Text(it, color = Color(0xFFED4245), fontSize = 12.sp) }
            profile.notice?.let { Text(it, color = OnlineGreen, fontSize = 12.sp) }

            // Discord-inspired public profile preview.
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BgPanel)
            ) {
                Box(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height(118.dp).background(accent))
                    ProfileAvatar(
                        name = profile.displayName.ifBlank { name }, avatarUrl = avatar, color = accent,
                        modifier = Modifier.padding(start = 18.dp, top = 72.dp).size(92.dp)
                            .border(5.dp, BgPanel, CircleShape),
                        onClick = { picker.launch("image/*") }
                    )
                    Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 176.dp, bottom = 18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(profile.displayName.ifBlank { "New SAssist member" }, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (profile.premium) Icon(Icons.Filled.CheckCircle, contentDescription = "Verified", tint = Color(0xFFFEE75C), modifier = Modifier.padding(start = 6.dp).size(18.dp))
                        }
                        Text(
                            if (profile.handle.isBlank()) "@choose_username" else "@${profile.handle}",
                            color = TextMuted, fontSize = 13.sp
                        )
                        if (profile.bio.isNotBlank()) Text(profile.bio, color = TextPrimary, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
                        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = OnlineGreen, modifier = Modifier.size(15.dp))
                            Text("  All core features are enabled", color = TextMuted, fontSize = 12.sp)
                        }
                    }
                }
            }

            Text("PROFILE CUSTOMIZATION", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(40) }, modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors, shape = RoundedCornerShape(12.dp), singleLine = true,
                label = { Text("Display name") }, supportingText = { Text("This is how other members see you.") }
            )
            OutlinedTextField(
                value = bio, onValueChange = { bio = it.take(200) }, modifier = Modifier.fillMaxWidth(),
                colors = textFieldColors, shape = RoundedCornerShape(12.dp), minLines = 3, maxLines = 4,
                label = { Text("About me") }, placeholder = { Text("Tell the community about yourself", color = TextMuted) },
                supportingText = { Text("${bio.length}/200") }
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ColorLens, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                    Text("  Profile accent", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    PROFILE_COLORS.forEach { swatch ->
                        val swatchColor = profileColor(swatch)
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(swatchColor)
                                .then(if (swatch == color) Modifier.border(3.dp, TextPrimary, CircleShape) else Modifier)
                                .clickable { color = swatch }
                        )
                    }
                }
            }
            Button(
                onClick = { onSave(name.trim(), bio, color) }, enabled = !profile.busy && name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Blurple), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
            ) { Text("Save profile") }

            HorizontalDivider(color = BgPanel)
            Text("USERNAME", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("Claim an @username that other members can use to find you. All valid usernames are available to everyone.", color = TextMuted, fontSize = 12.sp)
            OutlinedTextField(
                value = handle,
                onValueChange = {
                    handle = it.trim().removePrefix("@").lowercase().take(20)
                    if (handle.length >= 3) onCheckHandle(handle)
                },
                modifier = Modifier.fillMaxWidth(), colors = textFieldColors, shape = RoundedCornerShape(12.dp), singleLine = true,
                prefix = { Text("@", color = TgAccent) }, label = { Text("Username") }
            )
            profile.handleCheck?.let { Text(it, color = if (it.contains("available")) OnlineGreen else Color(0xFFED4245), fontSize = 12.sp) }
            Button(
                onClick = { if (handle.isNotBlank()) onClaimHandle(handle) },
                enabled = !profile.busy && handle.length >= 3 && handle != profile.handle,
                colors = ButtonDefaults.buttonColors(containerColor = TgAccent), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
            ) { Text("Save @username") }

            HorizontalDivider(color = BgPanel)
            TextButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("Log out", color = Color(0xFFED4245)) }
        }
    }
}
