package com.simplylauncher

import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.Process
import androidx.appcompat.app.AppCompatActivity

class ShortcutReceiverActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val launcherApps = getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps
        val request = launcherApps.getPinItemRequest(intent)

        if (request != null && request.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) {
            val shortcutInfo = request.shortcutInfo
            if (shortcutInfo != null && request.accept()) {
                val packageName = shortcutInfo.`package`
                val realShortcutId = shortcutInfo.id

                var savedData = ""

                // --- INIZIO RECUPERO URL (NOVA-STYLE) ---
                // Interroghiamo il sistema per ottenere la ShortcutInfo completa.
                // Questo ci permette di leggere l'Intent originale con l'URL, 
                // che a volte non è presente nell'oggetto passato direttamente.
                try {
                    val query = LauncherApps.ShortcutQuery()
                        .setPackage(packageName)
                        .setQueryFlags(
                            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                            LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or
                            LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                        )
                    
                    val allShortcuts = launcherApps.getShortcuts(query, Process.myUserHandle())
                    val realShortcut = allShortcuts?.firstOrNull { it.id == realShortcutId }
                    val realIntent = realShortcut?.intent
                    
                    if (realIntent != null) {
                        if (realIntent.action == Intent.ACTION_VIEW && realIntent.data != null) {
                            // Abbiamo trovato l'URL! Lo salviamo col prefisso "url:"
                            savedData = "url:${realIntent.data}"
                        } else {
                            // Se non è un ACTION_VIEW, salviamo l'Intent serializzato
                            savedData = realIntent.toUri(Intent.URI_INTENT_SCHEME)
                        }
                    }
                } catch (_: Exception) {
                    // Se fallisce, savedData resta vuoto e useremo startShortcut
                }
                // --- FINE RECUPERO URL ---

                val existingSlots = PrefsSync.homeSlots(this)
                val sameShortcutExists = existingSlots.any {
                    it.packageName == packageName && it.shortcutId == realShortcutId
                }
                val instanceId = if (sameShortcutExists) {
                    "inst_${System.currentTimeMillis()}_${(0..9999).random()}"
                } else {
                    ""
                }

                val uniqueKey = when {
                    instanceId.isEmpty() -> "$packageName|$realShortcutId"
                    else -> "$packageName|$realShortcutId|$instanceId"
                }

                // Salviamo l'URL (se trovato) o stringa vuota
                PrefsSync.setShortcutIntent(this, uniqueKey, savedData)

                // Piazziamo l'icona sulla home
                ShortcutPlacement.placeOnFirstFreeCell(this, packageName, realShortcutId, instanceId)
                
                // Ricarichiamo la home
                sendBroadcast(Intent("com.simplylauncher.RELOAD"))
            }
        }
        finish()
    }
}
