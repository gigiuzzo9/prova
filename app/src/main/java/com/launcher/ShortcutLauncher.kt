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

        // Parsing dell'Intent salvato (nullable, ma lo gestiamo con let)
        val parsedIntent: Intent? = if (!savedIntentUri.isNullOrEmpty()) {
            try {
                Intent.parseUri(savedIntentUri, Intent.URI_INTENT_SCHEME)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }

        // --- Gestione link web, con let: pi è NON-NULL qui dentro ---
        if (parsedIntent != null) {
            parsedIntent.let { pi ->
                val isWebLink = pi.action == Intent.ACTION_VIEW && isHttpUrl(pi.data)
                if (isWebLink) {
                    val url = pi.data

                    // 2a) Intent salvato
                    try {
                        pi.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        pi.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                        context.startActivity(pi)
                        return
                    } catch (_: Exception) {}

                    // 2b) URL diretto
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

                    // 2c) Stringa URL
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
            }
        }

        // --- Scorciatoia normale → startShortcut ---
        try {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            launcherApps.startShortcut(packageName, shortcutId, null, null, Process.myUserHandle())
            return
        } catch (_: Exception) {}

        // --- Fallback: usa l'Intent salvato se disponibile ---
        if (parsedIntent != null) {
            parsedIntent.let { pi ->
                try {
                    pi.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(pi)
                    return
                } catch (_: Exception) {}
            }
        }

        // --- Ultimo fallback: apri l'app ---
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
