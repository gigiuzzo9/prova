package com.simplylauncher

import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Bundle
import android.util.Log
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

                // ===== LOG DIAGNOSTICO =====
                Log.d("PIN_DEBUG", "=== SHORTCUT PINNATA ===")
                Log.d("PIN_DEBUG", "pkg=$packageName id=$realShortcutId")
                Log.d("PIN_DEBUG", "intent=${shortcutInfo.intent}")
                Log.d("PIN_DEBUG", "intent action=${shortcutInfo.intent?.action}")
                Log.d("PIN_DEBUG", "intent data=${shortcutInfo.intent?.data}")
                Log.d("PIN_DEBUG", "intent flags=${shortcutInfo.intent?.flags}")
                Log.d("PIN_DEBUG", "intent extras=${shortcutInfo.intent?.extras?.keySet()}")
                shortcutInfo.intent?.extras?.keySet()?.forEach { key ->
                    Log.d("PIN_DEBUG", "  extra[$key]=${shortcutInfo.intent?.extras?.get(key)}")
                }
                Log.d("PIN_DEBUG", "intents count=${shortcutInfo.intents?.size}")
                shortcutInfo.intents?.forEachIndexed { i, it ->
                    Log.d("PIN_DEBUG", "  intents[$i] action=${it.action} data=${it.data} extras=${it.extras?.keySet()}")
                    it.extras?.keySet()?.forEach { key ->
                        Log.d("PIN_DEBUG", "    intents[$i].extra[$key]=${it.extras?.get(key)}")
                    }
                }
                Log.d("PIN_DEBUG", "categories=${shortcutInfo.categories}")
                Log.d("PIN_DEBUG", "longLabel=${shortcutInfo.longLabel} shortLabel=${shortcutInfo.shortLabel}")
                // ===== FINE LOG =====

                val existingSlots = PrefsSync.homeSlots(this)
                val sameShortcutExists = existingSlots.any {
                    it.packageName == packageName && it.shortcutId == realShortcutId
                }
                val instanceId = if (sameShortcutExists) {
                    "inst_${System.currentTimeMillis()}_${(0..9999).random()}"
                } else {
                    ""
                }

                // Salva l'Intent originale (prova sia intent che intents[0])
                val uniqueKey = when {
                    instanceId.isEmpty() -> "$packageName|$realShortcutId"
                    else -> "$packageName|$realShortcutId|$instanceId"
                }
                val originalIntent = shortcutInfo.intent
                    ?: shortcutInfo.intents?.firstOrNull()
                if (originalIntent != null) {
                    try {
                        val intentUri = originalIntent.toUri(Intent.URI_INTENT_SCHEME)
                        PrefsSync.setShortcutIntent(this, uniqueKey, intentUri)
                        Log.d("PIN_DEBUG", "Salvato intent per $uniqueKey: $intentUri")
                    } catch (e: Exception) {
                        Log.e("PIN_DEBUG", "Errore salvataggio intent: ${e.message}", e)
                    }
                } else {
                    Log.e("PIN_DEBUG", "NESSUNA INTENT per $uniqueKey")
                }

                placeShortcutOnFirstFreeCell(packageName, realShortcutId, instanceId)
                sendBroadcast(Intent("com.simplylauncher.RELOAD"))
            }
        }
        finish()
    }

    private fun placeShortcutOnFirstFreeCell(
        packageName: String,
        shortcutId: String,
        instanceId: String
    ): Boolean {
        val cols = PrefsSync.cols(this).coerceAtLeast(1)
        val rows = PrefsSync.rows(this).coerceAtLeast(1)
        val slots = PrefsSync.homeSlots(this).toMutableList()
        val widgets = PrefsSync.widgetSlots(this)

        val occupiedByPage = HashMap<Int, MutableSet<Pair<Int, Int>>>()
        fun occupied(page: Int): MutableSet<Pair<Int, Int>> =
            occupiedByPage.getOrPut(page) { mutableSetOf() }

        slots.forEach { occupied(it.page).add(it.row to it.col) }
        widgets.forEach { w ->
            for (r in w.row until w.row + w.heightCells) {
                for (c in w.col until w.col + w.widthCells) {
                    occupied(w.page).add(r to c)
                }
            }
        }

        var targetPage = 0
        var targetRow = -1
        var targetCol = -1
        var page = 0
        while (targetRow == -1) {
            val occ = occupied(page)
            outer@ for (r in 0 until rows) {
                for (c in 0 until cols) {
                    if (!occ.contains(r to c)) {
                        targetPage = page
                        targetRow = r
                        targetCol = c
                        break@outer
                    }
                }
            }
            if (targetRow == -1) {
                page++
                if (page >= 20) {
                    return false
                }
            }
        }

        val newSlot = HomeSlot(
            packageName = packageName,
            row = targetRow,
            col = targetCol,
            page = targetPage,
            shortcutId = shortcutId,
            instanceId = instanceId
        )
        slots.add(newSlot)
        PrefsSync.setHomeSlots(this, slots)

        if (targetPage + 1 > PrefsSync.pageCount(this)) {
            PrefsSync.setPageCount(this, targetPage + 1)
        }
        return true
    }
}
