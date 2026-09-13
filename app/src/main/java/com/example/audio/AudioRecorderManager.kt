package com.example.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
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

enum class RecordingState {
    IDLE, RECORDING, PAUSED, STOPPED
}

class AudioRecorderManager(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var timerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val _durationSeconds = MutableStateFlow(0)
    val durationSeconds: StateFlow<Int> = _durationSeconds.asStateFlow()

    private val _amplitude = MutableStateFlow(0)
    val amplitude: StateFlow<Int> = _amplitude.asStateFlow()

    fun startRecording(): File? {
        try {
            stopRecording() // Clean up any previous session

            val outputDir = context.cacheDir
            val outputFile = File(outputDir, "conference_audio_${System.currentTimeMillis()}.m4a")
            currentFile = outputFile

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            _recordingState.value = RecordingState.RECORDING
            _durationSeconds.value = 0

            startTimer()
            return outputFile
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to start recording", e)
            _recordingState.value = RecordingState.IDLE
            return null
        }
    }

    fun pauseRecording() {
        if (_recordingState.value == RecordingState.RECORDING && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.pause()
                _recordingState.value = RecordingState.PAUSED
            } catch (e: Exception) {
                Log.e("AudioRecorder", "Failed to pause recording", e)
            }
        }
    }

    fun resumeRecording() {
        if (_recordingState.value == RecordingState.PAUSED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.resume()
                _recordingState.value = RecordingState.RECORDING
            } catch (e: Exception) {
                Log.e("AudioRecorder", "Failed to resume recording", e)
            }
        }
    }

    fun stopRecording(): File? {
        timerJob?.cancel()
        timerJob = null

        if (_recordingState.value != RecordingState.IDLE) {
            try {
                mediaRecorder?.apply {
                    try {
                        stop()
                    } catch (e: Exception) {
                        Log.e("AudioRecorder", "Error stopping recorder", e)
                    }
                    release()
                }
            } catch (e: Exception) {
                Log.e("AudioRecorder", "Error releasing recorder", e)
            } finally {
                mediaRecorder = null
            }
        }

        _recordingState.value = RecordingState.STOPPED
        _amplitude.value = 0
        return currentFile
    }

    fun reset() {
        stopRecording()
        _recordingState.value = RecordingState.IDLE
        _durationSeconds.value = 0
        currentFile = null
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (_recordingState.value == RecordingState.RECORDING || _recordingState.value == RecordingState.PAUSED) {
                delay(200)
                if (_recordingState.value == RecordingState.RECORDING) {
                    try {
                        val maxAmp = mediaRecorder?.maxAmplitude ?: 0
                        _amplitude.value = maxAmp
                    } catch (e: Exception) {
                        // ignore amplitude read error
                    }
                }
                delay(800)
                if (_recordingState.value == RecordingState.RECORDING) {
                    _durationSeconds.value += 1
                }
            }
        }
    }

    fun getCurrentFile(): File? = currentFile
}
