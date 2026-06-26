package dev.ryazha.sassist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ryazha.sassist.model.ConnState
import dev.ryazha.sassist.ui.theme.BgDark
import dev.ryazha.sassist.ui.theme.BgInput
import dev.ryazha.sassist.ui.theme.Blurple
import dev.ryazha.sassist.ui.theme.TextMuted
import dev.ryazha.sassist.ui.theme.TextPrimary
import dev.ryazha.sassist.ui.theme.TgAccent

@Composable
fun ConnectScreen(
    conn: ConnState,
    error: String?,
    onConnect: (url: String, username: String) -> Unit
) {
    var url by remember { mutableStateOf("ws://10.0.2.2:8080") }
    var username by remember { mutableStateOf("") }

    Box(
        Modifier.fillMaxSize().background(BgDark),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier.size(84.dp).clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(Blurple, TgAccent))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(46.dp))
            }
            Text("SAssist", color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text("the coder's messenger", color = TextMuted, fontSize = 14.sp)

            val colors = TextFieldDefaults.colors(
                focusedContainerColor = BgInput, unfocusedContainerColor = BgInput,
                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                focusedIndicatorColor = TgAccent, cursorColor = TgAccent
            )
            OutlinedTextField(
                value = url, onValueChange = { url = it },
                label = { Text("Server URL", color = TextMuted) },
                singleLine = true, colors = colors, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = username, onValueChange = { username = it },
                label = { Text("Username", color = TextMuted) },
                singleLine = true, colors = colors, modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { onConnect(url.trim(), username.trim()) },
                enabled = conn != ConnState.Connecting && username.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Blurple),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (conn == ConnState.Connecting)
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else
                    Text("Connect", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            if (error != null)
                Text("⚠ " + error, color = Color(0xFFED4245), fontSize = 13.sp)
        }
    }
}
