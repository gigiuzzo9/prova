package com.simplylauncher

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Process
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

        val uniqueKey = when {
            instanceId.isEmpty() -> "$packageName|$shortcutId"
            else -> "$packageName|$shortcutId|$instanceId"
        }

        val savedData = PrefsSync.shortcutIntents(context)[uniqueKey]

        // 1. Se abbiamo salvato un URL puro (es. "url:https://..."), aprilo subito
        if (savedData != null && savedData.startsWith("url:")) {
            val url = savedData.removePrefix("url:")
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            if (tryStart(context, browserIntent)) return
            
            // Se fallisce (raro), apri forzando il browser specifico
            val forcedIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                setPackage(packageName)
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            if (tryStart(context, forcedIntent)) return
        }

        // 2. Prova l'API startShortcut (per le app normali)
        try {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            launcherApps.startShortcut(packageName, shortcutId, null, null, Process.myUserHandle())
            return
        } catch (_: Exception) {}

        // 3. Fallback: se abbiamo l'Intent originale salvato
        if (savedData != null && !savedData.startsWith("url:")) {
            try {
                val pi = Intent.parseUri(savedData, Intent.URI_INTENT_SCHEME)
                if (tryStart(context, pi)) return
            } catch (_: Exception) {}
        }

        // 4. Fallback: apri l'app (solo se non è un browser)
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null && tryStart(context, launchIntent)) return
        } catch (_: Exception) {}

        if (showErrorToast) {
            Toast.makeText(context.applicationContext, "Impossibile avviare la scorciatoia", Toast.LENGTH_SHORT).show()
        }
    }
}
