package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.audio.AudioDiagnostic
import com.example.core.audio.RealtimeAudioEngine
import com.example.core.audio.VoiceEffect
import com.example.core.audio.VoicePlayer
import com.example.core.audio.VoiceRecorder
import com.example.core.audio.WavAudioUtil
import com.example.core.launcher.AppLauncherManager
import com.example.core.launcher.LaunchableApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class VoiceChangerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(VoiceChangerUiState())
    val uiState: StateFlow<VoiceChangerUiState> = _uiState.asStateFlow()

    private val recorder = VoiceRecorder()
    private val player = VoicePlayer()
    private val realtimeEngine = RealtimeAudioEngine()

    private val audioDir: File by lazy {
        File(getApplication<Application>().cacheDir, "audio").apply { mkdirs() }
    }

    private val rawRecordFile: File by lazy {
        File(audioDir, "raw_recording.wav")
    }

    private val transformedFile: File by lazy {
        File(audioDir, "transformed_voice.wav")
    }

    init {
        // Collect recorder state
        viewModelScope.launch {
            recorder.isRecording.collect { rec ->
                _uiState.update { it.copy(isRecording = rec) }
            }
        }
        viewModelScope.launch {
            recorder.amplitude.collect { amp ->
                if (_uiState.value.isRecording) {
                    _uiState.update { it.copy(amplitude = amp) }
                }
            }
        }
        viewModelScope.launch {
            recorder.durationMs.collect { dur ->
                if (_uiState.value.isRecording) {
                    _uiState.update { it.copy(recordingDurationMs = dur) }
                }
            }
        }

        // Collect player state
        viewModelScope.launch {
            player.isPlaying.collect { playing ->
                _uiState.update { it.copy(isPlaying = playing) }
            }
        }
        viewModelScope.launch {
            player.playbackPositionMs.collect { pos ->
                if (_uiState.value.isPlaying) {
                    _uiState.update { it.copy(playbackPositionMs = pos) }
                }
            }
        }
        viewModelScope.launch {
            player.playbackAmplitude.collect { amp ->
                if (_uiState.value.isPlaying) {
                    _uiState.update { it.copy(amplitude = amp) }
                }
            }
        }

        // Collect realtime engine state
        viewModelScope.launch {
            realtimeEngine.isLiveActive.collect { live ->
                _uiState.update { it.copy(isLiveActive = live) }
            }
        }
        viewModelScope.launch {
            realtimeEngine.liveAmplitude.collect { amp ->
                if (_uiState.value.isLiveActive) {
                    _uiState.update { it.copy(amplitude = amp) }
                }
            }
        }

        // Initialize target launchable apps
        loadTargetApps()
    }

    fun onPermissionResult(isGranted: Boolean) {
        _uiState.update { it.copy(hasMicPermission = isGranted) }
        if (!isGranted) {
            stopAllAudio()
        }
    }

    fun setMode(mode: ActiveMode) {
        if (_uiState.value.activeMode == mode) return
        stopAllAudio()
        _uiState.update { it.copy(activeMode = mode, amplitude = 0f) }
    }

    fun selectEffect(effect: VoiceEffect) {
        _uiState.update { it.copy(selectedEffect = effect) }
        realtimeEngine.currentEffect = effect

        if (_uiState.value.hasRecording && rawRecordFile.exists() && rawRecordFile.length() > 44) {
            val wasPlaying = _uiState.value.isPlaying
            if (wasPlaying) {
                player.pause()
            }

            if (effect == VoiceEffect.ORIGINAL) {
                val loaded = player.loadAudio(rawRecordFile)
                if (loaded) {
                    _uiState.update {
                        it.copy(
                            isAuditioningOriginal = true,
                            activeAudioName = "Original Voice (Raw Mic)",
                            playbackDurationMs = player.totalDurationMs.value,
                            playbackPositionMs = 0L,
                            statusMessage = "Loaded Original Voice! Tap Play to listen.",
                            isErrorMessage = false
                        )
                    }
                    if (wasPlaying) player.play { showErrorMessage(it) }
                }
            } else {
                applyTransformation(rawRecordFile, effect, _uiState.value.finePitchMultiplier, resumePlayback = wasPlaying)
            }
        }
    }

    fun toggleAuditionOriginal(showOriginal: Boolean) {
        if (!_uiState.value.hasRecording) return
        val wasPlaying = _uiState.value.isPlaying
        if (wasPlaying) player.pause()

        if (showOriginal) {
            val loaded = player.loadAudio(rawRecordFile)
            if (loaded) {
                _uiState.update {
                    it.copy(
                        isAuditioningOriginal = true,
                        activeAudioName = "Original Voice (Raw Mic)",
                        playbackDurationMs = player.totalDurationMs.value,
                        playbackPositionMs = 0L,
                        statusMessage = "Playing Original Voice"
                    )
                }
                if (wasPlaying) player.play { showErrorMessage(it) }
            }
        } else {
            // Audition transformed
            if (transformedFile.exists() && transformedFile.length() > 44) {
                val loaded = player.loadAudio(transformedFile)
                if (loaded) {
                    _uiState.update {
                        it.copy(
                            isAuditioningOriginal = false,
                            activeAudioName = "${_uiState.value.selectedEffect.displayName} (Transformed)",
                            playbackDurationMs = player.totalDurationMs.value,
                            playbackPositionMs = 0L,
                            statusMessage = "Playing Transformed Voice (${_uiState.value.selectedEffect.displayName})"
                        )
                    }
                    if (wasPlaying) player.play { showErrorMessage(it) }
                }
            } else {
                applyTransformation(rawRecordFile, _uiState.value.selectedEffect, _uiState.value.finePitchMultiplier, resumePlayback = wasPlaying)
            }
        }
    }

    fun setFinePitch(semitones: Int) {
        val clamped = semitones.coerceIn(-6, 6)
        _uiState.update { it.copy(finePitchSemitones = clamped) }
        val multiplier = Math.pow(2.0, clamped / 12.0).toFloat()
        realtimeEngine.finePitchMultiplier = multiplier

        if (_uiState.value.hasRecording && rawRecordFile.exists() && _uiState.value.selectedEffect != VoiceEffect.ORIGINAL) {
            val wasPlaying = _uiState.value.isPlaying
            if (wasPlaying) player.pause()
            applyTransformation(rawRecordFile, _uiState.value.selectedEffect, multiplier, resumePlayback = wasPlaying)
        }
    }

    fun toggleRecording() {
        if (!_uiState.value.hasMicPermission) {
            showErrorMessage("Microphone permission is required to record audio.")
            return
        }

        if (_uiState.value.isRecording) {
            recorder.stopRecording(
                targetWavFile = rawRecordFile,
                onSuccess = { file, sampleRate, diagnostic ->
                    // Load raw recording first so user hears 100% original voice!
                    val loaded = player.loadAudio(file)
                    if (loaded) {
                        _uiState.update {
                            it.copy(
                                hasRecording = true,
                                isAuditioningOriginal = true,
                                activeAudioName = "Original Voice (Raw Mic)",
                                playbackDurationMs = player.totalDurationMs.value,
                                playbackPositionMs = 0L,
                                amplitude = 0f,
                                diagnostic = diagnostic,
                                statusMessage = diagnostic.summary,
                                isErrorMessage = false
                            )
                        }
                    } else {
                        showErrorMessage("Recorded file could not be loaded into audio player.")
                    }
                },
                onError = { err ->
                    showErrorMessage(err)
                    _uiState.update { it.copy(hasRecording = false, amplitude = 0f) }
                }
            )
        } else {
            stopAllAudio()
            val started = recorder.startRecording(
                targetWavFile = rawRecordFile,
                onError = { err ->
                    showErrorMessage(err)
                }
            )
            if (started) {
                _uiState.update {
                    it.copy(
                        statusMessage = "Recording raw 16-bit PCM... Speak clearly into the microphone.",
                        isErrorMessage = false,
                        hasRecording = false,
                        diagnostic = null
                    )
                }
            }
        }
    }

    /**
     * Built-in diagnostic test: records 3 seconds of microphone audio
     * and reports whether valid voice-level PCM data was captured.
     */
    fun runThreeSecondDiagnostic() {
        if (!_uiState.value.hasMicPermission) {
            showErrorMessage("Microphone permission required for 3-second test.")
            return
        }

        stopAllAudio()
        _uiState.update {
            it.copy(
                statusMessage = "Starting 3-second diagnostic test... Speak now!",
                isErrorMessage = false,
                hasRecording = false,
                diagnostic = null
            )
        }

        recorder.runThreeSecondDiagnosticTest(
            targetWavFile = rawRecordFile,
            scope = viewModelScope,
            onCountdown = { sec ->
                _uiState.update { it.copy(statusMessage = "Recording test audio... $sec s remaining. Speak!") }
            },
            onComplete = { diag, file ->
                val loaded = player.loadAudio(file)
                if (loaded) {
                    _uiState.update {
                        it.copy(
                            hasRecording = true,
                            isAuditioningOriginal = true,
                            activeAudioName = "Original Voice (3s Test)",
                            playbackDurationMs = player.totalDurationMs.value,
                            playbackPositionMs = 0L,
                            amplitude = 0f,
                            diagnostic = diag,
                            statusMessage = "3-Second Test Complete: ${diag.summary}",
                            isErrorMessage = !diag.isValidVoice
                        )
                    }
                }
            },
            onError = { err ->
                showErrorMessage("Diagnostic test error: $err")
            }
        )
    }

    private fun applyTransformation(
        inputFile: File,
        effect: VoiceEffect,
        multiplier: Float,
        resumePlayback: Boolean
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val success = withContext(Dispatchers.IO) {
                try {
                    WavAudioUtil.transformWav(
                        inputFile = inputFile,
                        outputFile = transformedFile,
                        effect = effect,
                        finePitchMultiplier = multiplier
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }

            if (success && transformedFile.exists()) {
                val loaded = player.loadAudio(transformedFile)
                if (loaded) {
                    _uiState.update {
                        it.copy(
                            hasRecording = true,
                            isProcessing = false,
                            isAuditioningOriginal = false,
                            playbackDurationMs = player.totalDurationMs.value,
                            playbackPositionMs = 0L,
                            amplitude = 0f,
                            activeAudioName = "${effect.displayName} (Transformed)",
                            statusMessage = "Transformed into ${effect.displayName}! Tap Play to listen.",
                            isErrorMessage = false
                        )
                    }
                    if (resumePlayback) {
                        player.play { showErrorMessage(it) }
                    }
                } else {
                    _uiState.update { it.copy(isProcessing = false) }
                    showErrorMessage("Could not load transformed audio file.")
                }
            } else {
                _uiState.update { it.copy(isProcessing = false) }
                showErrorMessage("Voice transformation failed. Please record again.")
            }
        }
    }

    fun togglePlayback() {
        if (!_uiState.value.hasRecording) {
            showErrorMessage("No audio recorded to play.")
            return
        }

        if (_uiState.value.isPlaying) {
            player.pause()
        } else {
            player.play { err ->
                showErrorMessage(err)
            }
        }
    }

    fun seekPlayback(positionMs: Long) {
        player.seekTo(positionMs)
        _uiState.update { it.copy(playbackPositionMs = positionMs) }
    }

    fun discardRecording() {
        stopAllAudio()
        if (rawRecordFile.exists()) rawRecordFile.delete()
        if (transformedFile.exists()) transformedFile.delete()

        _uiState.update {
            it.copy(
                hasRecording = false,
                isProcessing = false,
                playbackPositionMs = 0L,
                playbackDurationMs = 0L,
                recordingDurationMs = 0L,
                amplitude = 0f,
                activeAudioName = null,
                diagnostic = null,
                statusMessage = null,
                isErrorMessage = false
            )
        }
    }

    fun toggleLiveMic() {
        if (!_uiState.value.hasMicPermission) {
            showErrorMessage("Microphone permission is required for live mic.")
            return
        }

        if (_uiState.value.isLiveActive) {
            realtimeEngine.stop()
            _uiState.update { it.copy(statusMessage = "Live mic stopped.") }
        } else {
            player.stop()
            realtimeEngine.currentEffect = _uiState.value.selectedEffect
            realtimeEngine.finePitchMultiplier = _uiState.value.finePitchMultiplier
            val started = realtimeEngine.start()
            if (!started) {
                showErrorMessage("Could not start live mic. Check if another app is using the mic.")
            } else {
                _uiState.update {
                    it.copy(
                        statusMessage = "Live mic active (${_uiState.value.selectedEffect.displayName}). Use headphones!",
                        isErrorMessage = false
                    )
                }
            }
        }
    }

    fun shareActiveVoice(context: Context) {
        val fileToShare = if (_uiState.value.isAuditioningOriginal || !transformedFile.exists()) {
            rawRecordFile
        } else {
            transformedFile
        }

        if (!fileToShare.exists() || fileToShare.length() <= 44) {
            showErrorMessage("No audio recorded to share.")
            return
        }

        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                fileToShare
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/wav"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                val label = if (_uiState.value.isAuditioningOriginal) "Original Voice" else _uiState.value.selectedEffect.displayName
                putExtra(Intent.EXTRA_SUBJECT, "Voice Recording ($label)")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Audio").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            showErrorMessage("Failed to share audio: ${e.message}")
        }
    }

    fun loadTargetApps() {
        val apps = AppLauncherManager.getTargetApps(getApplication())
        _uiState.update { it.copy(targetApps = apps) }
    }

    fun launchApp(context: Context, app: LaunchableApp) {
        if (!app.isInstalled) {
            showErrorMessage("${app.name} is not installed on this device.")
            return
        }

        val result = AppLauncherManager.launchApp(context, app.packageName)
        if (result.isSuccess) {
            _uiState.update {
                it.copy(
                    statusMessage = "Opening ${app.name}...",
                    isErrorMessage = false
                )
            }
        } else {
            showErrorMessage("Could not launch ${app.name}: ${result.exceptionOrNull()?.message}")
        }
    }

    private fun showErrorMessage(message: String) {
        _uiState.update { it.copy(statusMessage = message, isErrorMessage = true) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    private fun stopAllAudio() {
        if (_uiState.value.isRecording) {
            recorder.cancelRecording()
        }
        player.stop()
        realtimeEngine.stop()
        _uiState.update { it.copy(amplitude = 0f) }
    }

    override fun onCleared() {
        super.onCleared()
        stopAllAudio()
        player.release()
    }
}
