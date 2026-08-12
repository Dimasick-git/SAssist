package dev.ryazha.sassist.net

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/** Downloads an attachment to app cache and lets Android select a compatible app. */
object MediaOpener {
    private val client = OkHttpClient.Builder()
        .connectTimeout(75, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /** Opens a file already received by Nearby without making any network request. */
    fun openLocal(context: Context, file: File, mime: String): String? {
        return try {
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mime.ifBlank { "application/octet-stream" })
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(intent, "Open with"))
            null
        } catch (_: Exception) {
            "No installed application can open this file."
        }
    }

    suspend fun open(context: Context, url: String, suggestedName: String, mime: String): String? = withContext(Dispatchers.IO) {
        try {
            val cleanName = suggestedName
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .take(120)
                .ifBlank { "attachment" }
            val cacheDir = File(context.cacheDir, "shared_attachments").apply { mkdirs() }
            val target = File(cacheDir, "${System.currentTimeMillis()}_$cleanName")
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext "Download failed (HTTP ${response.code})."
                val body = response.body ?: return@withContext "Attachment is empty."
                body.byteStream().use { input -> FileOutputStream(target).use { output -> input.copyTo(output) } }
            }
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", target)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mime.ifBlank { "application/octet-stream" })
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            withContext(Dispatchers.Main) {
                try {
                    context.startActivity(Intent.createChooser(intent, "Open with"))
                    null
                } catch (_: Exception) {
                    "No installed application can open this file."
                }
            }
        } catch (e: Exception) {
            "Could not open attachment: ${e.message ?: "network error"}"
        }
    }
}
