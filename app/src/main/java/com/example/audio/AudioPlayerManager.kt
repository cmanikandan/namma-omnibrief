package com.example.audio

import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class AudioPlayerManager {

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _progressMs = MutableStateFlow(0)
    val progressMs: StateFlow<Int> = _progressMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0)
    val durationMs: StateFlow<Int> = _durationMs.asStateFlow()

    fun playFile(file: File) {
        if (!file.exists()) return

        stop()

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                _durationMs.value = duration
                setOnCompletionListener {
                    _isPlaying.value = false
                    _progressMs.value = 0
                    progressJob?.cancel()
                }
                start()
            }
            _isPlaying.value = true
            startProgressTracker()
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error playing audio file", e)
            _isPlaying.value = false
        }
    }

    fun togglePlayPause(file: File) {
        val player = mediaPlayer
        if (player != null && player.isPlaying) {
            player.pause()
            _isPlaying.value = false
        } else if (player != null) {
            player.start()
            _isPlaying.value = true
            startProgressTracker()
        } else {
            playFile(file)
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error stopping player", e)
        } finally {
            mediaPlayer = null
            _isPlaying.value = false
            _progressMs.value = 0
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (_isPlaying.value) {
                mediaPlayer?.let { player ->
                    try {
                        if (player.isPlaying) {
                            _progressMs.value = player.currentPosition
                        }
                    } catch (e: Exception) {
                        // ignore
                    }
                }
                delay(300)
            }
        }
    }
}
