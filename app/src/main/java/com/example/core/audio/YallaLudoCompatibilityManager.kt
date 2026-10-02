package com.example.core.audio

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

data class YallaLudoDiagnostics(
    val isInstalled: Boolean,
    val installedPackageName: String?,
    val hasMicPermission: Boolean,
    val isMicInConflict: Boolean,
    val canInjectVirtualMic: Boolean = false,
    val injectionRestrictionReason: String,
    val recommendedWorkflow: String
)

object YallaLudoCompatibilityManager {

    const val YALLA_LUDO_PKG = "com.yalla.yallaludo"
    const val YALLA_LUDO_HD_PKG = "com.yalla.yallaludohd"

    fun getInstalledPackageName(context: Context): String? {
        val pm = context.packageManager
        return when {
            isPackageInstalled(pm, YALLA_LUDO_PKG) -> YALLA_LUDO_PKG
            isPackageInstalled(pm, YALLA_LUDO_HD_PKG) -> YALLA_LUDO_HD_PKG
            else -> null
        }
    }

    fun isYallaLudoInstalled(context: Context): Boolean {
        return getInstalledPackageName(context) != null
    }

    fun launchYallaLudo(context: Context): Boolean {
        val pkg = getInstalledPackageName(context) ?: return false
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun runDiagnostics(
        context: Context,
        hasMicPermission: Boolean,
        isMicInConflict: Boolean
    ): YallaLudoDiagnostics {
        val pkg = getInstalledPackageName(context)
        val isInstalled = pkg != null

        val reason = "Standard Android AOSP security strictly isolates the hardware microphone stream per process. " +
                "Android does not provide a public API for non-system apps to inject modified audio into another app's VoIP stream without root or custom audio HAL drivers."

        val workflow = "Use Live Voice Passthrough (speaker/headphone mode) or send transformed voice notes directly to Yalla Ludo via standard Android sharing."

        return YallaLudoDiagnostics(
            isInstalled = isInstalled,
            installedPackageName = pkg,
            hasMicPermission = hasMicPermission,
            isMicInConflict = isMicInConflict,
            canInjectVirtualMic = false,
            injectionRestrictionReason = reason,
            recommendedWorkflow = workflow
        )
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
