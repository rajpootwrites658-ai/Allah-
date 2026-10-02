package com.example.core.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * Robust 16-bit PCM microphone recorder.
 * - Tests supported microphone configurations (VOICE_RECOGNITION, MIC, DEFAULT).
 * - Streams audio directly to disk on a real-time priority audio thread.
 * - Guarantees full stream flushing via thread.join() before writing the canonical WAV header.
 * - Computes real-time and post-recording audio diagnostics (sample rate, channels, min/max, RMS, unique samples).
 */
class VoiceRecorder {

    private var audioRecord: AudioRecord? = null
    private var recordThread: Thread? = null
    private val isRecordingFlag = AtomicBoolean(false)

    private var activeSampleRate = 44100
    private var activeSource = MediaRecorder.AudioSource.VOICE_RECOGNITION

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private var recordStartTime = 0L
    private var tempPcmFile: File? = null

    @SuppressLint("MissingPermission")
    fun startRecording(
        targetWavFile: File,
        onError: (String) -> Unit
    ): Boolean {
        if (isRecordingFlag.get()) return true

        // Test supported configurations in order of voice quality
        val sources = listOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT
        )
        val sampleRates = listOf(44100, 16000)

        var createdRecord: AudioRecord? = null
        var chosenRate = 44100
        var chosenSource = MediaRecorder.AudioSource.VOICE_RECOGNITION

        for (source in sources) {
            for (rate in sampleRates) {
                val channelConfig = AudioFormat.CHANNEL_IN_MONO
                val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                val minBuf = AudioRecord.getMinBufferSize(rate, channelConfig, audioFormat)
                if (minBuf <= 0) continue

                val bufSize = maxOf(minBuf * 2, 4096)

                try {
                    val record = AudioRecord(source, rate, channelConfig, audioFormat, bufSize)
                    if (record.state == AudioRecord.STATE_INITIALIZED) {
                        createdRecord = record
                        chosenRate = rate
                        chosenSource = source
                        break
                    } else {
                        record.release()
                    }
                } catch (e: Exception) {
                    // Try next configuration
                }
            }
            if (createdRecord != null) break
        }

        if (createdRecord == null) {
            onError("Microphone hardware failed to initialize with supported configurations. Check permissions.")
            return false
        }

        try {
            createdRecord.startRecording()
            if (createdRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                createdRecord.release()
                onError("Microphone is currently held by another app or system audio service.")
                return false
            }

            audioRecord = createdRecord
            activeSampleRate = chosenRate
            activeSource = chosenSource
            isRecordingFlag.set(true)
            _isRecording.value = true
            recordStartTime = SystemClock.elapsedRealtime()

            val pcmFile = File(targetWavFile.parentFile, "recording_temp.pcm")
            pcmFile.parentFile?.mkdirs()
            tempPcmFile = pcmFile

            recordThread = Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)

                val shortBuffer = ShortArray(1024)
                val byteBuffer = ByteArray(2048)
                val byteBufWrapper = ByteBuffer.wrap(byteBuffer).order(ByteOrder.LITTLE_ENDIAN)

                var outStream: BufferedOutputStream? = null
                try {
                    outStream = BufferedOutputStream(FileOutputStream(pcmFile), 8192)

                    while (isRecordingFlag.get()) {
                        val read = createdRecord.read(shortBuffer, 0, shortBuffer.size)
                        if (read > 0) {
                            byteBufWrapper.clear()
                            for (i in 0 until read) {
                                byteBufWrapper.putShort(shortBuffer[i])
                            }

                            val bytesToWrite = read * 2
                            outStream.write(byteBuffer, 0, bytesToWrite)

                            val level = VoiceDspEngine.calculateRmsLevel(shortBuffer, read)
                            _amplitude.value = level

                            val elapsed = SystemClock.elapsedRealtime() - recordStartTime
                            _durationMs.value = elapsed
                        } else if (read < 0) {
                            break
                        }
                    }
                    outStream.flush()
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    try {
                        outStream?.close()
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }, "VoiceRecorderAudioThread").apply { start() }

            return true
        } catch (e: Exception) {
            createdRecord.release()
            onError("Microphone error: ${e.message}")
            return false
        }
    }

    fun stopRecording(
        targetWavFile: File,
        onSuccess: (File, sampleRate: Int, diagnostic: AudioDiagnostic) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isRecordingFlag.get() && audioRecord == null) {
            onError("No recording in progress.")
            return
        }

        isRecordingFlag.set(false)
        _isRecording.value = false
        _amplitude.value = 0f

        // Wait for the audio thread to cleanly flush and close the PCM stream
        try {
            recordThread?.join(2000)
        } catch (e: Exception) {
            // ignore
        }
        recordThread = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            // ignore
        } finally {
            audioRecord = null
        }

        val pcmFile = tempPcmFile
        if (pcmFile == null || !pcmFile.exists() || pcmFile.length() == 0L) {
            onError("No audio data was captured. Please check microphone permission and speak again.")
            return
        }

        val pcmLength = pcmFile.length()
        val totalSamples = pcmLength / 2
        val durationSec = totalSamples.toDouble() / activeSampleRate

        if (durationSec < 0.25) {
            pcmFile.delete()
            onError("Recording too short (${String.format("%.1f", durationSec)}s). Speak for at least 1 second.")
            return
        }

        try {
            // Write standard 44-byte WAV file
            targetWavFile.parentFile?.mkdirs()
            FileOutputStream(targetWavFile).use { wavOut ->
                val totalDataLen = pcmLength + 36
                val header = createCanonicalWavHeader(
                    totalAudioLen = pcmLength,
                    totalDataLen = totalDataLen,
                    sampleRate = activeSampleRate,
                    channels = 1,
                    byteRate = (activeSampleRate * 1 * 2).toLong()
                )
                wavOut.write(header)

                FileInputStream(pcmFile).use { pcmIn ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (pcmIn.read(buffer).also { bytesRead = it } != -1) {
                        wavOut.write(buffer, 0, bytesRead)
                    }
                }
                wavOut.flush()
            }

            pcmFile.delete()

            if (!targetWavFile.exists() || targetWavFile.length() <= 44) {
                onError("Failed to save recording as WAV file.")
                return
            }

            // Analyze recorded PCM samples
            val (samples, sRate) = WavAudioUtil.readWavFile(targetWavFile)
            val diagnostic = AudioDiagnostic.analyze(samples, sRate)

            if (diagnostic.isSilent) {
                onError(diagnostic.summary)
                return
            }

            onSuccess(targetWavFile, activeSampleRate, diagnostic)
        } catch (e: Exception) {
            onError("Error saving WAV file: ${e.message}")
        }
    }

    /**
     * Diagnostic test: records exactly 3 seconds of microphone audio,
     * writes to WAV, analyzes the PCM data, and reports the results.
     */
    fun runThreeSecondDiagnosticTest(
        targetWavFile: File,
        scope: CoroutineScope,
        onCountdown: (Int) -> Unit,
        onComplete: (AudioDiagnostic, File) -> Unit,
        onError: (String) -> Unit
    ) {
        val started = startRecording(targetWavFile, onError)
        if (!started) return

        scope.launch(Dispatchers.Main) {
            for (sec in 3 downTo 1) {
                onCountdown(sec)
                delay(1000)
            }
            stopRecording(
                targetWavFile = targetWavFile,
                onSuccess = { file, _, diagnostic ->
                    onComplete(diagnostic, file)
                },
                onError = { err ->
                    onError(err)
                }
            )
        }
    }

    fun cancelRecording() {
        isRecordingFlag.set(false)
        _isRecording.value = false
        _amplitude.value = 0f
        _durationMs.value = 0L

        try {
            recordThread?.interrupt()
            recordThread?.join(500)
        } catch (e: Exception) {
            // ignore
        }
        recordThread = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            // ignore
        } finally {
            audioRecord = null
        }
        tempPcmFile?.delete()
    }

    private fun createCanonicalWavHeader(
        totalAudioLen: Long,
        totalDataLen: Long,
        sampleRate: Int,
        channels: Int,
        byteRate: Long
    ): ByteArray {
        val header = ByteArray(44)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put('R'.code.toByte())
        buffer.put('I'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.putInt(totalDataLen.toInt())
        buffer.put('W'.code.toByte())
        buffer.put('A'.code.toByte())
        buffer.put('V'.code.toByte())
        buffer.put('E'.code.toByte())

        buffer.put('f'.code.toByte())
        buffer.put('m'.code.toByte())
        buffer.put('t'.code.toByte())
        buffer.put(' '.code.toByte())
        buffer.putInt(16) // Subchunk1Size for PCM
        buffer.putShort(1.toShort()) // AudioFormat 1 = PCM
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate.toInt())
        buffer.putShort((channels * 2).toShort()) // Block align = 2
        buffer.putShort(16.toShort()) // Bits per sample = 16

        buffer.put('d'.code.toByte())
        buffer.put('a'.code.toByte())
        buffer.put('t'.code.toByte())
        buffer.put('a'.code.toByte())
        buffer.putInt(totalAudioLen.toInt())

        return header
    }
}
