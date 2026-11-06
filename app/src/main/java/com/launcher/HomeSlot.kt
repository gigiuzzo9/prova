package com.simplylauncher

/**
 * Rappresenta un'icona posizionata nella griglia della home.
 *
 * @param packageName package dell'app (o della scorciatoia)
 * @param row riga nella griglia (0-based)
 * @param col colonna nella griglia (0-based)
 * @param page pagina della home (0-based)
 * @param shortcutId se non vuoto, questo slot è una scorciatoia (ShortcutInfo.id)
 */
data class HomeSlot(
    val packageName: String,
    val row: Int,
    val col: Int,
    val page: Int = 0,
    val shortcutId: String = ""
) {
    companion object {
        /**
         * Serializza una lista di HomeSlot in formato stringa.
         * Formato: "pkg:row:col:page:shortcutId|pkg:row:col:page:shortcutId|..."
         * (shortcutId è opzionale, se vuoto viene omesso per compatibilità).
         */
        fun listToString(slots: List<HomeSlot>): String {
            return slots.joinToString("|") {
                if (it.shortcutId.isEmpty()) {
                    "${it.packageName}:${it.row}:${it.col}:${it.page}"
                } else {
                    "${it.packageName}:${it.row}:${it.col}:${it.page}:${it.shortcutId}"
                }
            }
        }

        /**
         * Deserializza una stringa in lista di HomeSlot.
         * Supporta:
         * - vecchio formato (3 parti): pkg:row:col
         * - formato con page (4 parti): pkg:row:col:page
         * - formato con shortcutId (5 parti): pkg:row:col:page:shortcutId
         */
        fun stringToList(raw: String): List<HomeSlot> {
            if (raw.isBlank()) return emptyList()
            return raw.split("|").mapNotNull { entry ->
                val parts = entry.split(":")
                when (parts.size) {
                    3 -> {
                        val pkg = parts[0]
                        val row = parts[1].toIntOrNull() ?: return@mapNotNull null
                        val col = parts[2].toIntOrNull() ?: return@mapNotNull null
                        HomeSlot(pkg, row, col, 0)
                    }
                    4 -> {
                        val pkg = parts[0]
                        val row = parts[1].toIntOrNull() ?: return@mapNotNull null
                        val col = parts[2].toIntOrNull() ?: return@mapNotNull null
                        val page = parts[3].toIntOrNull() ?: 0
                        HomeSlot(pkg, row, col, page)
                    }
                    5 -> {
                        val pkg = parts[0]
                        val row = parts[1].toIntOrNull() ?: return@mapNotNull null
                        val col = parts[2].toIntOrNull() ?: return@mapNotNull null
                        val page = parts[3].toIntOrNull() ?: 0
                        val shortcutId = parts[4]
                        HomeSlot(pkg, row, col, page, shortcutId)
                    }
                    else -> null
                }
            }
        }

        /**
         * Migra una vecchia lista lineare (List<String>) in HomeSlot
         * assegnando posizioni progressive in una griglia con [cols] colonne
         * e una sola pagina (page = 0).
         */
        fun migrateFromLinear(items: List<String>, cols: Int): List<HomeSlot> {
            return items.mapIndexed { index, pkg ->
                HomeSlot(
                    packageName = pkg,
                    row = index / cols,
                    col = index % cols,
                    page = 0
                )
            }
        }
    }
}
