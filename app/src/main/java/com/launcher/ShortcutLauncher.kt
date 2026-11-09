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

        // -----------------------------------------------------------------
        // 1) PRIORITÀ: intent serializzato (Firefox e derivati)
        //
        // Se al pin abbiamo salvato l'intent originale (perché il browser
        // mette l'URL negli extras, che LauncherApps.startShortcut() non
        // propaga), lo rilanciamo così com'è. Intent.parseUri() preserva
        // gli extras → il browser apre la pagina corretta.
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
        // 2) Canale legacy: URL puro o intent serializzato nella mappa principale
        // (ShortcutLegacyReceiver salva qui il valore)
        // -----------------------------------------------------------------
        val savedData = map[uniqueKey] ?: ""

        if (savedData.startsWith("url:")) {
            val url = savedData.removePrefix("url:")
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
        // Identico a Discreet: LauncherApps.startShortcut() con controllo
        // esplicito di hasShortcutHostPermission().
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
