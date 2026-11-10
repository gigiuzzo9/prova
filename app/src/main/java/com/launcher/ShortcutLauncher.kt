package com.simplylauncher

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.UserHandle
import android.util.Log
import android.widget.Toast

object ShortcutLauncher {

    private const val TAG = "ShortcutLauncher"
    private const val SEP = "|"

    private fun tryStart(context: Context, intent: Intent): Boolean {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "tryStart fallito", e)
            false
        }
    }

    fun launch(
        context: Context,
        packageName: String,
        shortcutId: String,
        instanceId: String = "",
        showErrorToast: Boolean = true
    ) {
        if (shortcutId.isBlank()) {
            AppRepository.launchApp(context, packageName)
            return
        }

        val map = PrefsSync.shortcutIntents(context)
        // La shortcutId che arriva qui è la chiave univoca salvata da ShortcutReceiverActivity
        val savedData = map[shortcutId] ?: ""
        Log.d(TAG, "launch: key=$shortcutId savedData=$savedData")

        // URL puro
        if (savedData.startsWith("url:")) {
            val url = savedData.removePrefix("url:")
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                if (tryStart(context, browserIntent)) {
                    Log.d(TAG, "Avviato via URL: $url")
                    return
                }
            } catch (e: Exception) {
                Log.e(TAG, "Apertura URL fallita: $url", e)
            }
        }

        // Formato canonico
        if (savedData.contains(SEP)) {
            val parts = savedData.split(SEP)
            if (parts.size >= 2) {
                val pkg = parts[0]
                val id = parts[1]
                val userId = parts.getOrNull(2)?.toIntOrNull() ?: 0
                val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

                if (!launcherApps.hasShortcutHostPermission()) {
                    if (showErrorToast) {
                        Toast.makeText(context, "Imposta SimplyLauncher come launcher predefinito", Toast.LENGTH_LONG).show()
                    }
                    return
                }
                try {
                    launcherApps.startShortcut(pkg, id, null, null, UserHandle.getUserHandleForUid(userId))
                    Log.d(TAG, "startShortcut OK: $pkg/$id")
                    return
                } catch (e: Exception) {
                    Log.e(TAG, "startShortcut fallito", e)
                }
            }
        }

        // Fallback app
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null && tryStart(context, launchIntent)) {
                Log.w(TAG, "Fallback app")
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallback launchIntent fallito", e)
        }

        if (showErrorToast) {
            Toast.makeText(context, "Impossibile avviare la scorciatoia", Toast.LENGTH_SHORT).show()
        }
    }
}
