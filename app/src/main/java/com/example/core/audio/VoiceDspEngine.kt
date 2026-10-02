package com.example.core.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-quality Digital Signal Processing (DSP) engine for voice transformation.
 * - Circular dual-delay line granular pitch transposition.
 * - Smooth Hann crossfading with sum-of-weights = 1.0.
 * - Integrated DC-blocking filter (cuts sub-20Hz DC bias).
 * - Stable FIR vocal presence shaping for Natural Female and Young Boy.
 * - Exact 100% bit-exact passthrough for Original Voice.
 */
class VoiceDspEngine(
    private val sampleRate: Int = 44100
) {
    private val bufferSize = 2048
    private val halfBuffer = bufferSize / 2f

    // Streaming state
    private val rtRingBuffer = FloatArray(bufferSize)
    private var rtWriteIdx = 0
    private var rtTap1 = 0.0
    private var rtTap2 = halfBuffer.toDouble()
    private var rtDcX1 = 0f
    private var rtDcY1 = 0f
    private var rtEqX1 = 0f

    fun reset() {
        rtRingBuffer.fill(0f)
        rtWriteIdx = 0
        rtTap1 = 0.0
        rtTap2 = halfBuffer.toDouble()
        rtDcX1 = 0f
        rtDcY1 = 0f
        rtEqX1 = 0f
    }

    /**
     * Transforms an entire recorded PCM audio buffer.
     */
    fun transformBuffer(
        input: ShortArray,
        effect: VoiceEffect,
        finePitchMultiplier: Float = 1.0f
    ): ShortArray {
        if (input.isEmpty()) return ShortArray(0)

        val targetPitch = (effect.pitchFactor * finePitchMultiplier).coerceIn(0.5f, 2.0f)

        // Exact bit-level passthrough for Original Voice
        if (effect == VoiceEffect.ORIGINAL && abs(targetPitch - 1.0f) < 0.005f) {
            return input.clone()
        }

        val count = input.size
        val output = ShortArray(count)

        val ring = FloatArray(bufferSize)
        var writeIdx = 0
        var tap1 = 0.0
        var tap2 = halfBuffer.toDouble()
        val rateDelta = 1.0 - targetPitch.toDouble()

        var dcX1 = 0f
        var dcY1 = 0f
        var eqX1 = 0f

        for (n in 0 until count) {
            val sample = input[n].toFloat()
            ring[writeIdx] = sample

            // Read Tap 1
            val readPos1 = ((writeIdx - tap1) % bufferSize + bufferSize) % bufferSize
            val i1 = floor(readPos1).toInt()
            val frac1 = (readPos1 - i1).toFloat()
            val s1 = ring[i1] * (1.0f - frac1) + ring[(i1 + 1) % bufferSize] * frac1

            val phi1 = tap1 / bufferSize
            val w1 = 0.5 * (1.0 - cos(2.0 * Math.PI * phi1))

            // Read Tap 2
            val readPos2 = ((writeIdx - tap2) % bufferSize + bufferSize) % bufferSize
            val i2 = floor(readPos2).toInt()
            val frac2 = (readPos2 - i2).toFloat()
            val s2 = ring[i2] * (1.0f - frac2) + ring[(i2 + 1) % bufferSize] * frac2

            val w2 = 1.0 - w1

            // Advance taps
            tap1 = (tap1 + rateDelta) % bufferSize
            if (tap1 < 0.0) tap1 += bufferSize

            tap2 = (tap2 + rateDelta) % bufferSize
            if (tap2 < 0.0) tap2 += bufferSize

            writeIdx = (writeIdx + 1) % bufferSize

            val pitchShifted = (s1 * w1 + s2 * w2).toFloat()

            // DC-blocking filter (cuts sub-20Hz DC bias)
            val dcOut = pitchShifted - dcX1 + 0.995f * dcY1
            dcX1 = pitchShifted
            dcY1 = dcOut

            // Vocal presence shaping
            val shaped = when (effect) {
                VoiceEffect.NATURAL_FEMALE -> {
                    val out = 0.90f * dcOut - 0.18f * eqX1
                    eqX1 = dcOut
                    out
                }
                VoiceEffect.YOUNG_BOY -> {
                    val out = 0.92f * dcOut - 0.12f * eqX1
                    eqX1 = dcOut
                    out
                }
                VoiceEffect.DEEP_MAN -> {
                    val out = 0.78f * dcOut + 0.22f * eqX1
                    eqX1 = dcOut
                    out
                }
                VoiceEffect.ROBOT -> {
                    val carrier = sin(2.0 * PI * 180.0 * n / sampleRate).toFloat()
                    dcOut * (0.35f + 0.65f * carrier)
                }
                else -> dcOut
            }

            output[n] = shaped.toInt().coerceIn(-32768, 32767).toShort()
        }

        return output
    }

    /**
     * Real-time streaming process for live mic playback.
     */
    fun processStream(
        input: ShortArray,
        output: ShortArray = ShortArray(input.size),
        count: Int = input.size,
        effect: VoiceEffect,
        finePitchMultiplier: Float = 1.0f
    ): ShortArray {
        val targetPitch = (effect.pitchFactor * finePitchMultiplier).coerceIn(0.5f, 2.0f)

        if (effect == VoiceEffect.ORIGINAL && abs(targetPitch - 1.0f) < 0.005f) {
            System.arraycopy(input, 0, output, 0, count)
            return output
        }

        val rateDelta = 1.0 - targetPitch.toDouble()

        for (i in 0 until count) {
            val sample = input[i].toFloat()
            rtRingBuffer[rtWriteIdx] = sample

            val readPos1 = ((rtWriteIdx - rtTap1) % bufferSize + bufferSize) % bufferSize
            val i1 = floor(readPos1).toInt()
            val frac1 = (readPos1 - i1).toFloat()
            val s1 = rtRingBuffer[i1] * (1.0f - frac1) + rtRingBuffer[(i1 + 1) % bufferSize] * frac1

            val phi1 = rtTap1 / bufferSize
            val w1 = 0.5 * (1.0 - cos(2.0 * Math.PI * phi1))

            val readPos2 = ((rtWriteIdx - rtTap2) % bufferSize + bufferSize) % bufferSize
            val i2 = floor(readPos2).toInt()
            val frac2 = (readPos2 - i2).toFloat()
            val s2 = rtRingBuffer[i2] * (1.0f - frac2) + rtRingBuffer[(i2 + 1) % bufferSize] * frac2

            val w2 = 1.0 - w1

            rtTap1 = (rtTap1 + rateDelta) % bufferSize
            if (rtTap1 < 0.0) rtTap1 += bufferSize

            rtTap2 = (rtTap2 + rateDelta) % bufferSize
            if (rtTap2 < 0.0) rtTap2 += bufferSize

            rtWriteIdx = (rtWriteIdx + 1) % bufferSize

            val pitchShifted = (s1 * w1 + s2 * w2).toFloat()

            // DC-blocker
            val dcOut = pitchShifted - rtDcX1 + 0.995f * rtDcY1
            rtDcX1 = pitchShifted
            rtDcY1 = dcOut

            output[i] = dcOut.toInt().coerceIn(-32768, 32767).toShort()
        }

        return output
    }

    companion object {
        fun calculateRmsLevel(samples: ShortArray, count: Int = samples.size): Float {
            if (count == 0) return 0f
            var sum = 0.0
            for (i in 0 until count) {
                val s = samples[i] / 32768.0
                sum += s * s
            }
            val rms = sqrt(sum / count).toFloat()
            return min(1.0f, rms * 5.0f)
        }
    }
}
