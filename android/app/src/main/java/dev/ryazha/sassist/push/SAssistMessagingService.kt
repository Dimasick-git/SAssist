package dev.ryazha.sassist.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dev.ryazha.sassist.MainActivity
import dev.ryazha.sassist.R
import dev.ryazha.sassist.data.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class SAssistMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        scope.launch {
            try {
                val session = Session(applicationContext)
                val serverUrl = session.serverUrl
                val authToken = session.token
                if (!serverUrl.isBlank() && !authToken.isNullOrBlank()) {
                    val client = OkHttpClient()
                    val json = JSONObject().put("token", token).toString()
                    val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
                    var httpUrl = serverUrl
                    if (httpUrl.startsWith("ws://")) httpUrl = httpUrl.replace("ws://", "http://")
                    if (httpUrl.startsWith("wss://")) httpUrl = httpUrl.replace("wss://", "https://")
                    if (!httpUrl.contains("://")) httpUrl = "https://$httpUrl"
                    val req = Request.Builder()
                        .url("$httpUrl/api/push/token")
                        .addHeader("Authorization", "Bearer $authToken")
                        .post(body)
                        .build()
                    client.newCall(req).execute().close()
                }
            } catch (e: Exception) {
                // ignore token sync error
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val title = remoteMessage.notification?.title ?: "SAssist"
        val body = remoteMessage.notification?.body ?: "New message"
        val channel = remoteMessage.data["channel"] ?: "general"

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("target_channel", channel)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "sassist_chat_push"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                channelId, "Chat Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for incoming SAssist chat messages and direct messages"
            }
            notificationManager.createNotificationChannel(notificationChannel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }
}
