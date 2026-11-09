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

        // 0. Shortcut LEGACY: l'URI dell'Intent è salvato direttamente
        //    (Firefox e browser vecchi). Provalo per primo.
        if (savedData != null && savedData.startsWith("intent:")) {
            try {
                val pi = Intent.parseUri(savedData, Intent.URI_INTENT_SCHEME)
                // Se l'Intent ha un URL nei dati, prova ad aprirlo direttamente
                if (pi.data != null && (pi.action == Intent.ACTION_VIEW || pi.action == null)) {
                    val viewIntent = Intent(Intent.ACTION_VIEW, pi.data).apply {
                        addCategory(Intent.CATEGORY_BROWSABLE)
                        // preserva il package originale se specificato
                        pi.`package`?.let { setPackage(it) }
                    }
                    if (tryStart(context, viewIntent)) return
                }
                // Altrimenti lancia l'Intent così com'è
                if (tryStart(context, pi)) return
            } catch (_: Exception) {}
        }

        // 1. URL puro (es. "url:https://...")
        if (savedData != null && savedData.startsWith("url:")) {
            val url = savedData.removePrefix("url:")
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            if (tryStart(context, browserIntent)) return

            val forcedIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                setPackage(packageName)
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            if (tryStart(context, forcedIntent)) return
        }

        // 2. startShortcut (per app moderne — Chrome, ecc.)
        try {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            launcherApps.startShortcut(packageName, shortcutId, null, null, Process.myUserHandle())
            return
        } catch (_: Exception) {}

        // 3. Fallback: se abbiamo l'Intent originale salvato (non-URL)
        if (savedData != null && !savedData.startsWith("url:") && !savedData.startsWith("intent:")) {
            try {
                val pi = Intent.parseUri(savedData, Intent.URI_INTENT_SCHEME)
                if (tryStart(context, pi)) return
            } catch (_: Exception) {}
        }

        // 4. Fallback finale: apri l'app
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null && tryStart(context, launchIntent)) return
        } catch (_: Exception) {}

        if (showErrorToast) {
            Toast.makeText(
                context.applicationContext,
                "Impossibile avviare la scorciatoia",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
