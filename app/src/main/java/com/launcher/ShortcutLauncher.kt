package com.simplylauncher

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.UserHandle
import android.util.Log
import android.widget.Toast

object ShortcutLauncher {

    private const val TAG = "ShortcutLauncher"
    private const val SEP = "|"
    private const val KEY_INTENT_SUFFIX = "|intent"

    private fun tryStart(context: Context, intent: Intent): Boolean {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "tryStart fallito", e)
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
        // Nessuna scorciatoia: avvia semplicemente l'app
        if (shortcutId.isBlank()) {
            AppRepository.launchApp(context, packageName)
            return
        }

        val uniqueKey = if (instanceId.isEmpty()) {
            "$packageName$SEP$shortcutId"
        } else {
            "$packageName$SEP$shortcutId$SEP$instanceId"
        }
        val map = PrefsSync.shortcutIntents(context)
        Log.d(TAG, "launch: pkg=$packageName id=$shortcutId instanceId=$instanceId key=$uniqueKey")

        // -----------------------------------------------------------------
        // 0) RICERCA PER PREFISSO: qualsiasi chiave che inizia con packageName|
        //    e ha valore "url:..." o "intent:..."
        // -----------------------------------------------------------------
        val urlEntry = map.entries.firstOrNull { (key, value) ->
            key.startsWith("$packageName$SEP") && value.startsWith("url:")
        }
        if (urlEntry != null) {
            val url = urlEntry.value.removePrefix("url:")
            
            // --- TENTATIVO PWA (Firefox/Iceraven) ---
            try {
                val pwaIntent = Intent("mozilla.components.feature.pwa.VIEW_PWA")
                pwaIntent.data = Uri.parse(url)
                pwaIntent.setPackage(packageName)
                if (tryStart(context, pwaIntent)) {
                    Log.d(TAG, "Avviato come PWA (ricerca per prefisso): $url")
                    return
                }
            } catch (e: Exception) {
                Log.e(TAG, "PWA fallita, provo ACTION_VIEW", e)
            }

            // --- FALLBACK BROWSER NORMALE ---
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                if (tryStart(context, browserIntent)) {
                    Log.d(TAG, "Avviato via URL (ricerca per prefisso): $url")
                    return
                }
            } catch (e: Exception) {
                Log.e(TAG, "Apertura URL fallita: $url", e)
            }
        }

        val intentEntry = map.entries.firstOrNull { (key, value) ->
            key.startsWith("$packageName$SEP") && value.startsWith("intent:")
        }
        if (intentEntry != null) {
            try {
                val parsed = Intent.parseUri(intentEntry.value, 0)
                if (tryStart(context, parsed)) {
                    Log.d(TAG, "Avviato via intent serializzato (ricerca per prefisso)")
                    return
                }
            } catch (e: Exception) {
                Log.e(TAG, "parseUri fallito", e)
            }
        }

        // -----------------------------------------------------------------
        // 1) Chiave esatta: intent serializzato
        // -----------------------------------------------------------------
        val serializedFallback = map["$uniqueKey$KEY_INTENT_SUFFIX"]
        if (!serializedFallback.isNullOrEmpty()) {
            try {
                val parsed = Intent.parseUri(serializedFallback, 0)
                if (tryStart(context, parsed)) {
                    Log.d(TAG, "Avviato via intent serializzato: $serializedFallback")
                    return
                }
            } catch (e: Exception) {
                Log.e(TAG, "parseUri fallito su fallback: $serializedFallback", e)
            }
        }

        // -----------------------------------------------------------------
        // 2) Chiave esatta: URL puro o intent serializzato
        // -----------------------------------------------------------------
        val savedData = map[uniqueKey] ?: ""

        if (savedData.startsWith("url:")) {
            val url = savedData.removePrefix("url:")
            
            // --- TENTATIVO PWA ---
            try {
                val pwaIntent = Intent("mozilla.components.feature.pwa.VIEW_PWA")
                pwaIntent.data = Uri.parse(url)
                pwaIntent.setPackage(packageName)
                if (tryStart(context, pwaIntent)) {
                    Log.d(TAG, "Avviato come PWA: $url")
                    return
                }
            } catch (e: Exception) {
                Log.e(TAG, "PWA fallita, provo ACTION_VIEW", e)
            }

            // --- FALLBACK BROWSER NORMALE ---
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                if (tryStart(context, browserIntent)) {
                    Log.d(TAG, "Avviato via URL puro: $url")
                    return
                }
            } catch (e: Exception) {
                Log.e(TAG, "Apertura URL fallita: $url", e)
            }
        }

        if (savedData.contains("://") || savedData.contains("intent:")) {
            try {
                val parsed = Intent.parseUri(savedData, 0)
                if (tryStart(context, parsed)) {
                    Log.d(TAG, "Avviato via intent legacy serializzato: $savedData")
                    return
                }
            } catch (e: Exception) {
                Log.e(TAG, "parseUri legacy fallito: $savedData", e)
            }
        }

        // -----------------------------------------------------------------
        // 3) Canale moderno: package|id|user (Chrome e derivati)
        // -----------------------------------------------------------------
        if (savedData.contains(SEP)) {
            val parts = savedData.split(SEP)
            if (parts.size == 3) {
                val pkg = parts[0]
                val id = parts[1]
                val userIdStr = parts[2]

                val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

                if (!launcherApps.hasShortcutHostPermission()) {
                    Log.e(TAG, "hasShortcutHostPermission=false: SimplyLauncher non è il launcher predefinito")
                    if (showErrorToast) {
                        Toast.makeText(
                            context.applicationContext,
                            "Imposta SimplyLauncher come launcher predefinito per usare le scorciatoie",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return
                }

                try {
                    val userId = userIdStr.toIntOrNull() ?: 0
                    val userHandle = UserHandle.getUserHandleForUid(userId)
                    launcherApps.startShortcut(pkg, id, null, null, userHandle)
                    Log.d(TAG, "startShortcut OK: $pkg/$id (user=$userId)")
                    return
                } catch (e: Exception) {
                    Log.e(TAG, "startShortcut fallito: $pkg/$id", e)
                }
            }
        }

        // -----------------------------------------------------------------
        // 4) Fallback finale: apri l'app principale
        // -----------------------------------------------------------------
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null && tryStart(context, launchIntent)) {
                Log.w(TAG, "Fallback: aperta l'app $packageName senza scorciatoia")
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallback launchIntent fallito per $packageName", e)
        }

        if (showErrorToast) {
            Toast.makeText(
                context.applicationContext,
                "Impossibile avviare la scorciatoia",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
