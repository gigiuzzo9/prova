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
                val packageName = shortcutInfo.`package`
                // Genera un ID univoco per questa istanza (non usare shortcutInfo.id)
                val uniqueId = "shortcut_${System.currentTimeMillis()}_${(0..9999).random()}"

                val slots = PrefsSync.homeSlots(this).toMutableList()

                // Trova la prima cella libera
                val occupied = mutableSetOf<Pair<Int, Int>>()
                slots.forEach { occupied.add(it.row to it.col) }

                var foundRow = 0
                var foundCol = 0
                var found = false
                for (r in 0 until 6) {
                    for (c in 0 until 4) {
                        if (!occupied.contains(r to c)) {
                            foundRow = r
                            foundCol = c
                            found = true
                            break
                        }
                    }
                    if (found) break
                }

                if (found) {
                    val newSlot = HomeSlot(
                        packageName = packageName,
                        row = foundRow,
                        col = foundCol,
                        page = 0,
                        shortcutId = uniqueId
                    )
                    slots.add(newSlot)
                    PrefsSync.setHomeSlots(this, slots)
                    sendBroadcast(Intent("com.simplylauncher.RELOAD"))
                }
            }
        }
        finish()
    }
}
