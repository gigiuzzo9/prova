package com.simplylauncher

import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class ShortcutReceiverActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val launcherApps = getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps
        val request = launcherApps.getPinItemRequest(intent)

        if (request != null && request.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) {
            val shortcutInfo = request.shortcutInfo
            if (shortcutInfo != null && request.accept()) {
                // Qui devi creare un HomeSlot o un AppInfo per la scorciatoia.
                // Esempio: salva l'intent della scorciatoia per poterlo lanciare.
                val shortcutIntent = launcherApps.getShortcutIntent(shortcutInfo, null, null)
                // ... salva nel tuo database (PrefsSync) con un ID univoco ...
            }
        }
        finish()
    }
}
