package com.simplylauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log

class LegacyShortcutReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "LegacyShortcut"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != "com.android.launcher.action.INSTALL_SHORTCUT" &&
            action != "android.intent.action.INSTALL_SHORTCUT") return

        val displayName = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME)
        val icon = intent.getParcelableExtra<Bitmap>(Intent.EXTRA_SHORTCUT_ICON)
        val shortcutIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_SHORTCUT_INTENT)

        if (displayName == null || shortcutIntent == null) {
            Log.e(TAG, "Dati mancanti: name=$displayName intent=$shortcutIntent")
            return
        }

        // Serializza l'Intent COMPLETO (qui c'è l'URL di Firefox/Chrome)
        val serializedIntent = shortcutIntent.toUri(0)
        Log.d(TAG, "Ricevuto legacy: $serializedIntent")

        val hasUrl = serializedIntent.contains("http://") || serializedIntent.contains("https://")

        // Scarta SOLO se è un avvio app generico E NON contiene un URL.
        // Se contiene un URL, è una scorciatoia vera (Firefox la manda così).
        if (!hasUrl &&
            serializedIntent.contains("android.intent.action.MAIN") &&
            serializedIntent.contains("android.intent.category.LAUNCHER")) {
            Log.d(TAG, "Scartato: avvio app generico senza URL")
            return
        }

        val packageName = shortcutIntent.`package` ?: "com.android.browser"
        val shortcutId = "legacy_${System.currentTimeMillis()}"
        val uniqueKey = "$packageName|$shortcutId"

        // Salviamo la stringa serializzata. ShortcutLauncher la aprirà con Intent.parseUri.
        PrefsSync.setShortcutIntent(context, uniqueKey, serializedIntent)

        // Posiziona l'icona nella home
        ShortcutPlacement.placeOnFirstFreeCell(context, packageName, shortcutId, "")

        context.sendBroadcast(Intent("com.simplylauncher.RELOAD"))
        Log.d(TAG, "Scorciatoia legacy salvata: $serializedIntent (hasUrl=$hasUrl)")
    }
}
