package com.simplylauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap

class LegacyShortcutReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 1. Controlla se è l'azione giusta
        if (intent.action != "com.android.launcher.action.INSTALL_SHORTCUT") return

        // 2. Estrai i dati (nome, icona, intent)
        val name = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME) ?: return
        val icon = intent.getParcelableExtra<Bitmap>(Intent.EXTRA_SHORTCUT_ICON)
        val shortcutIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_SHORTCUT_INTENT) ?: return

        // 3. Serializza l'Intent (questo contiene l'URL!)
        val serializedIntent = shortcutIntent.toUri(0)

        // 4. Genera un ID univoco
        val shortcutId = "legacy_${System.currentTimeMillis()}"
        val packageName = shortcutIntent.`package` ?: "com.android.browser"
        val uniqueKey = "$packageName|$shortcutId"

        // 5. Salva l'Intent serializzato
        PrefsSync.setShortcutIntent(context, uniqueKey, serializedIntent)

        // 6. Piazza l'icona sulla home
        ShortcutPlacement.placeOnFirstFreeCell(context, packageName, shortcutId, "")
        context.sendBroadcast(Intent("com.simplylauncher.RELOAD"))
    }
}
