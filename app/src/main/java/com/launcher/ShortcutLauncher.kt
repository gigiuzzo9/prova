package com.simplylauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Process
import android.widget.Toast

object ShortcutLauncher {

    private fun debug(context: Context, title: String, message: String) {
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
        if (shortcutId.isEmpty()) {
            AppRepository.launchApp(context, packageName)
            return
        }

        val uniqueKey = when {
            instanceId.isEmpty() -> "$packageName|$shortcutId"
            else -> "$packageName|$shortcutId|$instanceId"
        }

        val savedIntentUri = PrefsSync.shortcutIntents(context)[uniqueKey]

        // 1) Prova a parsare l'intent salvato
        var parsedIntent: Intent? = null
        if (!savedIntentUri.isNullOrEmpty()) {
            try {
                parsedIntent = Intent.parseUri(savedIntentUri, Intent.URI_INTENT_SCHEME)
            } catch (_: Exception) {}
        }

        // 2) È un collegamento browser? (action=VIEW + data http/https)
        val isBrowserLink = parsedIntent != null &&
                parsedIntent.action == Intent.ACTION_VIEW &&
                parsedIntent.data?.scheme?.let { it == "http" || it == "https" } == true

        // 3) Se è un browser link, usa SOLO l'intent salvato
        if (isBrowserLink) {
            try {
                parsedIntent!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(parsedIntent)
                return
            } catch (e: Exception) {
                if (showErrorToast) {
                    debug(context, "ERRORE BROWSER LINK", "${e.javaClass.simpleName}: ${e.message}")
                }
                return
            }
        }

        // 4) Se è una shortcut normale, prova startShortcut
        try {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            launcherApps.startShortcut(packageName, shortcutId, null, null, Process.myUserHandle())
            return
        } catch (_: Exception) {}

        // 5) Fallback: apri l'app
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return
            }
        } catch (_: Exception) {}

        if (showErrorToast) {
            debug(context, "TUTTO FALLITO", "pkg=$packageName id=$shortcutId")
        }
    }
}
