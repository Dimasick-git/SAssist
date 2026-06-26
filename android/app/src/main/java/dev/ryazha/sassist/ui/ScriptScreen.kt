package dev.ryazha.sassist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ryazha.sassist.script.ScriptEngine
import dev.ryazha.sassist.script.ScriptHost
import dev.ryazha.sassist.ui.theme.BgDark
import dev.ryazha.sassist.ui.theme.BgPanel
import dev.ryazha.sassist.ui.theme.Blurple
import dev.ryazha.sassist.ui.theme.CodeBg
import dev.ryazha.sassist.ui.theme.OnlineGreen
import dev.ryazha.sassist.ui.theme.TextMuted
import dev.ryazha.sassist.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SAMPLES = listOf(
    "greet" to "sa.send('Hello from a script! 🤖');",
    "echo last" to "sa.log('last msg: ' + sa.lastMessage());",
    "fib" to "function fib(n){return n<2?n:fib(n-1)+fib(n-2)}\nsa.send('fib(10)=' + fib(10));",
    "uptime ping" to "for(var i=0;i<3;i++) sa.log('ping ' + i); 'done';"
)

@Composable
fun ScriptScreen(
    lastMessage: String,
    onSend: (String) -> Unit,
    onBack: () -> Unit
) {
    var code by remember { mutableStateOf("// sa.log(x), sa.send(text), sa.lastMessage()\nsa.send('fib(10)=' + (function f(n){return n<2?n:f(n-1)+f(n-2)})(10));") }
    var out by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(BgDark)) {
        // top bar
        Row(
            Modifier.fillMaxWidth().background(BgPanel).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Text("Script console", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.clip(RoundedCornerShape(10.dp)).background(if (running) TextMuted else OnlineGreen)
                    .clickable(enabled = !running) {
                        running = true
                        out = "running…"
                        scope.launch {
                            val result = withContext(Dispatchers.Default) {
                                val host = ScriptHost(onSend = onSend, lastMessageText = lastMessage)
                                ScriptEngine.run(code, host)
                            }
                            out = result
                            running = false
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Run", tint = Color.White)
                    Spacer(Modifier.width(4.dp))
                    Text("Run", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        // sample chips
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SAMPLES.forEach { (name, snippet) ->
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(BgPanel)
                        .clickable { code = snippet }.padding(horizontal = 12.dp, vertical = 6.dp)
                ) { Text(name, color = TextMuted, fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
            }
        }

        // code editor
        TextField(
            value = code, onValueChange = { code = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp).padding(horizontal = 12.dp),
            textStyle = TextStyle(color = TextPrimary, fontFamily = FontFamily.Monospace, fontSize = 14.sp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = CodeBg, unfocusedContainerColor = CodeBg,
                focusedIndicatorColor = Blurple, unfocusedIndicatorColor = Color.Transparent,
                cursorColor = Blurple
            )
        )

        // output console
        Text("OUTPUT", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
        Box(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp).clip(RoundedCornerShape(10.dp))
                .background(CodeBg).padding(12.dp)
        ) {
            Text(
                if (out.isEmpty()) "// run a script to see output here" else out,
                color = if (out.startsWith("!!")) Color(0xFFED4245) else TextPrimary,
                fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                modifier = Modifier.verticalScroll(rememberScrollState())
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

