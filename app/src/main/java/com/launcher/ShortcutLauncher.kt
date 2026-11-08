package com.simplylauncher

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Process
import android.util.Log
import android.widget.Toast

object ShortcutLauncher {

    private const val TAG = "ShortcutLauncher"

    private const val FIREFOX_PWA_ACTION =
        "mozilla.components.feature.pwa.VIEW_PWA"

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

    private fun isFirefoxOrIceraven(packageName: String): Boolean {
        return packageName.startsWith("org.mozilla.firefox") ||
            packageName.contains("iceraven", ignoreCase = true)
    }

    private fun isHttpUrl(uri: Uri?): Boolean {
        val scheme = uri?.scheme?.lowercase() ?: return false
        return scheme == "http" || scheme == "https"
    }

    private fun isValidHttpString(value: String?): Boolean {
        if (value.isNullOrBlank()) return false

        return try {
            isHttpUrl(Uri.parse(value))
        } catch (_: Exception) {
            false
        }
    }

    private fun extractHttpUrl(intent: Intent): String? {
        intent.data?.let { data ->
            if (isHttpUrl(data)) return data.toString()

            if (data.scheme == "intent") {
                try {
                    val parsed = Intent.parseUri(
                        data.toString(),
                        Intent.URI_INTENT_SCHEME
                    )

                    parsed.data?.let {
                        if (isHttpUrl(it)) return it.toString()
                    }

                    val fallback =
                        parsed.getStringExtra("browser_fallback_url")

                    if (isValidHttpString(fallback)) return fallback
                } catch (e: Exception) {
                    Log.w(TAG, "Impossibile estrarre l'URL dall'intent URI", e)
                }
            }
        }

        val extras = intent.extras ?: return null

        val knownKeys = listOf(
            "S.browser_fallback_url",
            "browser_fallback_url",
            "url",
            "URL",
            "uri",
            "URI",
            "web_url",
            "webUrl",
            Intent.EXTRA_TEXT
        )

        for (key in knownKeys) {
            val value = try {
                extras.getString(key)
            } catch (_: Exception) {
                null
            }

            if (isValidHttpString(value)) return value
        }

        for (key in extras.keySet()) {
            val value = try {
                extras.get(key)
            } catch (_: Exception) {
                null
            }

            if (value is String && isValidHttpString(value)) {
                return value
            }
        }

        return null
    }

    private fun tryStart(context: Context, intent: Intent): Boolean {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Avvio dell'intent non riuscito", e)
            false
        }
    }

    private fun tryStartShortcut(
        context: Context,
        packageName: String,
        shortcutId: String
    ): Boolean {
        return try {
            val launcherApps = context.getSystemService(
                Context.LAUNCHER_APPS_SERVICE
            ) as LauncherApps

            launcherApps.startShortcut(
                packageName,
                shortcutId,
                null,
                null,
                Process.myUserHandle()
            )
            true
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Avvio shortcut non riuscito: $packageName / $shortcutId",
                e
            )
            false
        }
    }

    private fun parseSavedIntent(savedIntentUri: String?): Intent? {
        if (savedIntentUri.isNullOrBlank()) return null

        return try {
            Intent.parseUri(
                savedIntentUri,
                Intent.URI_INTENT_SCHEME
            )
        } catch (e: Exception) {
            Log.w(TAG, "Intent salvato non valido", e)
            null
        }
    }

    private fun showToast(context: Context, message: String, duration: Int) {
        Toast.makeText(
            context.applicationContext,
            message,
            duration
        ).show()
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

        val uniqueKey = if (instanceId.isBlank()) {
            "$packageName|$shortcutId"
        } else {
            "$packageName|$shortcutId|$instanceId"
        }

        val savedIntentUri =
            PrefsSync.shortcutIntents(context)[uniqueKey]

        val savedIntent = parseSavedIntent(savedIntentUri)

        // Firefox e derivati.
        // Non modificare l'azione dell'intent salvato: potrebbe essere
        // necessaria insieme al component e agli extra originali.
        if (isFirefoxOrIceraven(packageName)) {
            if (savedIntent != null && tryStart(context, savedIntent)) {
                return
            }

            if (tryStartShortcut(context, packageName, shortcutId)) {
                return
            }

            val url = savedIntent?.let(::extractHttpUrl)
            if (url != null) {
                val browserIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(url)
                ).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    setPackage(packageName)
                }

                if (tryStart(context, browserIntent)) {
                    return
                }
            }

            if (showErrorToast) {
                showToast(
                    context,
                    "Impossibile aprire il collegamento. Rimuovi l'icona e ripinnala da Firefox.",
                    Toast.LENGTH_LONG
                )
            }
            return
        }

        // Browser generici.
        if (packageName in BROWSER_PACKAGES) {
            val url = savedIntent?.let(::extractHttpUrl)

            if (url != null) {
                val browserIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(url)
                ).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    setPackage(packageName)
                }

                if (tryStart(context, browserIntent)) {
                    return
                }
            }

            if (tryStartShortcut(context, packageName, shortcutId)) {
                return
            }

            if (savedIntent != null && tryStart(context, savedIntent)) {
                return
            }

            try {
                val launchIntent =
                    context.packageManager.getLaunchIntentForPackage(packageName)

                if (launchIntent != null && tryStart(context, launchIntent)) {
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "Avvio dell'app browser non riuscito", e)
            }

            if (showErrorToast) {
                showToast(
                    context,
                    "Impossibile aprire il link. Rimuovi l'icona e ripinnala dal browser.",
                    Toast.LENGTH_LONG
                )
            }
            return
        }

        // App e shortcut non-browser.
        if (tryStartShortcut(context, packageName, shortcutId)) {
            return
        }

        if (savedIntent != null && tryStart(context, savedIntent)) {
            return
        }

        try {
            val launchIntent =
                context.packageManager.getLaunchIntentForPackage(packageName)

            if (launchIntent != null && tryStart(context, launchIntent)) {
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "Avvio dell'app non riuscito: $packageName", e)
        }

        if (showErrorToast) {
            showToast(
                context,
                "Impossibile avviare la scorciatoia",
                Toast.LENGTH_SHORT
            )
        }
    }
}
