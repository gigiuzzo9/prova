package com.simplylauncher

import android.content.Context

object ShortcutSlotPlacer {

    /**
     * Trova la prima cella libera (pagina 0..N, righe 0..rows-1, colonne 0..cols-1)
     * e vi posiziona un HomeSlot con shortcutId/instanceId.
     * @return true se posizionato, false se non c'è spazio.
     */
    fun placeOnFirstFreeCell(
        context: Context,
        packageName: String,
        shortcutId: String,
        instanceId: String,
        label: String = ""
    ): Boolean {
        val cols = PrefsSync.cols(context).coerceAtLeast(1)
        val rows = PrefsSync.rows(context).coerceAtLeast(1)
        val slots = PrefsSync.homeSlots(context).toMutableList()
        val widgets = PrefsSync.widgetSlots(context)

        // Mappa pagina -> celle occupate
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
        val maxPages = 20

        while (targetRow == -1 && page < maxPages) {
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
            if (targetRow == -1) page++
        }

        if (targetRow == -1) return false

        val newSlot = HomeSlot(
            packageName = packageName,
            row = targetRow,
            col = targetCol,
            page = targetPage,
            shortcutId = shortcutId,
            instanceId = instanceId
        )
        slots.add(newSlot)
        PrefsSync.setHomeSlots(context, slots)

        if (targetPage + 1 > PrefsSync.pageCount(context)) {
            PrefsSync.setPageCount(context, targetPage + 1)
        }
        return true
    }
}
