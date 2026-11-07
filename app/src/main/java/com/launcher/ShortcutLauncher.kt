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

        // ---- DIAGNOSTICA: interroga il sistema sulla shortcut ----
        val systemReport = StringBuilder()

        // 1) La shortcut esiste ancora nel LauncherApps?
        try {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val query = LauncherApps.ShortcutQuery().apply {
                setPackage(packageName)
                setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                )
            }
            val shortcuts = launcherApps.getShortcuts(query, Process.myUserHandle())
            val found = shortcuts?.firstOrNull { it.id == shortcutId }
            if (found != null) {
                systemReport.append("✓ Shortcut trovata nel sistema\n")
                systemReport.append("  id=${found.id}\n")
                systemReport.append("  intent=${found.intent}\n")
                systemReport.append("  intents=${found.intents?.size ?: 0}\n")
                systemReport.append("  isPinned=${if (android.os.Build.VERSION.SDK_INT >= 26) found.isPinned else "?"}\n")
                systemReport.append("  isEnabled=${found.isEnabled}\n")
                found.intents?.forEachIndexed { i, it ->
                    systemReport.append("  intents[$i]=$it\n")
                }
            } else {
                systemReport.append("✗ Shortcut NON trovata nel sistema\n")
                systemReport.append("  Il browser l'ha rimossa? (aggiornamento, pulizia cache)\n")
                systemReport.append("  Shortcuts disponibili per $packageName: ${shortcuts?.size ?: 0}\n")
                shortcuts?.forEachIndexed { i, s ->
                    systemReport.append("    [$i] id=${s.id} isPinned=${if (android.os.Build.VERSION.SDK_INT >= 26) s.isPinned else "?"}\n")
                }
            }
        } catch (e: Exception) {
            systemReport.append("✗ Errore query LauncherApps: ${e.javaClass.simpleName}: ${e.message}\n")
        }

        // 2) Intent salvato
        val savedIntentUri = PrefsSync.shortcutIntents(context)[uniqueKey]
        systemReport.append("\nIntent salvato:\n")
        systemReport.append(if (savedIntentUri.isNullOrEmpty()) "  (null/vuoto)\n" else "  $savedIntentUri\n")

        // ---- PROVA AD APRIRE CON L'INTENT SALVATO ----
        var launchResult = "non tentato"
        if (!savedIntentUri.isNullOrEmpty()) {
            try {
                val intent = Intent.parseUri(savedIntentUri, Intent.URI_INTENT_SCHEME)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // Verifica se c'è un handler
                val resolveInfo = context.packageManager.resolveActivity(intent, 0)
                if (resolveInfo == null) {
                    launchResult = "nessuna Activity risolve l'intent"
                } else {
                    context.startActivity(intent)
                    launchResult = "startActivity OK → ${resolveInfo.activityInfo.packageName}/${resolveInfo.activityInfo.name}"
                    debug(context, "LANCIO CON INTENT SALVATO", "$systemReport\n\nRisultato: $launchResult")
                    return
                }
            } catch (e: Exception) {
                launchResult = "ECCEZIONE: ${e.javaClass.simpleName}: ${e.message}"
            }
        }

        // ---- FALLBACK: startShortcut (per vedere cosa dice) ----
        var shortcutError = "non tentato"
        try {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            launcherApps.startShortcut(packageName, shortcutId, null, null, Process.myUserHandle())
            shortcutError = "OK (nessuna eccezione, ma il sistema potrebbe comunque rifiutare)"
        } catch (e: Exception) {
            shortcutError = "ECCEZIONE: ${e.javaClass.simpleName}: ${e.message}"
        }

        debug(
            context,
            "DIAGNOSTICA SHORTCUT",
            "$systemReport\n" +
            "Intent salvato: $launchResult\n" +
            "startShortcut: $shortcutError\n\n" +
            "uniqueKey=$uniqueKey"
        )
    }
}
