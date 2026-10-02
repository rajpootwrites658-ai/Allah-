package com.example.core.audio

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Detailed diagnostic information for recorded raw PCM audio.
 */
data class AudioDiagnostic(
    val sampleRate: Int,
    val channelCount: Int,
    val audioEncoding: String,
    val durationMs: Long,
    val sampleCount: Int,
    val minSample: Int,
    val maxSample: Int,
    val peakAmplitude: Int,
    val rmsPercent: Float,
    val distinctValuesCount: Int,
    val isSilent: Boolean,
    val isConstantOrDC: Boolean,
    val isClipped: Boolean,
    val isValidVoice: Boolean,
    val summary: String
) {
    companion object {
        fun analyze(samples: ShortArray, sampleRate: Int = 44100): AudioDiagnostic {
            if (samples.isEmpty()) {
                return AudioDiagnostic(
                    sampleRate = sampleRate,
                    channelCount = 1,
                    audioEncoding = "16-bit PCM Mono (Little-Endian)",
                    durationMs = 0L,
                    sampleCount = 0,
                    minSample = 0,
                    maxSample = 0,
                    peakAmplitude = 0,
                    rmsPercent = 0f,
                    distinctValuesCount = 0,
                    isSilent = true,
                    isConstantOrDC = true,
                    isClipped = false,
                    isValidVoice = false,
                    summary = "No audio samples recorded."
                )
            }

            var min = Int.MAX_VALUE
            var max = Int.MIN_VALUE
            var sumSquare = 0.0
            val sampleSet = HashSet<Short>()

            for (s in samples) {
                val v = s.toInt()
                if (v < min) min = v
                if (v > max) max = v
                sumSquare += (v.toDouble() * v.toDouble())

                if (sampleSet.size < 500) {
                    sampleSet.add(s)
                }
            }

            val peak = maxOf(abs(min), abs(max))
            val rms = sqrt(sumSquare / samples.size)
            val rmsPct = ((rms / 32768.0) * 100.0).toFloat().coerceIn(0f, 100f)
            val duration = (samples.size.toDouble() / sampleRate * 1000.0).toLong()

            val isSilent = peak < 200
            val isConstantOrDC = sampleSet.size < 15
            val isClipped = min <= -32760 || max >= 32760
            val isValidVoice = !isSilent && !isConstantOrDC && samples.size >= (sampleRate * 0.3)

            val summaryText = when {
                isSilent -> "SILENCE: Microphone captured no audible speech (Peak: $peak / 32767). Check microphone volume or permissions."
                isConstantOrDC -> "CONSTANT/DC: Audio signal is frozen or corrupted without wave changes ($sampleSet.size distinct samples)."
                isClipped -> "CLIPPED: Volume too loud! Samples clipped at maximum scale (Min: $min, Max: $max)."
                isValidVoice -> "VALID VOICE: Real speech wave captured! Rate: ${sampleRate}Hz | 16-bit Mono | Samples: ${samples.size} | Peak: $peak | RMS: ${String.format("%.1f", rmsPct)}%"
                else -> "Audio captured: ${samples.size} samples, duration ${duration}ms"
            }

            return AudioDiagnostic(
                sampleRate = sampleRate,
                channelCount = 1,
                audioEncoding = "16-bit PCM Mono (Little-Endian)",
                durationMs = duration,
                sampleCount = samples.size,
                minSample = min,
                maxSample = max,
                peakAmplitude = peak,
                rmsPercent = rmsPct,
                distinctValuesCount = sampleSet.size,
                isSilent = isSilent,
                isConstantOrDC = isConstantOrDC,
                isClipped = isClipped,
                isValidVoice = isValidVoice,
                summary = summaryText
            )
        }
    }
}
