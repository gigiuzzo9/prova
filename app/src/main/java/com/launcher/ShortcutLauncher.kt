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

    // Package dei browser più comuni. Per questi NON usiamo startShortcut,
    // ma estraiamo l'URL e lanciamo ACTION_VIEW.
    private val BROWSER_PACKAGES = setOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary",
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "org.mozilla.focus",
        "com.sec.android.app.sbrowser",
        "com.sec.android.app.sbrowser.beta",
        "com.brave.browser",
        "com.microsoft.emmx",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.duckduckgo.mobile.android",
        "org.chromium.chrome",
        "com.android.browser",
        "com.mi.globalbrowser",
        "com.heytap.browser",
        "com.transsion.phoenix",
        "com.vivo.browser"
    )

    private fun isHttpUrl(uri: Uri?): Boolean {
        val scheme = uri?.scheme?.lowercase() ?: return false
        return scheme == "http" || scheme == "https"
    }

    private fun showError(context: Context, title: String, message: String) {
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
     * Estrae un URL http/https da un Intent, provando tutte le fonti note.
     */
    private fun extractHttpUrl(intent: Intent): String? {
        intent.data?.let { d ->
            if (isHttpUrl(d)) return d.toString()
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
        val extras = intent.extras
        if (extras != null) {
            val known = listOf(
                "S.browser_fallback_url", "browser_fallback_url",
                "url", "URL", "uri", "URI", "web_url", "webUrl",
                Intent.EXTRA_TEXT
            )
            for (k in known) {
                val v = extras.getString(k)
                if (!v.isNullOrEmpty() && (v.startsWith("http://") || v.startsWith("https://"))) return v
            }
            for (k in extras.keySet()) {
                val v = extras.get(k)
                if (v is String && (v.startsWith("http://") || v.startsWith("https://"))) return v
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

        // === RAMO BROWSER: non usiamo startShortcut, estraiamo l'URL ===
        if (packageName in BROWSER_PACKAGES) {
            // Prova a estrarre l'URL dall'Intent salvato
            if (!savedIntentUri.isNullOrEmpty()) {
                val parsed: Intent? = try {
                    Intent.parseUri(savedIntentUri, Intent.URI_INTENT_SCHEME)
                } catch (_: Exception) {
                    null
                }

                if (parsed != null) {
                    val url = extractHttpUrl(parsed)
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
                            if (showErrorToast) {
                                showError(context, "ERRORE LINK WEB", "URL: $url\n\n${e.message}")
                            }
                            return
                        }
                    }

                    // Nessun URL estratto: prova a lanciare l'Intent salvato ma
                    // SENZA component/package (che spesso bloccano l'avvio)
                    if (parsed.data != null) {
                        val cleaned = Intent(Intent.ACTION_VIEW, parsed.data).apply {
                            addCategory(Intent.CATEGORY_BROWSABLE)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            parsed.extras?.let { putExtras(it) }
                        }
                        try {
                            context.startActivity(cleaned)
                            return
                        } catch (e: Exception) {
                            if (showErrorToast) {
                                showError(context, "ERRORE LINK WEB", "Impossibile avviare il link.\n\n${e.message}")
                            }
                            return
                        }
                    }

                    // Ultimo tentativo: apri il browser (non il link specifico)
                    try {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(launchIntent)
                            return
                        }
                    } catch (_: Exception) {}
                }
            }

            if (showErrorToast) {
                showError(context, "LINK WEB NON DISPONIBILE",
                    "Non è stato possibile recuperare l'URL salvato.\n" +
                    "Prova a rimuovere e ripinnare il link dal browser.")
            }
            return
        }

        // === RAMO NORMALE (app, shortcut Activity): identico a prima ===
        try {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            launcherApps.startShortcut(packageName, shortcutId, null, null, Process.myUserHandle())
            return
        } catch (_: Exception) {}

        // Fallback: intent salvato
        if (!savedIntentUri.isNullOrEmpty()) {
            try {
                val pi = Intent.parseUri(savedIntentUri, Intent.URI_INTENT_SCHEME)
                pi.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(pi)
                return
            } catch (_: Exception) {}
        }

        // Fallback finale: apri l'app
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return
            }
        } catch (_: Exception) {}

        if (showErrorToast) {
            showError(context, "SCORCIATOIA NON AVVIABILE", "pkg=$packageName\nid=$shortcutId")
        }
    }
}
