package com.example

import com.example.core.audio.VoiceDspEngine
import com.example.core.audio.VoiceEffect
import com.example.core.audio.VoicePlayer
import com.example.core.audio.WavAudioUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VoiceChangerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testVoiceEffectsHaveValidParameters() {
        val effects = VoiceEffect.values()
        assertTrue(effects.isNotEmpty())

        val femaleEffect = VoiceEffect.NATURAL_FEMALE
        assertEquals("female", femaleEffect.id)
        assertTrue("Female pitch must be higher than 1.0", femaleEffect.pitchFactor > 1.0f)
        assertTrue(femaleEffect.trebleBoostDb > 0f)

        val boyEffect = VoiceEffect.YOUNG_BOY
        assertEquals("boy", boyEffect.id)
        assertTrue("Boy pitch must be higher than 1.0", boyEffect.pitchFactor > 1.0f)
        assertTrue("Female pitch is higher than boy pitch", femaleEffect.pitchFactor > boyEffect.pitchFactor)

        val originalEffect = VoiceEffect.ORIGINAL
        assertEquals(1.0f, originalEffect.pitchFactor, 0.001f)

        val deepEffect = VoiceEffect.DEEP_MAN
        assertTrue("Deep pitch must be lower than 1.0", deepEffect.pitchFactor < 1.0f)
    }

    @Test
    fun testVoiceTransformationOnRecordedAudio() {
        val dsp = VoiceDspEngine(sampleRate = 44100)
        // Synthesize 0.5s of 200Hz tone (simulating vocal fundamental)
        val sampleCount = 22050
        val input = ShortArray(sampleCount) { i ->
            (sin(2.0 * Math.PI * 200.0 * i / 44100.0) * 16000).toInt().toShort()
        }

        // 1. Natural Female Transformation
        val femaleTransformed = dsp.transformBuffer(input, VoiceEffect.NATURAL_FEMALE)
        assertEquals("Transformed audio duration should match input", input.size, femaleTransformed.size)

        var differentFromInput = false
        for (i in 0 until minOf(input.size, femaleTransformed.size)) {
            if (input[i] != femaleTransformed[i]) {
                differentFromInput = true
                break
            }
        }
        assertTrue("Natural Female transformation must modify the audio", differentFromInput)

        // 2. Young Boy Transformation
        val boyTransformed = dsp.transformBuffer(input, VoiceEffect.YOUNG_BOY)
        assertEquals("Transformed audio duration should match input", input.size, boyTransformed.size)

        var boyDifferent = false
        for (i in 0 until minOf(input.size, boyTransformed.size)) {
            if (input[i] != boyTransformed[i]) {
                boyDifferent = true
                break
            }
        }
        assertTrue("Young Boy transformation must modify the audio", boyDifferent)

        // 3. Original Voice (should preserve input)
        val originalOut = dsp.transformBuffer(input, VoiceEffect.ORIGINAL)
        assertEquals(input.size, originalOut.size)
        assertEquals(input[100], originalOut[100])
    }

    @Test
    fun testRealtimeStreamProcessing() {
        val dsp = VoiceDspEngine(sampleRate = 44100)
        val input = ShortArray(1024) { (it * 20 % 25000).toShort() }
        val output = ShortArray(1024)

        dsp.processStream(
            input = input,
            output = output,
            count = 1024,
            effect = VoiceEffect.NATURAL_FEMALE
        )

        var hasNonZero = false
        for (sample in output) {
            if (sample != 0.toShort()) hasNonZero = true
        }
        assertTrue("Output stream should contain non-zero audio samples", hasNonZero)

        val rms = VoiceDspEngine.calculateRmsLevel(input)
        assertTrue("RMS level should be normalized between 0 and 1", rms in 0f..1f)
    }

    @Test
    fun testWavReadAndWrite() {
        val file = File(tempFolder.root, "test_audio.wav")
        val originalSamples = ShortArray(2048) { (it * 15 % 20000).toShort() }

        WavAudioUtil.writeWavFile(file, originalSamples, sampleRate = 44100)
        assertTrue(file.exists())
        assertTrue("WAV file must be larger than 44-byte header", file.length() > 44)

        val (readSamples, sampleRate) = WavAudioUtil.readWavFile(file)
        assertEquals(44100, sampleRate)
        assertEquals(originalSamples.size, readSamples.size)
        assertEquals(originalSamples[100], readSamples[100])
    }

    @Test
    fun testWavTransformationAndPlaybackLoading() {
        val sourceFile = File(tempFolder.root, "raw_mic.wav")
        val transformedFile = File(tempFolder.root, "transformed_female.wav")
        val samples = ShortArray(44100) { (sin(2.0 * Math.PI * 300.0 * it / 44100.0) * 18000).toInt().toShort() }

        WavAudioUtil.writeWavFile(sourceFile, samples, sampleRate = 44100)

        // Transform to Natural Female
        val success = WavAudioUtil.transformWav(
            inputFile = sourceFile,
            outputFile = transformedFile,
            effect = VoiceEffect.NATURAL_FEMALE
        )

        assertTrue("Transforming WAV must succeed", success)
        assertTrue(transformedFile.exists())
        assertTrue(transformedFile.length() > 44)

        // Test that VoicePlayer loads the transformed audio
        val player = VoicePlayer()
        val loaded = player.loadAudio(transformedFile)
        assertTrue("VoicePlayer must successfully load transformed audio", loaded)
        assertEquals("Duration should be approximately 1000ms", 1000L, player.totalDurationMs.value)
        assertEquals("Initial playback position must be 0", 0L, player.playbackPositionMs.value)
        assertFalse("Player must not start in playing state automatically", player.isPlaying.value)

        player.release()
    }

    @Test
    fun testEmptyOrInvalidAudioHandling() {
        val nonExistentFile = File(tempFolder.root, "does_not_exist.wav")
        val outputFile = File(tempFolder.root, "output.wav")

        val result = WavAudioUtil.transformWav(nonExistentFile, outputFile, VoiceEffect.NATURAL_FEMALE)
        assertFalse("Transforming non-existent file must return false", result)

        val player = VoicePlayer()
        val loaded = player.loadAudio(nonExistentFile)
        assertFalse("Loading non-existent file in player must return false", loaded)

        player.release()
    }

    @Test
    fun testAppLauncherManagerTargetAppsList() {
        val targets = com.example.core.launcher.AppLauncherManager.TARGET_APPS
        assertTrue(targets.isNotEmpty())

        val yalla = targets.find { it.id == "yalla_ludo" }
        org.junit.Assert.assertNotNull("Yalla Ludo must be in target apps", yalla)
        assertTrue(yalla!!.packageCandidates.contains("com.yalla.yallaludo"))

        val tiktok = targets.find { it.id == "tiktok" }
        org.junit.Assert.assertNotNull("TikTok must be in target apps", tiktok)
        assertTrue(tiktok!!.packageCandidates.contains("com.zhiliaoapp.musically"))

        val whatsapp = targets.find { it.id == "whatsapp" }
        org.junit.Assert.assertNotNull("WhatsApp must be in target apps", whatsapp)
        assertTrue(whatsapp!!.packageCandidates.contains("com.whatsapp"))

        val instagram = targets.find { it.id == "instagram" }
        org.junit.Assert.assertNotNull("Instagram must be in target apps", instagram)
        assertTrue(instagram!!.packageCandidates.contains("com.instagram.android"))
    }

    @Test
    fun testAppLauncherLaunchNonExistentFailsGracefully() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val result = com.example.core.launcher.AppLauncherManager.launchApp(context, "com.nonexistent.fake.app")
        assertFalse("Launching non-existent app must fail gracefully", result.isSuccess)
        assertTrue(result.exceptionOrNull() is android.content.ActivityNotFoundException)
    }

    @Test
    fun testAudioDiagnosticAnalysis() {
        val sampleRate = 44100
        val sampleCount = sampleRate * 3 // 3 seconds
        val voiceSamples = ShortArray(sampleCount) { i ->
            val t = i.toDouble() / sampleRate
            (sin(2.0 * Math.PI * 300.0 * t) * 15000.0).toInt().toShort()
        }

        // Test normal voice analysis
        val diag = com.example.core.audio.AudioDiagnostic.analyze(voiceSamples, sampleRate)
        assertEquals(44100, diag.sampleRate)
        assertEquals(1, diag.channelCount)
        assertEquals("16-bit PCM Mono (Little-Endian)", diag.audioEncoding)
        assertEquals(3000L, diag.durationMs)
        assertEquals(sampleCount, diag.sampleCount)
        assertTrue("Min sample must be negative", diag.minSample < -10000)
        assertTrue("Max sample must be positive", diag.maxSample > 10000)
        assertTrue("RMS must be in valid voice range", diag.rmsPercent in 5f..50f)
        assertTrue("Wave must have changing samples", diag.distinctValuesCount > 50)
        assertTrue("Must detect valid voice", diag.isValidVoice)
        assertFalse("Must not be silent", diag.isSilent)
        assertFalse("Must not be constant DC", diag.isConstantOrDC)

        // Test silence detection
        val silentSamples = ShortArray(sampleCount) { 0 }
        val silentDiag = com.example.core.audio.AudioDiagnostic.analyze(silentSamples, sampleRate)
        assertTrue("Must detect silence", silentDiag.isSilent)
        assertFalse("Must reject silence as valid voice", silentDiag.isValidVoice)

        // Test constant DC bias detection
        val dcSamples = ShortArray(sampleCount) { 500 }
        val dcDiag = com.example.core.audio.AudioDiagnostic.analyze(dcSamples, sampleRate)
        assertTrue("Must detect constant DC bias", dcDiag.isConstantOrDC)
        assertFalse("Must reject constant DC as valid voice", dcDiag.isValidVoice)
    }

    @Test
    fun testEndToEndRecordPlayAndTransform() {
        // Step 1: Simulate 3 seconds of voice input (250Hz fundamental + harmonics)
        val sampleRate = 44100
        val sampleCount = sampleRate * 3 // Exactly 3 seconds of audio
        val voiceSamples = ShortArray(sampleCount) { i ->
            val t = i.toDouble() / sampleRate
            val fundamental = sin(2.0 * Math.PI * 250.0 * t) * 12000.0
            val harmonic1 = sin(2.0 * Math.PI * 500.0 * t) * 6000.0
            val harmonic2 = sin(2.0 * Math.PI * 750.0 * t) * 3000.0
            (fundamental + harmonic1 + harmonic2).toInt().toShort()
        }

        // Diagnostic verification on raw PCM samples
        val diag = com.example.core.audio.AudioDiagnostic.analyze(voiceSamples, sampleRate)
        assertTrue("Diagnostic must confirm valid voice data", diag.isValidVoice)
        assertEquals(44100, diag.sampleRate)
        assertEquals(1, diag.channelCount)
        assertEquals(3000L, diag.durationMs)
        assertEquals(sampleCount, diag.sampleCount)
        assertTrue("Min sample must be negative", diag.minSample < -10000)
        assertTrue("Max sample must be positive", diag.maxSample > 10000)
        assertTrue("RMS must be valid", diag.rmsPercent in 5f..50f)
        assertTrue("Distinct values must indicate dynamic wave changes", diag.distinctValuesCount > 50)

        val rawFile = File(tempFolder.root, "raw_recording.wav")
        WavAudioUtil.writeWavFile(rawFile, voiceSamples, sampleRate)
        assertTrue("Raw recording file must exist", rawFile.exists())
        assertEquals(44 + sampleCount * 2L, rawFile.length())

        // Verify exact Canonical WAV header fields
        val fileBytes = rawFile.readBytes()
        val buffer = java.nio.ByteBuffer.wrap(fileBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val riff = ByteArray(4).also { buffer.get(it) }
        assertEquals("RIFF", String(riff))
        val riffLength = buffer.getInt()
        assertEquals(rawFile.length() - 8, riffLength.toLong())
        val wave = ByteArray(4).also { buffer.get(it) }
        assertEquals("WAVE", String(wave))
        val fmt = ByteArray(4).also { buffer.get(it) }
        assertEquals("fmt ", String(fmt))
        val subchunk1Size = buffer.getInt()
        assertEquals(16, subchunk1Size) // PCM format subchunk size
        val audioFormat = buffer.getShort().toInt()
        assertEquals(1, audioFormat) // 1 = PCM
        val numChannels = buffer.getShort().toInt()
        assertEquals(1, numChannels) // 1 = Mono
        val readSampleRate = buffer.getInt()
        assertEquals(44100, readSampleRate)
        val byteRate = buffer.getInt()
        assertEquals(88200, byteRate) // 44100 * 1 * 2
        val blockAlign = buffer.getShort().toInt()
        assertEquals(2, blockAlign) // 1 * 2
        val bitsPerSample = buffer.getShort().toInt()
        assertEquals(16, bitsPerSample)

        // Verify read back PCM format and sample fidelity
        val (readSamples, readRate) = WavAudioUtil.readWavFile(rawFile)
        assertEquals(sampleRate, readRate)
        assertEquals(voiceSamples.size, readSamples.size)
        assertEquals(voiceSamples[0], readSamples[0])
        assertEquals(voiceSamples[1000], readSamples[1000])

        // Step 2: Test playback of original recorded voice FIRST (without any transformation)
        val player = VoicePlayer()
        val originalLoaded = player.loadAudio(rawFile)
        assertTrue("Original voice audio must load successfully", originalLoaded)
        assertEquals(3000L, player.totalDurationMs.value)
        assertEquals(0L, player.playbackPositionMs.value)

        // Step 3: Apply Natural Female transformation and verify transformed audio
        val femaleFile = File(tempFolder.root, "transformed_female.wav")
        val femaleTransformed = WavAudioUtil.transformWav(
            inputFile = rawFile,
            outputFile = femaleFile,
            effect = VoiceEffect.NATURAL_FEMALE
        )
        assertTrue("Natural Female transformation must succeed", femaleTransformed)
        assertTrue(femaleFile.exists())
        assertTrue("Transformed file must have valid audio data", femaleFile.length() > 44)

        val (femaleSamples, femaleRate) = WavAudioUtil.readWavFile(femaleFile)
        assertEquals(sampleRate, femaleRate)
        assertEquals(sampleCount, femaleSamples.size)
        // Verify audio is transformed (not identical to raw mic, but bounded and clear)
        assertNotEquals(voiceSamples[2000], femaleSamples[2000])

        // Test playback of Natural Female transformed voice
        val femalePlayerLoaded = player.loadAudio(femaleFile)
        assertTrue("Natural Female audio must load in player", femalePlayerLoaded)
        assertEquals(3000L, player.totalDurationMs.value)

        // Step 4: Apply Young Boy transformation and verify transformed audio
        val boyFile = File(tempFolder.root, "transformed_boy.wav")
        val boyTransformed = WavAudioUtil.transformWav(
            inputFile = rawFile,
            outputFile = boyFile,
            effect = VoiceEffect.YOUNG_BOY
        )
        assertTrue("Young Boy transformation must succeed", boyTransformed)
        assertTrue(boyFile.exists())

        val (boySamples, _) = WavAudioUtil.readWavFile(boyFile)
        assertEquals(sampleCount, boySamples.size)
        assertNotEquals(voiceSamples[2000], boySamples[2000])

        // Test playback of Young Boy transformed voice
        val boyPlayerLoaded = player.loadAudio(boyFile)
        assertTrue("Young Boy audio must load in player", boyPlayerLoaded)
        assertEquals(3000L, player.totalDurationMs.value)

        player.release()
    }
}
