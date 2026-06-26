package dev.ryazha.sassist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ryazha.sassist.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun CodeScreen(
    identifier: String,
    devCode: String?,
    busy: Boolean,
    error: String?,
    onVerify: (String) -> Unit,
    onResend: () -> Unit,
    onBack: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    var countdown by remember { mutableStateOf(30) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(devCode) { if (!devCode.isNullOrBlank()) code = devCode.take(6) }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(countdown) { if (countdown > 0) { delay(1000); countdown -= 1 } }

    Column(Modifier.fillMaxSize().background(BgDarkest).padding(24.dp)) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
        }
        Spacer(Modifier.height(8.dp))
        Text("Enter code", color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(6.dp))
        Text("Sent to " + identifier, color = TextMuted, fontSize = 14.sp)
        if (!devCode.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text("Dev mode: code auto-filled", color = TgAccent, fontSize = 12.sp)
        }
        Spacer(Modifier.height(28.dp))

        BasicTextField(
            value = code,
            onValueChange = { v -> if (v.length <= 6 && v.all { it.isDigit() }) code = v },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.focusRequester(focusRequester),
            decorationBox = {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(6) { i ->
                        val ch = code.getOrNull(i)?.toString() ?: ""
                        val active = i == code.length
                        Box(
                            Modifier.size(46.dp, 58.dp).clip(RoundedCornerShape(12.dp))
                                .background(BgInput)
                                .border(2.dp, if (active) Blurple else BgPanel, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) { Text(ch, color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        )

        if (error != null) {
            Spacer(Modifier.height(14.dp))
            Text(error, color = Color(0xFFED4245), fontSize = 13.sp)
        }

        Spacer(Modifier.height(18.dp))
        if (countdown > 0) {
            Text("Resend code in " + countdown + "s", color = TextMuted, fontSize = 13.sp)
        } else {
            Text(
                "Resend code", color = TgAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { countdown = 30; onResend() }
            )
        }

        Spacer(Modifier.weight(1f))
        Button(
            onClick = { onVerify(code) },
            enabled = !busy && code.length == 6,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Blurple, disabledContainerColor = BgPanel)
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = TextPrimary, strokeWidth = 2.dp)
            else Text("Verify & continue", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
    }
}
