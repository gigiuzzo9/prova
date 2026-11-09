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
                val realShortcutId = shortcutInfo.id

                val existingSlots = PrefsSync.homeSlots(this)
                val sameShortcutExists = existingSlots.any {
                    it.packageName == packageName && it.shortcutId == realShortcutId
                }
                val instanceId = if (sameShortcutExists) {
                    "inst_${System.currentTimeMillis()}_${(0..9999).random()}"
                } else {
                    ""
                }

                val uniqueKey = if (instanceId.isEmpty()) "$packageName|$realShortcutId" else "$packageName|$realShortcutId|$instanceId"
                PrefsSync.setShortcutIntent(this, uniqueKey, "")

                placeShortcutOnFirstFreeCell(packageName, realShortcutId, instanceId)
                sendBroadcast(Intent("com.simplylauncher.RELOAD"))
            }
        }
        finish()
    }

    private fun placeShortcutOnFirstFreeCell(packageName: String, shortcutId: String, instanceId: String): Boolean {
        val cols = PrefsSync.cols(this).coerceAtLeast(1)
        val rows = PrefsSync.rows(this).coerceAtLeast(1)
        val slots = PrefsSync.homeSlots(this).toMutableList()
        val widgets = PrefsSync.widgetSlots(this)
        val occupied = mutableSetOf<Pair<Int, Int>>()

        slots.forEach { occupied.add(it.row to it.col) }
        widgets.forEach { w ->
            for (r in w.row until w.row + w.heightCells) {
                for (c in w.col until w.col + w.widthCells) {
                    occupied.add(r to c)
                }
            }
        }

        var r = 0; var c = 0; var p = 0; var found = false
        while (!found && p < 20) {
            if (!occupied.contains(r to c)) { found = true }
            else { c++; if (c >= cols) { c = 0; r++; if (r >= rows) { r = 0; p++ } } }
        }
        if (!found) return false

        slots.add(HomeSlot(packageName, r, c, p, shortcutId, instanceId))
        PrefsSync.setHomeSlots(this, slots)
        if (p + 1 > PrefsSync.pageCount(this)) PrefsSync.setPageCount(this, p + 1)
        return true
    }
}
