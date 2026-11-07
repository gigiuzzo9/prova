package com.simplylauncher

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.widget.Toast

object ShortcutLauncher {

    private fun showDebug(context: Context, title: String, message: String) {
        Handler(Looper.getMainLooper()).post {
            try {
                AlertDialog.Builder(context)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show()
            } catch (_: Exception) {
                // se il context non è un Activity, fallback su Toast lungo
                Toast.makeText(context.applicationContext, "$title\n$message", Toast.LENGTH_LONG).show()
            }
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

        // ---- STEP 1: startShortcut (come il vecchio codice) ----
        var step1Error: String? = null
        try {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            launcherApps.startShortcut(
                packageName,
                shortcutId,
                null,
                null,
                Process.myUserHandle()
            )
            showDebug(
                context,
                "STEP 1 — startShortcut OK",
                "pkg=$packageName\nid=$shortcutId\ninst=$instanceId\n\n" +
                "Se il collegamento non parte comunque, il browser ha rifiutato l'avvio " +
                "(shortcut non più valida nel sistema)."
            )
            return
        } catch (e: Exception) {
            step1Error = "${e.javaClass.simpleName}: ${e.message}"
        }

        // ---- STEP 2: fallback con Intent salvato ----
        var step2Error: String? = null
        if (!savedIntentUri.isNullOrEmpty()) {
            try {
                val intent = Intent.parseUri(savedIntentUri, Intent.URI_INTENT_SCHEME)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                showDebug(
                    context,
                    "STEP 2 — fallback OK",
                    "Intent salvato:\n$savedIntentUri\n\n" +
                    "STEP 1 era fallito con:\n$step1Error"
                )
                return
            } catch (e: Exception) {
                step2Error = "${e.javaClass.simpleName}: ${e.message}"
            }
        }

        // ---- STEP 3: apri l'app ----
        var step3Error: String? = null
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                showDebug(
                    context,
                    "STEP 3 — apertura app OK",
                    "Ho aperto l'app '$packageName' ma NON il collegamento specifico.\n\n" +
                    "STEP 1: $step1Error\n" +
                    "STEP 2: ${step2Error ?: "nessun intent salvato"}\n" +
                    "savedIntentUri=${savedIntentUri ?: "(null)"}"
                )
                return
            } else {
                step3Error = "getLaunchIntentForPackage = null"
            }
        } catch (e: Exception) {
            step3Error = "${e.javaClass.simpleName}: ${e.message}"
        }

        // ---- STEP 4: tutto fallito ----
        showDebug(
            context,
            "TUTTI I TENTATIVI FALLITI",
            "pkg=$packageName\nid=$shortcutId\ninst=$instanceId\n\n" +
            "STEP 1 (startShortcut): $step1Error\n\n" +
            "STEP 2 (fallback intent): ${step2Error ?: "nessun intent salvato"}\n\n" +
            "savedIntentUri=${savedIntentUri ?: "(null)"}\n\n" +
            "STEP 3 (apri app): $step3Error"
        )
    }
}
