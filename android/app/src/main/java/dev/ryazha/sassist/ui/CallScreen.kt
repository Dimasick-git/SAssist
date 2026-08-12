package dev.ryazha.sassist.ui

import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.ryazha.sassist.model.CallKind
import dev.ryazha.sassist.model.CallPhase
import dev.ryazha.sassist.model.CallUi
import dev.ryazha.sassist.ui.theme.BgDark
import dev.ryazha.sassist.ui.theme.BgDarkest
import dev.ryazha.sassist.ui.theme.OnlineGreen
import dev.ryazha.sassist.ui.theme.TextMuted
import dev.ryazha.sassist.ui.theme.TextPrimary
import org.json.JSONObject

private class SignalBridge(private val onSignal: (String) -> Unit) {
    @JavascriptInterface fun signal(raw: String) = onSignal(raw)
}

@Composable
fun CallScreen(
    call: CallUi,
    onSignal: (String) -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onEnd: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var granted by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                (call.kind == CallKind.Audio || androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED)
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        granted = result[android.Manifest.permission.RECORD_AUDIO] == true &&
            (call.kind == CallKind.Audio || result[android.Manifest.permission.CAMERA] == true)
    }
    LaunchedEffect(call.kind) {
        if (!granted) {
            val permissions = if (call.kind == CallKind.Video) arrayOf(android.Manifest.permission.RECORD_AUDIO, android.Manifest.permission.CAMERA)
            else arrayOf(android.Manifest.permission.RECORD_AUDIO)
            permissionLauncher.launch(permissions)
        }
    }

    var webView by remember(call.channel) { mutableStateOf<WebView?>(null) }
    var webReady by remember(call.channel) { mutableStateOf(false) }
    var started by remember(call.channel) { mutableStateOf(false) }
    var deliveredSignals by remember(call.channel) { mutableIntStateOf(0) }
    var muted by remember { mutableStateOf(false) }
    var cameraOff by remember { mutableStateOf(false) }

    LaunchedEffect(webReady, granted, call.phase, call.signals.size) {
        val web = webView ?: return@LaunchedEffect
        if (!webReady || !granted) return@LaunchedEffect
        if (call.phase == CallPhase.Outgoing && !started) {
            started = true
            web.evaluateJavascript("startOutgoing(" + if (call.kind == CallKind.Video) "true" else "false" + ")", null)
        }
        if (call.phase != CallPhase.Incoming) {
            call.signals.drop(deliveredSignals).forEach { signal ->
                web.evaluateJavascript("receiveSignal(" + JSONObject.quote(signal) + ")", null)
            }
            deliveredSignals = call.signals.size
        }
    }

    Column(Modifier.fillMaxSize().background(BgDarkest), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
            AndroidView(
                factory = { viewContext ->
                    WebView(viewContext).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = false
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.allowFileAccess = false
                        webChromeClient = object : WebChromeClient() {
                            override fun onPermissionRequest(request: android.webkit.PermissionRequest) {
                                if (granted) request.grant(request.resources) else request.deny()
                            }
                        }
                        addJavascriptInterface(SignalBridge(onSignal), "SAssistCall")
                        webViewClient = object : android.webkit.WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String) { webReady = true }
                        }
                        loadDataWithBaseURL("https://sassist.local/", WEBRTC_HTML, "text/html", "UTF-8", null)
                        webView = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            Column(
                Modifier.align(Alignment.TopCenter).padding(top = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(call.peerName, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(callStatus(call.phase), color = if (call.phase == CallPhase.Active) OnlineGreen else TextMuted, fontSize = 14.sp)
            }
            if (!granted) {
                Surface(Modifier.align(Alignment.Center), color = BgDark) {
                    Text(tr("Для звонка нужны разрешения камеры и микрофона.", "A call needs camera and microphone permissions."), color = TextPrimary, modifier = Modifier.padding(16.dp))
                }
            }
        }

        if (call.phase == CallPhase.Incoming) {
            Row(Modifier.fillMaxWidth().background(BgDark).padding(24.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                CallControl(Icons.Filled.CallEnd, Color(0xFFED4245), tr("Отклонить", "Decline"), onDecline)
                CallControl(Icons.Filled.Mic, OnlineGreen, tr("Принять", "Accept"), onAccept)
            }
        } else {
            Row(Modifier.fillMaxWidth().background(BgDark).padding(18.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                CallControl(if (muted) Icons.Filled.MicOff else Icons.Filled.Mic, Color(0xFF5865F2), tr("Микрофон", "Microphone")) {
                    muted = !muted; webView?.evaluateJavascript("toggleMute()", null)
                }
                if (call.kind == CallKind.Video) {
                    CallControl(if (cameraOff) Icons.Filled.VideocamOff else Icons.Filled.Videocam, Color(0xFF5865F2), tr("Камера", "Camera")) {
                        cameraOff = !cameraOff; webView?.evaluateJavascript("toggleCamera()", null)
                    }
                }
                CallControl(Icons.Filled.CallEnd, Color(0xFFED4245), tr("Завершить", "End")) {
                    webView?.evaluateJavascript("endCall()", null); onEnd()
                }
            }
        }
    }
}

@Composable
private fun CallControl(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(onClick = onClick, modifier = Modifier.size(58.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = color, contentColor = Color.White)) {
            Icon(icon, contentDescription = label)
        }
        Spacer(Modifier.height(5.dp))
        Text(label, color = TextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun callStatus(phase: CallPhase): String = when (phase) {
    CallPhase.Incoming -> tr("Входящий звонок", "Incoming call")
    CallPhase.Outgoing -> tr("Звоним…", "Calling…")
    CallPhase.Connecting -> tr("Подключение…", "Connecting…")
    CallPhase.Active -> tr("В звонке", "In call")
}

private const val WEBRTC_HTML = """
<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>
<style>html,body{margin:0;background:#000;width:100%;height:100%;overflow:hidden}video{position:absolute;object-fit:cover}.remote{width:100%;height:100%}.local{width:29%;height:23%;right:12px;bottom:12px;border-radius:14px;border:1px solid #5865F2}</style></head>
<body><video id='remote' class='remote' autoplay playsinline></video><video id='local' class='local' autoplay muted playsinline></video>
<script>
let pc=null, stream=null, videoMode=false;
const signal=(x)=>window.SAssistCall.signal(JSON.stringify(x));
async function ensure(video){if(pc)return;videoMode=video;stream=await navigator.mediaDevices.getUserMedia({audio:true,video:video});document.getElementById('local').srcObject=stream;pc=new RTCPeerConnection({iceServers:[{urls:'stun:stun.l.google.com:19302'}]});stream.getTracks().forEach(t=>pc.addTrack(t,stream));pc.ontrack=e=>{document.getElementById('remote').srcObject=e.streams[0]};pc.onicecandidate=e=>{if(e.candidate)signal({type:'candidate',kind:videoMode?'video':'audio',candidate:e.candidate})};}
async function startOutgoing(video){await ensure(video);const offer=await pc.createOffer();await pc.setLocalDescription(offer);signal({type:'offer',kind:video?'video':'audio',sdp:pc.localDescription});}
async function receiveSignal(raw){const x=JSON.parse(raw);if(x.type==='offer'){await ensure(x.kind==='video');await pc.setRemoteDescription(x.sdp);const answer=await pc.createAnswer();await pc.setLocalDescription(answer);signal({type:'answer',kind:videoMode?'video':'audio',sdp:pc.localDescription});}else if(x.type==='answer'){await pc.setRemoteDescription(x.sdp);}else if(x.type==='candidate'&&x.candidate){try{await pc.addIceCandidate(x.candidate);}catch(e){}}}
function toggleMute(){if(stream)stream.getAudioTracks().forEach(t=>t.enabled=!t.enabled)}function toggleCamera(){if(stream)stream.getVideoTracks().forEach(t=>t.enabled=!t.enabled)}function endCall(){if(stream)stream.getTracks().forEach(t=>t.stop());if(pc)pc.close();pc=null;stream=null;}
</script></body></html>
"""
