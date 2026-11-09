package com.simplylauncher

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.UserHandle
import android.widget.Toast

object ShortcutLauncher {

    private fun tryStart(context: Context, intent: Intent): Boolean {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
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

        val uniqueKey = if (instanceId.isEmpty()) "$packageName|$shortcutId" else "$packageName|$shortcutId|$instanceId"
        val savedData = PrefsSync.shortcutIntents(context)[uniqueKey] ?: ""

        // 1. Se abbiamo salvato un URL puro, aprilo subito
        if (savedData.startsWith("url:")) {
            val url = savedData.removePrefix("url:")
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            if (tryStart(context, browserIntent)) return
        }

        // 2. Se abbiamo salvato l'Intent serializzato (Legacy - Iceraven)
        if (savedData.contains("://") || savedData.contains("intent:")) {
            try {
                val parsedIntent = Intent.parseUri(savedData, 0)
                if (tryStart(context, parsedIntent)) return
            } catch (_: Exception) {}
        }

        // 3. Se abbiamo salvato package|id|user (Moderno - Chrome) come fa Discreet
        if (savedData.contains("|")) {
            val parts = savedData.split("|")
            if (parts.size == 3) {
                val pkg = parts[0]
                val id = parts[1]
                val userIdStr = parts[2]
                
                try {
                    val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                    // QUI USIAMO L'ID UTENTE ESATTO COME DISCREET
                    val userId = userIdStr.toIntOrNull() ?: 0
                    val userHandle = UserHandle.getUserHandleForUid(userId)
                    
                    launcherApps.startShortcut(pkg, id, null, null, userHandle)
                    return
                } catch (_: Exception) {}
            }
        }

        // 4. Fallback: apri l'app
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null && tryStart(context, launchIntent)) return
        } catch (_: Exception) {}

        if (showErrorToast) {
            Toast.makeText(context.applicationContext, "Impossibile avviare la scorciatoia", Toast.LENGTH_SHORT).show()
        }
    }
}
