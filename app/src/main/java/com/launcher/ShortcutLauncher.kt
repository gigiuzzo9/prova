package com.simplylauncher

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Process
import android.widget.Toast

object ShortcutLauncher {

    /**
     * Package dei browser noti. Per queste shortcut NON usiamo mai startShortcut(),
     * perché le shortcut web puntano a trampoline interni al browser non esportati.
     * Estraiamo l'URL e lanciamo ACTION_VIEW pulito.
     */
    private val BROWSER_PACKAGES = setOf(
        "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
        "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.focus",
        "com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser.beta",
        "com.brave.browser", "com.microsoft.emmx",
        "com.opera.browser", "com.opera.mini.native",
        "com.duckduckgo.mobile.android", "org.chromium.chrome",
        "com.android.browser", "com.mi.globalbrowser",
        "com.heytap.browser", "com.transsion.phoenix", "com.vivo.browser"
    )

    private fun isHttpUrl(uri: Uri?): Boolean {
        val scheme = uri?.scheme?.lowercase() ?: return false
        return scheme == "http" || scheme == "https"
    }

    /**
     * Estrae un URL http/https dall'Intent, provando tutte le fonti note.
     */
    private fun extractHttpUrl(intent: Intent): String? {
        // 1) data diretto
        intent.data?.let { d ->
            if (isHttpUrl(d)) return d.toString()

            // 2) intent://... → prova a estrarre il fallback
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
                "S.browser_fallback_url", "browser_fallback_url",
                "url", "URL", "uri", "URI", "web_url", "webUrl",
                Intent.EXTRA_TEXT
            )
            for (k in known) {
                val v = extras.getString(k)
                if (!v.isNullOrEmpty() && (v.startsWith("http://") || v.startsWith("https://"))) return v
            }
            // 4) qualunque extra stringa che inizi con http
            for (k in extras.keySet()) {
                val v = extras.get(k)
                if (v is String && (v.startsWith("http://") || v.startsWith("https://"))) return v
            }
        }
        return null
    }

    /** Apre un Intent con FLAG_ACTIVITY_NEW_TASK e ritorna true se riesce. */
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
        // Senza shortcutId è una normale icona app
        if (shortcutId.isBlank()) {
            AppRepository.launchApp(context, packageName)
            return
        }

        val uniqueKey = when {
            instanceId.isEmpty() -> "$packageName|$shortcutId"
            else -> "$packageName|$shortcutId|$instanceId"
        }

        val savedIntentUri = PrefsSync.shortcutIntents(context)[uniqueKey]

        // ═══════ RAMO BROWSER: mai startShortcut, solo ACTION_VIEW ═══════
        if (packageName in BROWSER_PACKAGES) {
            // 1) Prova a estrarre l'URL dall'Intent salvato
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
                        }
                        if (tryStart(context, browserIntent)) return
                    }

                    // 2) Nessun URL: prova ad aprire il data dell'intent senza component/package
                    if (parsed.data != null) {
                        val cleaned = Intent(Intent.ACTION_VIEW, parsed.data).apply {
                            addCategory(Intent.CATEGORY_BROWSABLE)
                            parsed.extras?.let { putExtras(it) }
                        }
                        if (tryStart(context, cleaned)) return
                    }
                }
            }

            // 3) Fallback estremo: apri il browser senza link specifico
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null && tryStart(context, launchIntent)) return
            } catch (_: Exception) {}

            // 4) Non ce l'abbiamo fatta: messaggio chiaro all'utente
            if (showErrorToast) {
                Toast.makeText(
                    context.applicationContext,
                    "Impossibile aprire il link. Il browser non ha salvato l'URL del pin.\n" +
                    "Rimuovi l'icona e ripinna il link dal browser.",
                    Toast.LENGTH_LONG
                ).show()
            }
            return
        }

        // ═══════ RAMO APP / SHORTCUT ACTIVITY: identico a prima ═══════
        // 1) startShortcut nativo
        try {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            launcherApps.startShortcut(packageName, shortcutId, null, null, Process.myUserHandle())
            return
        } catch (_: Exception) {}

        // 2) Fallback: intent salvato così com'è
        if (!savedIntentUri.isNullOrEmpty()) {
            try {
                val pi = Intent.parseUri(savedIntentUri, Intent.URI_INTENT_SCHEME)
                if (tryStart(context, pi)) return
            } catch (_: Exception) {}
        }

        // 3) Fallback finale: apri l'app
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null && tryStart(context, launchIntent)) return
        } catch (_: Exception) {}

        // 4) Errore
        if (showErrorToast) {
            Toast.makeText(
                context.applicationContext,
                "Impossibile avviare la scorciatoia",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
