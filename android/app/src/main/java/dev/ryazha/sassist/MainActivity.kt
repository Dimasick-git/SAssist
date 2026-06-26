package dev.ryazha.sassist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.ryazha.sassist.data.ChatViewModel
import dev.ryazha.sassist.model.ConnState
import dev.ryazha.sassist.ui.ChatScreen
import dev.ryazha.sassist.ui.ConnectScreen
import dev.ryazha.sassist.ui.ScriptScreen
import dev.ryazha.sassist.ui.theme.SAssistTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SAssistTheme { App() } }
    }
}

@Composable
private fun App(vm: ChatViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var showScripts by remember { mutableStateOf(false) }
    var codeMode by remember { mutableStateOf(false) }

    when {
        state.conn != ConnState.Connected -> {
            ConnectScreen(
                conn = state.conn,
                error = state.error,
                onConnect = { url, user -> vm.connect(url, user) }
            )
        }
        showScripts -> {
            ScriptScreen(
                lastMessage = state.messages.lastOrNull()?.text ?: "",
                onSend = { vm.send(it) },
                onBack = { showScripts = false }
            )
        }
        else -> {
            ChatScreen(
                channels = state.channels,
                currentChannel = state.currentChannel,
                messages = state.messages,
                presence = state.presence.size,
                codeMode = codeMode,
                onChannel = { vm.switchChannel(it) },
                onToggleCode = { codeMode = !codeMode },
                onSend = { vm.send(it) },
                onOpenScripts = { showScripts = true },
                onDisconnect = { vm.disconnect() }
            )
        }
    }
}
