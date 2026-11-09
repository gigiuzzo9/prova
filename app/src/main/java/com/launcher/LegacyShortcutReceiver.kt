package com.simplylauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log

class LegacyShortcutReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.android.launcher.action.INSTALL_SHORTCUT") return

        val displayName = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME)
        val icon = intent.getParcelableExtra<Bitmap>(Intent.EXTRA_SHORTCUT_ICON)
        val shortcutIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_SHORTCUT_INTENT)

        if (displayName == null || shortcutIntent == null) {
            Log.e("LegacyShortcut", "Dati mancanti")
            return
        }

        // Serializza l'Intent (qui c'è l'URL!)
        val serializedIntent = shortcutIntent.toUri(0)

        // Ignora le scorciatoie di avvio app generiche (come fa Discreet)
        if (serializedIntent.contains("android.intent.action.MAIN") &&
            serializedIntent.contains("android.intent.category.LAUNCHER")) return

        val packageName = shortcutIntent.`package` ?: "com.android.browser"
        val shortcutId = "legacy_${System.currentTimeMillis()}"
        val uniqueKey = "$packageName|$shortcutId"

        // Salviamo la stringa serializzata. Il tuo ShortcutLauncher la aprirà con Intent.parseUri.
        PrefsSync.setShortcutIntent(context, uniqueKey, serializedIntent)

        ShortcutPlacement.placeOnFirstFreeCell(context, packageName, shortcutId, "")
        context.sendBroadcast(Intent("com.simplylauncher.RELOAD"))
        Log.d("LegacyShortcut", "Scorciatoia salvata: $serializedIntent")
    }
}
