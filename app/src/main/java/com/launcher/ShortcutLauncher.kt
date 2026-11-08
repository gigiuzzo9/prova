package com.simplylauncher

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Process
import android.widget.Toast

object ShortcutLauncher {

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

    private fun isValidHttpString(s: String?): Boolean {
        if (s.isNullOrEmpty()) return false
        return try {
            val u = Uri.parse(s)
            u.scheme?.lowercase()?.let { it == "http" || it == "https" } == true
        } catch (_: Exception) {
            false
        }
    }

    private fun extractHttpUrl(intent: Intent): String? {
        intent.data?.let { d ->
            if (isHttpUrl(d)) return d.toString()
            if (d.scheme == "intent") {
                try {
                    val parsed = Intent.parseUri(d.toString(), Intent.URI_INTENT_SCHEME)
                    parsed.data?.let { if (isHttpUrl(it)) return it.toString() }
                    val fb = parsed.getStringExtra("browser_fallback_url")
                    if (isValidHttpString(fb)) return fb
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
                if (isValidHttpString(v)) return v
            }
            for (k in extras.keySet()) {
                val v = extras.get(k)
                if (v is String && isValidHttpString(v)) return v
            }
        }
        return null
    }

    private fun tryStart(context: Context, intent: Intent): Boolean {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun tryStartShortcut(
        context: Context,
        packageName: String,
        shortcutId: String
    ): Boolean {
        return try {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            launcherApps.startShortcut(packageName, shortcutId, null, null, Process.myUserHandle())
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

        val savedIntentUri = PrefsSync.shortcutIntents(context)[uniqueKey]

        // ─── RAMO BROWSER ───
        if (packageName in BROWSER_PACKAGES) {
            // Estrai URL dall'Intent salvato (se presente)
            var url: String? = null
            if (!savedIntentUri.isNullOrEmpty()) {
                val parsed: Intent? = try {
                    Intent.parseUri(savedIntentUri, Intent.URI_INTENT_SCHEME)
                } catch (_: Exception) {
                    null
                }
                if (parsed != null) {
                    url = extractHttpUrl(parsed)
                }
            }

            // ★ MODIFICA 1: Firefox / IceRaven usano l'azione VIEW_PWA
            if (packageName == "org.mozilla.firefox" || packageName.contains("iceraven")) {
                if (url != null) {
                    val pwaIntent = Intent("mozilla.components.feature.pwa.VIEW_PWA").apply {
                        setPackage(packageName)
                        data = Uri.parse(url)
                    }
                    if (tryStart(context, pwaIntent)) return
                }
            }

            // Tentativo con ACTION_VIEW vincolato al browser della shortcut
            if (url != null) {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    setPackage(packageName)
                }
                if (tryStart(context, browserIntent)) return
            }

            // ★ MODIFICA 2: fallback a startShortcut() per i browser
            if (tryStartShortcut(context, packageName, shortcutId)) return

            // Fallback: intent salvato così com'è
            if (!savedIntentUri.isNullOrEmpty()) {
                try {
                    val pi = Intent.parseUri(savedIntentUri, Intent.URI_INTENT_SCHEME)
                    if (tryStart(context, pi)) return
                } catch (_: Exception) {}
            }

            // Ultimo: apri la home del browser
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null && tryStart(context, launchIntent)) return
            } catch (_: Exception) {}

            if (showErrorToast) {
                Toast.makeText(
                    context.applicationContext,
                    "Impossibile aprire il link. Rimuovi l'icona e ripinna dal browser.",
                    Toast.LENGTH_LONG
                ).show()
            }
            return
        }

        // ─── RAMO APP / SHORTCUT ACTIVITY ───
        if (tryStartShortcut(context, packageName, shortcutId)) return

        if (!savedIntentUri.isNullOrEmpty()) {
            try {
                val pi = Intent.parseUri(savedIntentUri, Intent.URI_INTENT_SCHEME)
                if (tryStart(context, pi)) return
            } catch (_: Exception) {}
        }

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
