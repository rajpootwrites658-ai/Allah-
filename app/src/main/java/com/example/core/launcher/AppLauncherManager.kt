package com.example.core.launcher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class TargetAppInfo(
    val id: String,
    val defaultName: String,
    val packageCandidates: List<String>,
    val category: String
)

data class LaunchableApp(
    val id: String,
    val name: String,
    val packageName: String,
    val isInstalled: Boolean,
    val iconDrawable: Drawable? = null,
    val category: String = "App"
)

object AppLauncherManager {

    val TARGET_APPS = listOf(
        TargetAppInfo(
            id = "yalla_ludo",
            defaultName = "Yalla Ludo",
            packageCandidates = listOf("com.yalla.yallaludo", "com.yalla.yallaludohd"),
            category = "Gaming & Voice Chat"
        ),
        TargetAppInfo(
            id = "whatsapp",
            defaultName = "WhatsApp",
            packageCandidates = listOf("com.whatsapp", "com.whatsapp.w4b"),
            category = "Messaging"
        ),
        TargetAppInfo(
            id = "tiktok",
            defaultName = "TikTok",
            packageCandidates = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
            category = "Social & Video"
        ),
        TargetAppInfo(
            id = "instagram",
            defaultName = "Instagram",
            packageCandidates = listOf("com.instagram.android"),
            category = "Social"
        ),
        TargetAppInfo(
            id = "messenger",
            defaultName = "Messenger",
            packageCandidates = listOf("com.facebook.orca"),
            category = "Messaging"
        ),
        TargetAppInfo(
            id = "telegram",
            defaultName = "Telegram",
            packageCandidates = listOf("org.telegram.messenger"),
            category = "Messaging"
        )
    )

    /**
     * Scans for target apps and retrieves their actual installed names and icons.
     */
    fun getTargetApps(context: Context): List<LaunchableApp> {
        val pm = context.packageManager
        return TARGET_APPS.map { target ->
            var installedPkg: String? = null
            var appName = target.defaultName
            var icon: Drawable? = null

            for (pkg in target.packageCandidates) {
                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    installedPkg = pkg
                    appName = pm.getApplicationLabel(appInfo).toString()
                    icon = pm.getApplicationIcon(appInfo)
                    break
                } catch (e: PackageManager.NameNotFoundException) {
                    // Try next candidate
                }
            }

            val finalPkg = installedPkg ?: target.packageCandidates.first()
            LaunchableApp(
                id = target.id,
                name = appName,
                packageName = finalPkg,
                isInstalled = installedPkg != null,
                iconDrawable = icon,
                category = target.category
            )
        }
    }

    /**
     * Launches the requested app using standard official Android launch Intent.
     */
    fun launchApp(context: Context, packageName: String): Result<Unit> {
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(packageName)
            ?: return Result.failure(ActivityNotFoundException("App not installed: $packageName"))

        return try {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
