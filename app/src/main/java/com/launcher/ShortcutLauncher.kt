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
        if (shortcutId.isBlank()) {
            AppRepository.launchApp(context, packageName)
            return
        }

        // La chiave che passiamo è quella salvata da ShortcutReceiverActivity.
        // Se contiene un timestamp o è un URL pulito, dobbiamo cercarla nella mappa.
        val map = PrefsSync.shortcutIntents(context)
        Log.d(TAG, "launch: pkg=$packageName key=$shortcutId")

        // Cerchiamo la chiave esatta, oppure una chiave che inizi per "$packageName|"
        // e che contenga la parte significativa di shortcutId.
        val entry = map.entries.firstOrNull { (k, _) ->
            k == shortcutId || k.startsWith("$packageName$SEP") && shortcutId.contains(k.substringAfter("$packageName$SEP"))
        } ?: map.entries.firstOrNull { (k, v) ->
            // Fallback: se la chiave non corrisponde, cerchiamo per package
            k.startsWith("$packageName$SEP") && v.startsWith("url:")
        }

        if (entry == null) {
            Log.w(TAG, "Nessuna entry trovata per $packageName/$shortcutId")
            // Prova con startShortcut (per PWA)
            try {
                val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                if (launcherApps.hasShortcutHostPermission()) {
                    // Estrai l'ID originale: la chiave può essere package|safeUrl o package|id|timestamp
                    val idToUse = shortcutId.split(SEP).getOrNull(1) ?: shortcutId
                    launcherApps.startShortcut(packageName, idToUse, null, null, UserHandle.getUserHandleForUid(0))
                    Log.d(TAG, "startShortcut fallback OK: $packageName/$idToUse")
                    return
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fallback startShortcut fallito", e)
            }
            if (showErrorToast) {
                Toast.makeText(context, "Impossibile avviare la scorciatoia", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val savedData = entry.value
        Log.d(TAG, "Trovato: $savedData")

        // --- URL puro ---
        if (savedData.startsWith("url:")) {
            val url = savedData.removePrefix("url:")
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                if (tryStart(context, browserIntent)) {
                    Log.d(TAG, "Avviato via URL: $url")
                    return
                }
            } catch (e: Exception) {
                Log.e(TAG, "Apertura URL fallita: $url", e)
            }
        }

        // --- Formato canonico package|id|user ---
        if (savedData.contains(SEP)) {
            val parts = savedData.split(SEP)
            if (parts.size >= 2) {
                val pkg = parts[0]
                val id = parts[1]
                val userId = parts.getOrNull(2)?.toIntOrNull() ?: 0
                val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

                if (!launcherApps.hasShortcutHostPermission()) {
                    if (showErrorToast) {
                        Toast.makeText(context, "Imposta SimplyLauncher come launcher predefinito", Toast.LENGTH_LONG).show()
                    }
                    return
                }
                try {
                    launcherApps.startShortcut(pkg, id, null, null, UserHandle.getUserHandleForUid(userId))
                    Log.d(TAG, "startShortcut OK: $pkg/$id")
                    return
                } catch (e: Exception) {
                    Log.e(TAG, "startShortcut fallito", e)
                }
            }
        }

        // --- Fallback app ---
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null && tryStart(context, launchIntent)) {
                Log.w(TAG, "Fallback app")
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallback launchIntent fallito", e)
        }

        if (showErrorToast) {
            Toast.makeText(context, "Impossibile avviare la scorciatoia", Toast.LENGTH_SHORT).show()
        }
    }
}
