package com.simplylauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Process
import android.widget.Toast

object ShortcutLauncher {

    private fun debug(context: Context, title: String, message: String) {
        // Se è Activity → AlertDialog (persistente, serve OK)
        if (context is Activity) {
            context.runOnUiThread {
                AlertDialog.Builder(context)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .setCancelable(false)
                    .show()
            }
        } else {
            // Fallback: Toast lunghissimo (non ideale ma visibile)
            Toast.makeText(context.applicationContext, "$title\n$message", Toast.LENGTH_LONG).show()
        }
    }

    fun launch(
        context: Context,
        packageName: String,
        shortcutId: String,
        instanceId: String = "",
        showErrorToast: Boolean = true
    ) {
        // LOG SUBITO: se non vedi questo dialog, launch() non viene chiamato
        debug(
            context,
            "DEBUG 0 — launch() invocato",
            "pkg=$packageName\nid=$shortcutId\ninst=$instanceId\ncontext=${context.javaClass.name}"
        )

        if (shortcutId.isEmpty()) {
            debug(context, "DEBUG — shortcutId VUOTO", "Chiamo AppRepository.launchApp")
            AppRepository.launchApp(context, packageName)
            return
        }

        val uniqueKey = when {
            instanceId.isEmpty() -> "$packageName|$shortcutId"
            else -> "$packageName|$shortcutId|$instanceId"
        }

        val savedIntentUri = PrefsSync.shortcutIntents(context)[uniqueKey]
        debug(
            context,
            "DEBUG 1 — intent salvato",
            "uniqueKey=$uniqueKey\nsavedIntentUri=${savedIntentUri ?: "(null)"}"
        )

        // STEP 1: startShortcut
        try {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            launcherApps.startShortcut(packageName, shortcutId, null, null, Process.myUserHandle())
            debug(context, "DEBUG 2 — startShortcut OK", "Nessuna eccezione. Se il link non parte, il browser ha rifiutato.")
            return
        } catch (e: Exception) {
            debug(context, "DEBUG 2 — startShortcut FALLITO", "${e.javaClass.name}: ${e.message}")
        }

        // STEP 2: fallback intent
        if (!savedIntentUri.isNullOrEmpty()) {
            try {
                val intent = Intent.parseUri(savedIntentUri, Intent.URI_INTENT_SCHEME)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                debug(context, "DEBUG 3 — fallback OK", savedIntentUri)
                return
            } catch (e: Exception) {
                debug(context, "DEBUG 3 — fallback FALLITO", "${e.javaClass.name}: ${e.message}")
            }
        }

        // STEP 3: apri app
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                debug(context, "DEBUG 4 — apro app", "Non il link, solo l'app.")
                return
            }
        } catch (_: Exception) {}

        debug(context, "DEBUG 5 — TUTTO FALLITO", "Nessun metodo ha funzionato")
    }
}
