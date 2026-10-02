package com.example.core.sharing

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.core.audio.VoiceEffect
import com.example.core.audio.WavAudioUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Legitimate, policy-compliant Android Intent sharing for voice notes.
 * Directly integrates WhatsApp, Instagram, Messenger/Facebook, and standard Android system share.
 */
enum class ShareTarget(val appName: String, val packageName: String?) {
    YALLA_LUDO("Yalla Ludo", "com.yalla.yallaludo"),
    WHATSAPP("WhatsApp", "com.whatsapp"),
    INSTAGRAM("Instagram", "com.instagram.android"),
    FACEBOOK_MESSENGER("Messenger", "com.facebook.orca"),
    ALL_APPS("More Apps", null)
}

object VoiceShareManager {

    /**
     * Prepares and transforms the recorded audio file into a clean WAV file,
     * then launches safe Android ACTION_SEND Intent to the target application.
     */
    suspend fun shareVoiceNote(
        context: Context,
        sourceWavFile: File,
        effect: VoiceEffect,
        finePitchMultiplier: Float = 1.0f,
        target: ShareTarget
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!sourceWavFile.exists() || sourceWavFile.length() == 0L) {
                return@withContext Result.failure(IllegalStateException("No recorded voice note found"))
            }

            val audioDir = File(context.cacheDir, "audio").apply { mkdirs() }
            val outputFile = File(audioDir, "voicenote_${effect.id}.wav")

            // Transform audio to destination file
            val success = WavAudioUtil.transformWav(
                inputFile = sourceWavFile,
                outputFile = outputFile,
                effect = effect,
                finePitchMultiplier = finePitchMultiplier
            )

            if (!success || !outputFile.exists()) {
                return@withContext Result.failure(IllegalStateException("Failed to transform audio file"))
            }

            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outputFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/wav"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "Voice Note (${effect.displayName})")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            withContext(Dispatchers.Main) {
                if (target.packageName != null) {
                    val pm = context.packageManager
                    val launchIntent = pm.getLaunchIntentForPackage(target.packageName)
                    if (launchIntent != null) {
                        shareIntent.setPackage(target.packageName)
                        try {
                            context.startActivity(shareIntent)
                            return@withContext
                        } catch (e: ActivityNotFoundException) {
                            // Target app not available, fall back to chooser below
                            Toast.makeText(
                                context,
                                "${target.appName} not installed. Opening share options...",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        Toast.makeText(
                            context,
                            "${target.appName} not installed. Opening share options...",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                // Standard Android chooser
                val chooser = Intent.createChooser(shareIntent, "Share Voice Note with").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
