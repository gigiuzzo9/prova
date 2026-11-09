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

                // --- INIZIO BLOCCO DI RECUPERO INTENT (come Nova) ---
                var savedData = ""
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
                            savedData = "url:${realIntent.data}"
                        } else {
                            savedData = realIntent.toUri(Intent.URI_INTENT_SCHEME)
                        }
                    }
                } catch (_: Exception) {
                    // Fallback: se fallisce, resta vuoto e usa startShortcut
                }
                // --- FINE BLOCCO ---

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

                // Salviamo l'URL (se recuperato) invece della stringa vuota
                PrefsSync.setShortcutIntent(this, uniqueKey, savedData)

                ShortcutPlacement.placeOnFirstFreeCell(this, packageName, realShortcutId, instanceId)
                sendBroadcast(Intent("com.simplylauncher.RELOAD"))
            }
        }
        finish()
    }
}
