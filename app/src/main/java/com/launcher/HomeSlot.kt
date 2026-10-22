package com.simplylauncher

/**
 * Rappresenta un'icona posizionata nella griglia della home.
 *
 * @param packageName package dell'app
 * @param row riga nella griglia (0-based)
 * @param col colonna nella griglia (0-based)
 * @param page pagina della home (0-based)
 */
data class HomeSlot(
    val packageName: String,
    val row: Int,
    val col: Int,
    val page: Int = 0
) {
    companion object {
        /**
         * Serializza una lista di HomeSlot in formato stringa.
         * Formato: "pkg:row:col:page|pkg:row:col:page|..."
         *
         * Compatibile all'indietro: se la stringa ha 3 parti invece di 4,
         * la page è 0 (migrazione automatica dal formato vecchio).
         */
        fun listToString(slots: List<HomeSlot>): String {
            return slots.joinToString("|") { "${it.packageName}:${it.row}:${it.col}:${it.page}" }
        }

        /**
         * Deserializza una stringa in lista di HomeSlot.
         * Supporta sia il vecchio formato (3 parti) sia il nuovo (4 parti).
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
