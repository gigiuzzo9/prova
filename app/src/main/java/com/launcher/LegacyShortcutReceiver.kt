package com.simplylauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log

class LegacyShortcutReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Log per verificare se il receiver viene chiamato
        Log.d("LegacyShortcut", "onReceive action: ${intent.action}")

        // 1. Controlla l'azione (supportiamo entrambe le varianti)
        val action = intent.action
        if (action != "com.android.launcher.action.INSTALL_SHORTCUT" &&
            action != "android.intent.action.INSTALL_SHORTCUT") {
            return
        }

        // 2. Estrai i dati
        val displayName = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME)
        val shortcutIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_SHORTCUT_INTENT)

        // Se mancano i dati, esci
        if (displayName == null || shortcutIntent == null) {
            Log.e("LegacyShortcut", "Dati mancanti: name=$displayName, intent=$shortcutIntent")
            return
        }

        // 3. IMPORTANTE: ignora le scorciatoie che sono solo l'avvio dell'app
        // (Discreet Launcher fa questo controllo per evitare duplicati all'installazione)
        val serializedIntent = shortcutIntent.toUri(0)
        if (serializedIntent.contains("android.intent.action.MAIN") &&
            serializedIntent.contains("android.intent.category.LAUNCHER")) {
            Log.d("LegacyShortcut", "Ignorata scorciatoia di avvio app")
            return
        }

        // 4. Genera un ID univoco e un package name fittizio (se non presente)
        val shortcutId = "legacy_${System.currentTimeMillis()}"
        val packageName = shortcutIntent.`package` ?: "com.android.browser"
        val uniqueKey = "$packageName|$shortcutId"

        // 5. Salva l'intent serializzato (che contiene l'URL di Iceraven)
        PrefsSync.setShortcutIntent(context, uniqueKey, serializedIntent)

        // 6. Piazza l'icona sulla home
        ShortcutPlacement.placeOnFirstFreeCell(context, packageName, shortcutId, "")
        
        // 7. Ricarica la home
        context.sendBroadcast(Intent("com.simplylauncher.RELOAD"))
        
        Log.d("LegacyShortcut", "Scorciatoia salvata: $serializedIntent")
    }
}
