package com.example.core.audio

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Robust utility for reading, writing, and transforming 16-bit PCM WAV audio files.
 * Handles standard 44-byte headers, chunk parsing, little-endian alignment, and stereo-to-mono downmixing.
 */
object WavAudioUtil {

    private const val HEADER_SIZE = 44

    /**
     * Writes 16-bit PCM mono samples to a standard WAV file.
     */
    @Throws(IOException::class)
    fun writeWavFile(file: File, samples: ShortArray, sampleRate: Int = 44100) {
        val byteData = ByteArray(samples.size * 2)
        val byteBuffer = ByteBuffer.wrap(byteData).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in samples) {
            byteBuffer.putShort(sample)
        }

        val totalAudioLen = byteData.size.toLong()
        val totalDataLen = totalAudioLen + 36

        file.parentFile?.mkdirs()
        FileOutputStream(file).use { out ->
            val header = createWavHeader(
                totalAudioLen = totalAudioLen,
                totalDataLen = totalDataLen,
                sampleRate = sampleRate,
                channels = 1,
                byteRate = (sampleRate * 1 * 2).toLong()
            )
            out.write(header)
            out.write(byteData)
            out.flush()
        }
    }

    /**
     * Reads 16-bit PCM samples and sample rate from any standard WAV file.
     * Fully reads all bytes into memory to eliminate partial stream read bugs.
     */
    @Throws(IOException::class)
    fun readWavFile(file: File): Pair<ShortArray, Int> {
        if (!file.exists() || file.length() < HEADER_SIZE) {
            return Pair(ShortArray(0), 44100)
        }

        val fileBytes = file.readBytes()
        if (fileBytes.size < HEADER_SIZE) {
            return Pair(ShortArray(0), 44100)
        }

        val buffer = ByteBuffer.wrap(fileBytes).order(ByteOrder.LITTLE_ENDIAN)

        // Verify "RIFF"
        val riff = ByteArray(4)
        buffer.get(riff)
        if (String(riff) != "RIFF") {
            return Pair(ShortArray(0), 44100)
        }

        // Skip FileSize (4 bytes)
        buffer.getInt()

        // Verify "WAVE"
        val wave = ByteArray(4)
        buffer.get(wave)
        if (String(wave) != "WAVE") {
            return Pair(ShortArray(0), 44100)
        }

        var sampleRate = 44100
        var channels = 1
        var bitsPerSample = 16
        var dataOffset = -1
        var dataSize = 0

        // Parse chunks sequentially
        while (buffer.remaining() >= 8) {
            val chunkId = ByteArray(4)
            buffer.get(chunkId)
            val chunkSize = buffer.getInt()
            val chunkName = String(chunkId)

            when (chunkName) {
                "fmt " -> {
                    val audioFormat = buffer.getShort().toInt() // 1 = PCM
                    channels = buffer.getShort().toInt()
                    sampleRate = buffer.getInt()
                    val byteRate = buffer.getInt()
                    val blockAlign = buffer.getShort()
                    bitsPerSample = buffer.getShort().toInt()

                    // Skip any extra format bytes if chunk > 16
                    val extra = chunkSize - 16
                    if (extra > 0 && buffer.remaining() >= extra) {
                        buffer.position(buffer.position() + extra)
                    }
                }
                "data" -> {
                    dataOffset = buffer.position()
                    dataSize = minOf(chunkSize, buffer.remaining())
                    break
                }
                else -> {
                    // Skip unknown chunk
                    if (chunkSize > 0 && buffer.remaining() >= chunkSize) {
                        buffer.position(buffer.position() + chunkSize)
                    } else {
                        break
                    }
                }
            }
        }

        if (dataOffset == -1 || dataSize <= 0) {
            // Fallback: standard 44-byte header offset
            dataOffset = HEADER_SIZE
            dataSize = maxOf(0, fileBytes.size - HEADER_SIZE)
        }

        val safeSampleRate = if (sampleRate in 8000..48000) sampleRate else 44100
        val bytesPerSample = bitsPerSample / 8
        if (bytesPerSample != 2) {
            // Only 16-bit PCM supported
            return Pair(ShortArray(0), safeSampleRate)
        }

        val totalSamples = dataSize / (bytesPerSample * channels)
        val pcmBuffer = ByteBuffer.wrap(fileBytes, dataOffset, dataSize).order(ByteOrder.LITTLE_ENDIAN)

        val monoSamples = ShortArray(totalSamples)
        for (i in 0 until totalSamples) {
            if (channels == 1) {
                monoSamples[i] = pcmBuffer.short
            } else {
                // Downmix stereo to mono
                val left = pcmBuffer.short.toInt()
                val right = pcmBuffer.short.toInt()
                monoSamples[i] = ((left + right) / 2).toShort()
            }
        }

        return Pair(monoSamples, safeSampleRate)
    }

    /**
     * Transforms an input WAV file using the selected VoiceEffect and writes to the destination file.
     */
    @Throws(IOException::class)
    fun transformWav(
        inputFile: File,
        outputFile: File,
        effect: VoiceEffect,
        finePitchMultiplier: Float = 1.0f
    ): Boolean {
        if (!inputFile.exists() || inputFile.length() <= HEADER_SIZE) {
            return false
        }

        val (rawSamples, sampleRate) = readWavFile(inputFile)
        if (rawSamples.isEmpty()) return false

        val dsp = VoiceDspEngine(sampleRate = sampleRate)
        val processedSamples = dsp.transformBuffer(
            input = rawSamples,
            effect = effect,
            finePitchMultiplier = finePitchMultiplier
        )

        if (processedSamples.isEmpty()) return false

        writeWavFile(outputFile, processedSamples, sampleRate)
        return outputFile.exists() && outputFile.length() > HEADER_SIZE
    }

    private fun createWavHeader(
        totalAudioLen: Long,
        totalDataLen: Long,
        sampleRate: Int,
        channels: Int,
        byteRate: Long
    ): ByteArray {
        val header = ByteArray(HEADER_SIZE)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF chunk
        buffer.put('R'.code.toByte())
        buffer.put('I'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.put('F'.code.toByte())
        buffer.putInt(totalDataLen.toInt())
        buffer.put('W'.code.toByte())
        buffer.put('A'.code.toByte())
        buffer.put('V'.code.toByte())
        buffer.put('E'.code.toByte())

        // fmt subchunk
        buffer.put('f'.code.toByte())
        buffer.put('m'.code.toByte())
        buffer.put('t'.code.toByte())
        buffer.put(' '.code.toByte())
        buffer.putInt(16) // SubChunk1Size for PCM
        buffer.putShort(1.toShort()) // AudioFormat 1 = PCM
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate.toInt())
        buffer.putShort((channels * 2).toShort()) // Block align
        buffer.putShort(16.toShort()) // Bits per sample

        // data subchunk
        buffer.put('d'.code.toByte())
        buffer.put('a'.code.toByte())
        buffer.put('t'.code.toByte())
        buffer.put('a'.code.toByte())
        buffer.putInt(totalAudioLen.toInt())

        return header
    }
}
