package dev.ryazha.sassist.audio

import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Single-stream voice-note player shared across the chat. Only one note plays
 * at a time; the UI observes [state] to show play/pause and a progress bar.
 */
class VoicePlayer(private val scope: CoroutineScope) {
    data class PlayState(val messageId: String? = null, val playing: Boolean = false, val progress: Float = 0f)

    private val _state = MutableStateFlow(PlayState())
    val state: StateFlow<PlayState> = _state
    private var player: MediaPlayer? = null
    private var ticker: Job? = null

    /** Toggle playback of [url] tagged by [messageId]. */
    fun toggle(messageId: String, url: String) {
        val cur = _state.value
        if (cur.messageId == messageId && cur.playing) { pause(); return }
        if (cur.messageId == messageId && !cur.playing && player != null) { resume(); return }
        play(messageId, url)
    }

    private fun play(messageId: String, url: String) {
        stopInternal()
        try {
            val mp = MediaPlayer()
            mp.setDataSource(url)
            mp.setOnPreparedListener {
                it.start()
                _state.value = PlayState(messageId, true, 0f)
                startTicker()
            }
            mp.setOnCompletionListener { stopInternal(); _state.value = PlayState(messageId, false, 0f) }
            mp.setOnErrorListener { _, _, _ -> stopInternal(); _state.value = PlayState(); true }
            mp.prepareAsync()
            player = mp
        } catch (e: Exception) {
            stopInternal(); _state.value = PlayState()
        }
    }

    private fun resume() {
        player?.let {
            it.start()
            _state.value = _state.value.copy(playing = true)
            startTicker()
        }
    }

    private fun pause() {
        player?.pause()
        ticker?.cancel()
        _state.value = _state.value.copy(playing = false)
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch(Dispatchers.Main) {
            while (true) {
                val mp = player ?: break
                val dur = mp.duration.takeIf { it > 0 } ?: 1
                _state.value = _state.value.copy(progress = (mp.currentPosition.toFloat() / dur).coerceIn(0f, 1f))
                delay(80)
            }
        }
    }

    private fun stopInternal() {
        ticker?.cancel(); ticker = null
        try { player?.release() } catch (_: Exception) {}
        player = null
    }

    fun release() { stopInternal(); _state.value = PlayState() }
}
