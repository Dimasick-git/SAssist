package dev.ryazha.sassist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ryazha.sassist.data.ProfileUi
import dev.ryazha.sassist.ui.theme.*

private val PROFILE_COLORS = listOf("5865F2", "229ED9", "23A55A", "F23F43", "FAA61A", "EB459E", "9B59B6", "1ABC9C")

@Composable
fun ProfileScreen(
    profile: ProfileUi,
    onSave: (displayName: String, bio: String, color: String) -> Unit,
    onCheckHandle: (String) -> Unit,
    onClaimHandle: (String) -> Unit,
    onClaimPremium: (String) -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit
) {
    var name by remember(profile.displayName) { mutableStateOf(profile.displayName) }
    var bio by remember(profile.bio) { mutableStateOf(profile.bio) }
    var color by remember(profile.color) { mutableStateOf(profile.color) }
    var handle by remember(profile.handle) { mutableStateOf(profile.handle) }
    var premiumCode by remember { mutableStateOf("") }

    val tf = TextFieldDefaults.colors(
        focusedContainerColor = BgInput, unfocusedContainerColor = BgInput,
        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
        cursorColor = Blurple, focusedIndicatorColor = BgInput, unfocusedIndicatorColor = BgInput
    )

    Column(Modifier.fillMaxSize().background(BgDarkest)) {
        Row(
            Modifier.fillMaxWidth().background(BgDark).padding(horizontal = 6.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Text("Profile", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (profile.premium) {
                Text("★ Premium", color = Color(0xFFFEE75C), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(end = 12.dp))
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (profile.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Blurple)
            profile.error?.let { Text(it, color = Color(0xFFED4245), fontSize = 12.sp) }
            profile.notice?.let { Text(it, color = OnlineGreen, fontSize = 12.sp) }

            // Display name
            Text("Display name", color = TextMuted, fontSize = 12.sp)
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(40) },
                modifier = Modifier.fillMaxWidth(), colors = tf,
                shape = RoundedCornerShape(12.dp), singleLine = true
            )

            // Bio
            Text("Bio", color = TextMuted, fontSize = 12.sp)
            OutlinedTextField(
                value = bio, onValueChange = { bio = it.take(200) },
                modifier = Modifier.fillMaxWidth(), colors = tf,
                shape = RoundedCornerShape(12.dp), maxLines = 3,
                placeholder = { Text("A few words about you", color = TextMuted) }
            )

            // Color
            Text("Name color", color = TextMuted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PROFILE_COLORS.forEach { c ->
                    val col = Color(0xFF000000 or c.toLong(16))
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(col)
                            .then(if (c == color) Modifier.border(2.dp, TextPrimary, CircleShape) else Modifier)
                            .clickable { color = c }
                    )
                }
            }

            Button(
                onClick = { onSave(name, bio, color) },
                enabled = !profile.busy,
                colors = ButtonDefaults.buttonColors(containerColor = Blurple),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save profile") }

            HorizontalDivider(color = BgPanel)

            // Handle
            Text("Username", color = TextMuted, fontSize = 12.sp)
            Text(
                if (profile.handle.isBlank()) "Claim a unique @username. Short ones (≤4 chars) are Premium-only."
                else "Current: @" + profile.handle,
                color = TextMuted, fontSize = 11.sp
            )
            OutlinedTextField(
                value = handle,
                onValueChange = { handle = it.trim().removePrefix("@"); if (handle.length >= 3) onCheckHandle(handle) },
                modifier = Modifier.fillMaxWidth(), colors = tf,
                shape = RoundedCornerShape(12.dp), singleLine = true,
                prefix = { Text("@", color = TgAccent) }
            )
            profile.handleCheck?.let { Text(it, color = TgAccent, fontSize = 11.sp) }
            Button(
                onClick = { if (handle.isNotBlank()) onClaimHandle(handle) },
                enabled = !profile.busy && handle.isNotBlank() && handle != profile.handle,
                colors = ButtonDefaults.buttonColors(containerColor = TgAccent),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Claim @username") }

            if (!profile.premium) {
                HorizontalDivider(color = BgPanel)
                Text("Premium", color = TextMuted, fontSize = 12.sp)
                OutlinedTextField(
                    value = premiumCode, onValueChange = { premiumCode = it },
                    modifier = Modifier.fillMaxWidth(), colors = tf,
                    shape = RoundedCornerShape(12.dp), singleLine = true,
                    placeholder = { Text("Premium code", color = TextMuted) }
                )
                Button(
                    onClick = { if (premiumCode.isNotBlank()) onClaimPremium(premiumCode.trim()) },
                    enabled = !profile.busy && premiumCode.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB8860B)),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Activate Premium ★") }
            }

            HorizontalDivider(color = BgPanel)
            TextButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                Text("Log out", color = Color(0xFFED4245))
            }
        }
    }
}
