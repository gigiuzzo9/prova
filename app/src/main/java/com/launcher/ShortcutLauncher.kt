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

    private fun isHttpUrl(uri: Uri?): Boolean {
        val scheme = uri?.scheme?.lowercase() ?: return false
        return scheme == "http" || scheme == "https"
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

        // 1) Prova a parsare l'Intent salvato
        var parsedIntent: Intent? = null
        if (!savedIntentUri.isNullOrEmpty()) {
            try {
                parsedIntent = Intent.parseUri(savedIntentUri, Intent.URI_INTENT_SCHEME)
            } catch (_: Exception) {}
        }

        // 2) È un collegamento web?
        val pi = parsedIntent
        val isWebLink = pi != null &&
                pi.action == Intent.ACTION_VIEW &&
                isHttpUrl(pi.data)

        if (isWebLink && pi != null) {
            val url = pi.data
            // 2a) Prova con l'Intent salvato
            try {
                pi.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                pi.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                context.startActivity(pi)
                return
            } catch (_: Exception) {}

            // 2b) Fallback: apri direttamente l'URL con ACTION_VIEW
            if (url != null) {
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, url).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    }
                    context.startActivity(browserIntent)
                    return
                } catch (_: Exception) {}
            }

            // 2c) Ultimo tentativo: estrai la stringa URL e apri
            val urlString = url?.toString()
            if (!urlString.isNullOrEmpty()) {
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(urlString)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(browserIntent)
                    return
                } catch (_: Exception) {}
            }

            if (showErrorToast) {
                debug(context, "ERRORE LINK WEB", "Impossibile aprire: ${url ?: savedIntentUri}")
            }
            return
        }

        // 3) Scorciatoia normale → startShortcut
        try {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            launcherApps.startShortcut(packageName, shortcutId, null, null, Process.myUserHandle())
            return
        } catch (_: Exception) {}

        // 4) Fallback: se l'Intent salvato è avviabile, usalo
        if (pi != null) {
            try {
                pi.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(pi)
                return
            } catch (_: Exception) {}
        }

        // 5) Ultimo fallback: apri l'app
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
