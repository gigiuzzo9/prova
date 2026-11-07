package com.simplylauncher

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Process
import android.widget.Toast

object ShortcutLauncher {

    fun launch(
        context: Context,
        packageName: String,
        shortcutId: String,
        instanceId: String = "",
        showErrorToast: Boolean = true
    ) {
        if (shortcutId.isEmpty()) {
            AppRepository.launchApp(context, packageName)
            return
        }

        val uniqueKey = when {
            instanceId.isEmpty() -> "$packageName|$shortcutId"
            else -> "$packageName|$shortcutId|$instanceId"
        }

        val savedIntentUri = PrefsSync.shortcutIntents(context)[uniqueKey]

        // 1) Se abbiamo l'Intent originale salvato, usalo SEMPRE per primo.
        //    È più affidabile di startShortcut per le shortcut "pinned" (browser),
        //    che il sistema può aver rimosso silenziosamente.
        if (!savedIntentUri.isNullOrEmpty()) {
            try {
                val fallbackIntent = Intent.parseUri(savedIntentUri, Intent.URI_INTENT_SCHEME)
                fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(fallbackIntent)
                return
            } catch (_: Exception) {}
        }

        // 2) Altrimenti prova startShortcut (shortcut dinamiche/manifest delle app)
        val started = try {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            launcherApps.startShortcut(
                packageName,
                shortcutId,
                null,
                null,
                Process.myUserHandle()
            )
            true
        } catch (_: Exception) {
            false
        }

        if (started) return

        // 3) Ultimo tentativo: apri l'app
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return
            }
        } catch (_: Exception) {}

        if (showErrorToast) {
            Toast.makeText(context, "Impossibile avviare la scorciatoia", Toast.LENGTH_SHORT).show()
        }
    }
}
