package dev.ryazha.sassist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ryazha.sassist.model.AuthMethod
import dev.ryazha.sassist.ui.theme.*

@Composable
fun AuthIdentifierScreen(
    method: AuthMethod,
    busy: Boolean,
    error: String?,
    serverUrl: String,
    onMethod: (AuthMethod) -> Unit,
    onServer: (String) -> Unit,
    onBack: () -> Unit,
    onSubmit: (AuthMethod, String, String) -> Unit
) {
    var identifier by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var server by remember { mutableStateOf(serverUrl) }
    var advanced by remember { mutableStateOf(false) }

    val tf = TextFieldDefaults.colors(
        focusedContainerColor = BgInput, unfocusedContainerColor = BgInput,
        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
        cursorColor = Blurple,
        focusedIndicatorColor = Blurple, unfocusedIndicatorColor = BgPanel,
        focusedLabelColor = Blurple, unfocusedLabelColor = TextMuted
    )

    Column(Modifier.fillMaxSize().background(BgDarkest).padding(24.dp)) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
        }
        Spacer(Modifier.height(8.dp))
        Text("Sign in", color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(6.dp))
        Text("We'll text or email you a one-time code.", color = TextMuted, fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))

        // segmented Phone | Email
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BgPanel).padding(4.dp)
        ) {
            SegItem("📱 Phone", method == AuthMethod.Phone, Modifier.weight(1f)) { onMethod(AuthMethod.Phone) }
            SegItem("✉️ Email", method == AuthMethod.Email, Modifier.weight(1f)) { onMethod(AuthMethod.Email) }
        }
        Spacer(Modifier.height(18.dp))

        OutlinedTextField(
            value = identifier, onValueChange = { identifier = it },
            label = { Text(if (method == AuthMethod.Phone) "Phone number" else "Email") },
            placeholder = { Text(if (method == AuthMethod.Phone) "+1234567890" else "you@code.dev", color = TextMuted) },
            singleLine = true, colors = tf, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (method == AuthMethod.Phone) KeyboardType.Phone else KeyboardType.Email
            )
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("Display name") },
            placeholder = { Text("How others see you", color = TextMuted) },
            singleLine = true, colors = tf, modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(10.dp))
        Text(
            if (advanced) "▾ Server settings" else "▸ Server settings",
            color = TgAccent, fontSize = 13.sp,
            modifier = Modifier.clickable { advanced = !advanced }
        )
        if (advanced) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = server, onValueChange = { server = it; onServer(it) },
                label = { Text("Server URL") }, singleLine = true, colors = tf,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = androidx.compose.ui.graphics.Color(0xFFED4245), fontSize = 13.sp)
        }

        Spacer(Modifier.weight(1f))
        Button(
            onClick = { onServer(server); onSubmit(method, identifier, name) },
            enabled = !busy && identifier.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Blurple, disabledContainerColor = BgPanel)
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = TextPrimary, strokeWidth = 2.dp)
            else Text("Send code", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
    }
}

@Composable
private fun SegItem(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) Brush.linearGradient(listOf(Blurple, TgAccent)) else Brush.linearGradient(listOf(BgPanel, BgPanel)))
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) TextPrimary else TextMuted, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}
