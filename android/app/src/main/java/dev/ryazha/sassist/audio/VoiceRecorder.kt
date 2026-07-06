package dev.ryazha.sassist.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Records a voice message to an m4a/AAC file, Telegram-style: start on
 * press-and-hold, stop() to keep it, cancel() to throw it away.
 */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    var startedAt: Long = 0L
        private set

    val isRecording: Boolean get() = recorder != null

    fun start(): Boolean {
        stopQuietly()
        val out = File(context.cacheDir, "voice_" + System.currentTimeMillis() + ".m4a")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(64_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(out.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            file = out
            startedAt = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            try { r.release() } catch (_: Exception) {}
            out.delete()
            recorder = null; file = null
            false
        }
    }

    /** Returns the recorded file and its duration, or null if too short/failed. */
    fun stop(): Result? {
        val r = recorder ?: return null
        val f = file
        val durationMs = System.currentTimeMillis() - startedAt
        recorder = null; file = null
        return try {
            r.stop(); r.release()
            if (f != null && f.exists() && durationMs >= 500) Result(f, durationMs) else { f?.delete(); null }
        } catch (e: Exception) {
            r.release(); f?.delete(); null
        }
    }

    fun cancel() {
        val r = recorder ?: return
        val f = file
        recorder = null; file = null
        try { r.stop() } catch (_: Exception) {}
        try { r.release() } catch (_: Exception) {}
        f?.delete()
    }

    private fun stopQuietly() { try { cancel() } catch (_: Exception) {} }

    data class Result(val file: File, val durationMs: Long)
}
