package com.example.ui

import com.example.core.audio.AudioDiagnostic
import com.example.core.audio.VoiceEffect
import com.example.core.launcher.LaunchableApp

enum class ActiveMode {
    RECORD_AND_TRANSFORM,
    REALTIME_MIC,
    APPS
}

data class VoiceChangerUiState(
    val hasMicPermission: Boolean = false,
    val activeMode: ActiveMode = ActiveMode.RECORD_AND_TRANSFORM,
    val selectedEffect: VoiceEffect = VoiceEffect.ORIGINAL, // Defaults to Original to verify raw mic first
    val finePitchSemitones: Int = 0, // -6 to +6 fine tune
    val isRecording: Boolean = false,
    val recordingDurationMs: Long = 0L,
    val hasRecording: Boolean = false,
    val isProcessing: Boolean = false,
    val isPlaying: Boolean = false,
    val isAuditioningOriginal: Boolean = true, // Whether playing raw recording or transformed
    val playbackPositionMs: Long = 0L,
    val playbackDurationMs: Long = 0L,
    val amplitude: Float = 0f,
    val isLiveActive: Boolean = false,
    val isSharing: Boolean = false,
    val statusMessage: String? = null,
    val isErrorMessage: Boolean = false,
    val activeAudioName: String? = null,
    val diagnostic: AudioDiagnostic? = null,
    val targetApps: List<LaunchableApp> = emptyList()
) {
    val finePitchMultiplier: Float
        get() = Math.pow(2.0, finePitchSemitones / 12.0).toFloat()
}
