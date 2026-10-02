package com.example.core.audio

import android.content.Context
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Detects microphone concurrency, preemption, and contention using legitimate Android APIs.
 * Android limits concurrent mic access: when an app like Yalla Ludo engages in voice chat,
 * the Android OS may silence or block secondary recording streams.
 */
class MicConflictDetector(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _isMicInConflict = MutableStateFlow(false)
    val isMicInConflict: StateFlow<Boolean> = _isMicInConflict.asStateFlow()

    private val _isMicSilencedBySystem = MutableStateFlow(false)
    val isMicSilencedBySystem: StateFlow<Boolean> = _isMicSilencedBySystem.asStateFlow()

    private val _conflictDescription = MutableStateFlow<String?>(null)
    val conflictDescription: StateFlow<String?> = _conflictDescription.asStateFlow()

    private var recordingCallback: AudioManager.AudioRecordingCallback? = null

    fun startMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && audioManager != null) {
            try {
                val callback = object : AudioManager.AudioRecordingCallback() {
                    override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>) {
                        val isSilenced = configs.any { it.isClientSilenced }
                        // Multiple active recording configurations indicate concurrent mic contention
                        val inConflict = configs.size > 1 || isSilenced

                        _isMicSilencedBySystem.value = isSilenced
                        _isMicInConflict.value = inConflict

                        _conflictDescription.value = when {
                            isSilenced -> "Android OS has silenced microphone input because another app has higher priority."
                            inConflict -> "Another application (${configs.size} recording streams) is actively using the microphone."
                            else -> null
                        }
                    }
                }
                audioManager.registerAudioRecordingCallback(callback, Handler(Looper.getMainLooper()))
                recordingCallback = callback

                // Check initial state
                val initialConfigs = audioManager.activeRecordingConfigurations
                _isMicInConflict.value = initialConfigs.size > 1 || initialConfigs.any { it.isClientSilenced }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stopMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && audioManager != null && recordingCallback != null) {
            try {
                audioManager.unregisterAudioRecordingCallback(recordingCallback!!)
            } catch (e: Exception) {
                // ignore
            } finally {
                recordingCallback = null
            }
        }
    }

    /**
     * Checks if standard Android allows direct internal virtual audio loopback to another app.
     * Always returns false on unmodified AOSP/Android because of process sandbox isolation.
     */
    fun isInternalAudioInjectionSupported(): Boolean {
        return false
    }
}
