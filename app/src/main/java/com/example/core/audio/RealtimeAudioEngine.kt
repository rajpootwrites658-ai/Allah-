package com.example.core.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Process
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Real-time low-latency microphone-to-speaker voice transformer.
 * Pipes mic audio directly through the VoiceDspEngine and out to AudioTrack.
 */
class RealtimeAudioEngine(
    private val sampleRate: Int = 44100
) {
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var workerThread: Thread? = null

    private val isRunning = AtomicBoolean(false)

    private val _isLiveActive = MutableStateFlow(false)
    val isLiveActive: StateFlow<Boolean> = _isLiveActive.asStateFlow()

    private val _liveAmplitude = MutableStateFlow(0f)
    val liveAmplitude: StateFlow<Float> = _liveAmplitude.asStateFlow()

    @Volatile
    var currentEffect: VoiceEffect = VoiceEffect.NATURAL_FEMALE

    @Volatile
    var finePitchMultiplier: Float = 1.0f

    private val dspEngine = VoiceDspEngine(sampleRate = sampleRate)

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (isRunning.get()) return true

        val channelIn = AudioFormat.CHANNEL_IN_MONO
        val channelOut = AudioFormat.CHANNEL_OUT_MONO
        val format = AudioFormat.ENCODING_PCM_16BIT

        val minInBuf = AudioRecord.getMinBufferSize(sampleRate, channelIn, format)
        val minOutBuf = AudioTrack.getMinBufferSize(sampleRate, channelOut, format)

        // Select compact buffer size for minimal audio latency
        val chunkSize = 512
        val inBufSize = maxOf(minInBuf, chunkSize * 4)
        val outBufSize = maxOf(minOutBuf, chunkSize * 4)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelIn,
                format,
                inBufSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                release()
                return false
            }

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(format)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelOut)
                        .build()
                )
                .setBufferSizeInBytes(outBufSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                release()
                return false
            }

            dspEngine.reset()
            audioRecord?.startRecording()
            audioTrack?.play()

            isRunning.set(true)
            _isLiveActive.value = true

            workerThread = Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                val inBuffer = ShortArray(chunkSize)
                val outBuffer = ShortArray(chunkSize)

                while (isRunning.get()) {
                    val read = audioRecord?.read(inBuffer, 0, chunkSize) ?: -1
                    if (read > 0) {
                        dspEngine.processStream(
                            input = inBuffer,
                            output = outBuffer,
                            count = read,
                            effect = currentEffect,
                            finePitchMultiplier = finePitchMultiplier
                        )

                        audioTrack?.write(outBuffer, 0, read)

                        val level = VoiceDspEngine.calculateRmsLevel(inBuffer, read)
                        _liveAmplitude.value = level
                    }
                }
            }, "VoiceChangerRealtimeThread").apply { start() }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            release()
            return false
        }
    }

    fun stop() {
        isRunning.set(false)
        _isLiveActive.value = false
        _liveAmplitude.value = 0f

        workerThread?.interrupt()
        workerThread = null

        release()
    }

    private fun release() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            // ignore
        } finally {
            audioRecord = null
        }

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            // ignore
        } finally {
            audioTrack = null
        }
    }
}
