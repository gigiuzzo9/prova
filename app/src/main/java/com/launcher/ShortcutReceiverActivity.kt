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

                // Se esiste già uno slot con la stessa (pkg, shortcutId), generiamo
                // un instanceId per distinguere questa nuova istanza.
                val existingSlots = PrefsSync.homeSlots(this)
                val sameShortcutExists = existingSlots.any {
                    it.packageName == packageName && it.shortcutId == realShortcutId
                }
                val instanceId = if (sameShortcutExists) {
                    "inst_${System.currentTimeMillis()}_${(0..9999).random()}"
                } else {
                    ""
                }

                val placed = placeShortcutOnFirstFreeCell(packageName, realShortcutId, instanceId)
                if (placed) {
                    // Salva l'Intent originale come fallback per il click,
                    // in caso la shortcut dinamica sparisca (browser che la rimuove).
                    val originalIntent = shortcutInfo.intent
                    if (originalIntent != null) {
                        val uniqueKey = when {
                            instanceId.isEmpty() -> "${packageName}|${realShortcutId}"
                            else -> "${packageName}|${realShortcutId}|${instanceId}"
                        }
                        PrefsSync.setShortcutIntent(
                            this,
                            uniqueKey,
                            originalIntent.toUri(Intent.URI_INTENT_SCHEME)
                        )
                    }
                    sendBroadcast(Intent("com.simplylauncher.RELOAD"))
                }
            }
        }
        finish()
    }

    /**
     * Cerca la prima cella libera (slot + widget) a partire da pagina 0.
     * Ritorna true se lo slot è stato piazzato e salvato.
     */
    private fun placeShortcutOnFirstFreeCell(
        packageName: String,
        shortcutId: String,
        instanceId: String
    ): Boolean {
        val cols = PrefsSync.cols(this).coerceAtLeast(1)
        val rows = PrefsSync.rows(this).coerceAtLeast(1)
        val slots = PrefsSync.homeSlots(this).toMutableList()
        val widgets = PrefsSync.widgetSlots(this)

        // Celle occupate per pagina (slot + widget)
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
                    // Nessuno spazio entro 20 pagine: rinuncia
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
