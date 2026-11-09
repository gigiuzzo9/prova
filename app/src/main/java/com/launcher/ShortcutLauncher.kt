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
        "com.heytap.browser", "com.transsion.phoenix", "com.vivo.browser",
        "io.github.forkmaintainers.iceraven"
    )

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
        // 1) Controlla il data diretto
        intent.data?.let { d ->
            if (isValidHttpString(d.toString())) return d.toString()
            // Se è un intent:// (tipico di Firefox), estrai il fallback
            if (d.scheme == "intent") {
                try {
                    val parsed = Intent.parseUri(d.toString(), Intent.URI_INTENT_SCHEME)
                    parsed.data?.let { if (isValidHttpString(it.toString())) return it.toString() }
                    val fb = parsed.getStringExtra("browser_fallback_url")
                    if (isValidHttpString(fb)) return fb
                } catch (_: Exception) {}
            }
        }

        // 2) Controlla gli extra (fondamentale per Firefox)
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
            
            // Scansiona TUTTI gli extra per trovare una stringa che assomiglia a un URL
            for (k in extras.keySet()) {
                val v = extras.get(k)
                if (v is String && isValidHttpString(v)) return v
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

        // 1. Estrai l'URL (per i browser)
        var url: String? = null
        if (!savedIntentUri.isNullOrEmpty()) {
            try {
                val parsed = Intent.parseUri(savedIntentUri, Intent.URI_INTENT_SCHEME)
                url = extractHttpUrl(parsed)
            } catch (_: Exception) {}
        }

        // 2. Se è un browser e abbiamo l'URL, apri il link in modo standard
        if (packageName in BROWSER_PACKAGES && url != null) {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(browserIntent)
                return
            } catch (e: Exception) {
                // Forza il package se il browser predefinito non basta
                try {
                    val forcedIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        setPackage(packageName)
                        addCategory(Intent.CATEGORY_BROWSABLE)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(forcedIntent)
                    return
                } catch (e2: Exception) {}
            }
        }

        // 3. Prova startShortcut (per le app normali e come fallback)
        try {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            launcherApps.startShortcut(packageName, shortcutId, null, null, Process.myUserHandle())
            return
        } catch (_: Exception) {}

        // 4. Fallback: apri l'Intent salvato così com'è
        if (!savedIntentUri.isNullOrEmpty()) {
            try {
                val pi = Intent.parseUri(savedIntentUri, Intent.URI_INTENT_SCHEME)
                pi.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(pi)
                return
            } catch (_: Exception) {}
        }

        // 5. Ultimo fallback: apri l'app
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return
            }
        } catch (_: Exception) {}

        if (showErrorToast) {
            Toast.makeText(context.applicationContext, "Impossibile avviare la scorciatoia", Toast.LENGTH_SHORT).show()
        }
    }
}
