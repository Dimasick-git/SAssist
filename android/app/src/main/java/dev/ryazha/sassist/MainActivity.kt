package dev.ryazha.sassist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.ryazha.sassist.data.ChatViewModel
import dev.ryazha.sassist.model.Stage
import dev.ryazha.sassist.ui.AuthIdentifierScreen
import dev.ryazha.sassist.ui.ChatScreen
import dev.ryazha.sassist.ui.ChatsListScreen
import dev.ryazha.sassist.ui.CodeScreen
import dev.ryazha.sassist.ui.ScriptScreen
import dev.ryazha.sassist.ui.WelcomeScreen
import dev.ryazha.sassist.ui.theme.BgDarkest
import dev.ryazha.sassist.ui.theme.SAssistTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SAssistTheme {
                val vm: ChatViewModel = viewModel()
                val state by vm.state.collectAsState()

                BackHandler(enabled = state.stage != Stage.Welcome) {
                    when (state.stage) {
                        Stage.Chat -> vm.backToChats()
                        Stage.Scripts -> vm.closeScripts()
                        Stage.EnterCode -> vm.startAuth()
                        Stage.EnterIdentifier -> vm.goWelcome()
                        Stage.Chats -> { /* stay */ }
                        else -> {}
                    }
                }

                AnimatedContent(
                    targetState = state.stage,
                    transitionSpec = {
                        (slideInHorizontally(animationSpec = tween(320)) { it / 3 } + fadeIn(tween(320))) togetherWith
                            (slideOutHorizontally(animationSpec = tween(320)) { -it / 3 } + fadeOut(tween(220)))
                    },
                    modifier = Modifier.fillMaxSize().background(BgDarkest),
                    label = "stage"
                ) { stage ->
                    when (stage) {
                        Stage.Welcome -> WelcomeScreen(onStart = { vm.startAuth() })
                        Stage.EnterIdentifier -> AuthIdentifierScreen(
                            method = state.authMethod, busy = state.authBusy, error = state.authError,
                            serverUrl = vm.serverUrl,
                            onMethod = { vm.setMethod(it) }, onServer = { vm.setServerUrl(it) },
                            onBack = { vm.goWelcome() },
                            onSubmit = { m, id, name -> vm.requestCode(m, id, name) }
                        )
                        Stage.EnterCode -> CodeScreen(
                            identifier = state.pendingIdentifier, devCode = state.devCode,
                            busy = state.authBusy, error = state.authError,
                            onVerify = { vm.verifyCode(it) }, onResend = { vm.resendCode() }, onBack = { vm.startAuth() }
                        )
                        Stage.Chats -> ChatsListScreen(
                            username = state.username, channels = state.channels, connState = state.connState,
                            presence = state.presenceByChannel, preview = { vm.lastPreview(it) },
                            onOpen = { vm.openChannel(it) }, onScripts = { vm.openScripts() },
                            onLogout = { vm.logout() }, onServer = { vm.setServerUrl(it) }
                        )
                        Stage.Chat -> ChatScreen(
                            channels = state.channels, currentChannel = state.currentChannel,
                            messages = state.messages, presence = state.presence,
                            codeMode = state.codeMode, e2ee = state.e2ee,
                            onChannel = { vm.openChannel(it) }, onToggleCode = { vm.toggleCode() },
                            onSend = { vm.send(it) }, onOpenScripts = { vm.openScripts() },
                            onBack = { vm.backToChats() }
                        )
                        Stage.Scripts -> ScriptScreen(
                            lastMessage = state.messages.lastOrNull()?.text ?: "",
                            onSend = { vm.send(it) }, onBack = { vm.closeScripts() }
                        )
                    }
                }
            }
        }
    }
}
