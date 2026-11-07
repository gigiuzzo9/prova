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

    private fun isHttpUrl(uri: Uri?): Boolean {
        val scheme = uri?.scheme?.lowercase() ?: return false
        return scheme == "http" || scheme == "https"
    }

    private fun showDialog(context: Context, title: String, message: String) {
        if (context is Activity) {
            if (context.isFinishing || context.isDestroyed) return
            context.runOnUiThread {
                if (context.isFinishing || context.isDestroyed) return@runOnUiThread
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

    /**
     * Estrae un URL http/https dall'Intent, provando tutte le fonti note.
     */
    private fun extractHttpUrl(intent: Intent): String? {
        // 1) data diretto
        intent.data?.let { d ->
            if (isHttpUrl(d)) return d.toString()

            // 2) intent:// → estrai fallback
            if (d.scheme == "intent") {
                try {
                    val parsed = Intent.parseUri(d.toString(), Intent.URI_INTENT_SCHEME)
                    parsed.data?.let { if (isHttpUrl(it)) return it.toString() }
                    parsed.getStringExtra("browser_fallback_url")?.let {
                        if (it.startsWith("http")) return it
                    }
                } catch (_: Exception) {}
            }
        }

        // 3) extras noti
        val extras = intent.extras
        if (extras != null) {
            val known = listOf(
                "S.browser_fallback_url",
                "browser_fallback_url",
                "url", "URL", "uri", "URI", "web_url", "webUrl",
                Intent.EXTRA_TEXT
            )
            for (k in known) {
                val v = extras.getString(k)
                if (!v.isNullOrEmpty() && (v.startsWith("http://") || v.startsWith("https://"))) {
                    return v
                }
            }
            // 4) qualunque extra stringa che inizi con http
            for (k in extras.keySet()) {
                val v = extras.get(k)
                if (v is String && (v.startsWith("http://") || v.startsWith("https://"))) {
                    return v
                }
            }
        }
        return null
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

        val savedIntentUri = PrefsSync.shortcutIntents(context)[uniqueKey]

        // Diagnostica: accumuliamo info per eventuale dialog finale
        val diag = StringBuilder()
        diag.append("pkg = ").append(packageName).append('\n')
        diag.append("shortcutId = ").append(shortcutId).append('\n')
        diag.append("instanceId = ").append(instanceId).append('\n')
        diag.append("uniqueKey = ").append(uniqueKey).append('\n')
        diag.append("savedIntentUri = ").append(savedIntentUri ?: "<null>").append("\n\n")

        // Se non c'è nulla di salvato, è un problema a monte (ShortcutReceiverActivity)
        if (savedIntentUri.isNullOrEmpty()) {
            diag.append("⚠ Nessun intent salvato per questa shortcut.\n")
            diag.append("Il problema è nel receiver del pin: il browser non ha fornito un intent.\n")
            diag.append("Serve correggere ShortcutReceiverActivity per estrarre l'URL da extras/label.\n")
            if (showErrorToast) showDialog(context, "DIAGNOSI SCORCIATOIA", diag.toString())
            return
        }

        val parsed: Intent? = try {
            Intent.parseUri(savedIntentUri, Intent.URI_INTENT_SCHEME)
        } catch (e: Exception) {
            diag.append("⚠ parseUri FALLITO: ").append(e.message).append('\n')
            if (showErrorToast) showDialog(context, "DIAGNOSI SCORCIATOIA", diag.toString())
            return
        }

        if (parsed != null) {
            diag.append("action = ").append(parsed.action).append('\n')
            diag.append("data = ").append(parsed.data).append('\n')
            diag.append("data.scheme = ").append(parsed.data?.scheme).append('\n')
            diag.append("component = ").append(parsed.component).append('\n')
            diag.append("package = ").append(parsed.`package`).append('\n')
            diag.append("categories = ").append(parsed.categories).append('\n')
            diag.append("extras keys = ").append(parsed.extras?.keySet()).append("\n\n")

            val url = extractHttpUrl(parsed)
            diag.append("URL estratto = ").append(url ?: "<nessuno>").append("\n\n")

            // Tentativo 1: URL estratto → Intent nuovo e pulito
            if (url != null) {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                }
                try {
                    context.startActivity(browserIntent)
                    return
                } catch (e: Exception) {
                    diag.append("⚠ Tentativo 1 (URL pulito) fallito:\n")
                    diag.append("  ").append(e.javaClass.simpleName).append(": ").append(e.message).append("\n\n")
                }
            }

            // Tentativo 2: Intent salvato così com'è
            try {
                parsed.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(parsed)
                return
            } catch (e: Exception) {
                diag.append("⚠ Tentativo 2 (intent salvato) fallito:\n")
                diag.append("  ").append(e.javaClass.simpleName).append(": ").append(e.message).append("\n\n")
            }

            // Tentativo 3: rimuovi component/package e riprova come VIEW
            if (parsed.data != null) {
                try {
                    val cleaned = Intent(Intent.ACTION_VIEW, parsed.data).apply {
                        addCategory(Intent.CATEGORY_BROWSABLE)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        // copia extras
                        parsed.extras?.let { putExtras(it) }
                    }
                    context.startActivity(cleaned)
                    return
                } catch (e: Exception) {
                    diag.append("⚠ Tentativo 3 (pulito da component) fallito:\n")
                    diag.append("  ").append(e.javaClass.simpleName).append(": ").append(e.message).append("\n\n")
                }
            }
        }

        // Tentativo 4: startShortcut nativo
        try {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            launcherApps.startShortcut(packageName, shortcutId, null, null, Process.myUserHandle())
            return
        } catch (e: Exception) {
            diag.append("⚠ Tentativo 4 (startShortcut) fallito:\n")
            diag.append("  ").append(e.javaClass.simpleName).append(": ").append(e.message).append("\n\n")
        }

        // Tentativo 5: apri l'app
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                diag.append("(aperti solo l'app, non il link)\n")
                if (showErrorToast) showDialog(context, "DIAGNOSI SCORCIATOIA", diag.toString())
                return
            }
        } catch (e: Exception) {
            diag.append("⚠ Tentativo 5 (app) fallito: ").append(e.message).append('\n')
        }

        if (showErrorToast) showDialog(context, "DIAGNOSI SCORCIATOIA", diag.toString())
    }
}
