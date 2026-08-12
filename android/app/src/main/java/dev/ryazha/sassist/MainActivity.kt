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
import androidx.compose.runtime.CompositionLocalProvider
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
import dev.ryazha.sassist.ui.ProfileScreen
import dev.ryazha.sassist.ui.PublicProfileScreen
import dev.ryazha.sassist.ui.ScriptScreen
import dev.ryazha.sassist.ui.LocalAppLanguage
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
                val voiceState by vm.voiceState.collectAsState()

                CompositionLocalProvider(LocalAppLanguage provides state.language) {
                BackHandler(enabled = state.stage != Stage.Welcome) {
                    when (state.stage) {
                        Stage.Chat -> vm.backToChats()
                        Stage.Scripts -> vm.closeScripts()
                        Stage.Profile -> vm.closeProfile()
                        Stage.UserProfile -> vm.closeUserProfile()
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
                            username = state.username,
                            avatarUrl = state.profile.avatarId.takeIf { it.isNotBlank() }?.let { vm.mediaUrl(it) },
                            channels = state.channels, connState = state.connState,
                            presence = state.presenceByChannel, preview = { vm.lastPreview(it) },
                            onOpen = { vm.openChannel(it) }, onScripts = { vm.openScripts() },
                            onProfile = { vm.openProfile() },
                            onLogout = { vm.logout() }, onServer = { vm.setServerUrl(it) },
                            onLanguage = { vm.setLanguage(it) },
                            nearby = state.nearby,
                            onStartNearby = { vm.startNearby() }, onStopNearby = { vm.stopNearby() },
                            onConnectNearby = { vm.connectNearby(it) }, onAcceptNearby = { vm.acceptNearby(it) },
                            onRejectNearby = { vm.rejectNearby(it) }
                        )
                        Stage.Chat -> ChatScreen(
                            channels = state.channels, currentChannel = state.currentChannel,
                            messages = state.messages, presence = state.presence,
                            typingUser = state.typingByChannel[state.currentChannel],
                            codeMode = state.codeMode, e2ee = state.e2ee,
                            connState = state.connState, myUserId = state.userId,
                            replyingTo = state.replyingTo, uploadBusy = state.uploadBusy,
                            hasCustomKey = state.customKeyChannels.contains(state.currentChannel),
                            recording = state.recording, recordingStartedAt = state.recordingStartedAt,
                            voiceState = voiceState,
                            mediaUrl = { vm.mediaUrl(it) }, nameOf = { vm.nameOf(it) },
                            onChannel = { vm.openChannel(it) }, onToggleCode = { vm.toggleCode() },
                            onSend = { vm.send(it) }, onSendMedia = { vm.sendMedia(it) },
                            onTyping = { vm.sendTyping() },
                            onReact = { id, emoji -> vm.react(id, emoji) },
                            onReply = { vm.startReply(it) }, onCancelReply = { vm.cancelReply() },
                            onOpenUserProfile = { vm.openUserProfile(it.userId) },
                            onRetry = { vm.retryMessage(it) },
                            onToggleVoice = { id, url -> vm.toggleVoice(id, url) },
                            onStartVoice = { vm.startVoiceRecording() },
                            onStopVoice = { vm.stopAndSendVoice() },
                            onCancelVoice = { vm.cancelVoiceRecording() },
                            onMarkRead = { vm.markChannelRead(state.currentChannel) },
                            onSetRoomKey = { vm.setRoomKey(it) },
                            onOpenScripts = { vm.openScripts() },
                            onBack = { vm.backToChats() }
                        )
                        Stage.Profile -> ProfileScreen(
                            profile = state.profile,
                            avatarUrl = { vm.mediaUrl(it) },
                            onUploadAvatar = { vm.uploadAvatar(it) },
                            onUploadBanner = { vm.uploadBanner(it) },
                            onSave = { n, b, c -> vm.saveProfile(n, b, c) },
                            onCheckHandle = { vm.checkHandle(it) },
                            onClaimHandle = { vm.claimHandle(it) },
                            onLogout = { vm.logout() },
                            onBack = { vm.closeProfile() }
                        )
                        Stage.UserProfile -> PublicProfileScreen(
                            profile = state.viewedProfile,
                            mediaUrl = { vm.mediaUrl(it) },
                            onMessage = { vm.startDirectMessage(state.viewedProfile.userId) },
                            onBack = { vm.closeUserProfile() }
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
}
