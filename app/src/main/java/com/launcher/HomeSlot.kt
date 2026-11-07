package com.simplylauncher

/**
 * Rappresenta un'icona posizionata nella griglia della home.
 *
 * @param packageName package dell'app (o della scorciatoia)
 * @param row riga nella griglia (0-based)
 * @param col colonna nella griglia (0-based)
 * @param page pagina della home (0-based)
 * @param shortcutId se non vuoto, questo slot è una scorciatoia (ShortcutInfo.id REALE)
 * @param instanceId se non vuoto, distingue più istanze della stessa scorciatoia
 *                   (es. due pin della stessa shortcut). Vuoto = prima istanza.
 */
data class HomeSlot(
    val packageName: String,
    val row: Int,
    val col: Int,
    val page: Int = 0,
    val shortcutId: String = "",
    val instanceId: String = ""
) {
    /**
     * Chiave univoca per identificare lo slot.
     * - App normale: "packageName"
     * - Scorciatoia singola: "packageName|shortcutId"
     * - Scorciatoia multipla: "packageName|shortcutId|instanceId"
     */
    val uniqueKey: String
        get() = when {
            shortcutId.isEmpty() -> packageName
            instanceId.isEmpty() -> "$packageName|$shortcutId"
            else -> "$packageName|$shortcutId|$instanceId"
        }

    /** true se questo slot è una scorciatoia (ha shortcutId reale). */
    val isShortcut: Boolean get() = shortcutId.isNotEmpty()

    companion object {
        /**
         * Serializza una lista di HomeSlot.
         * Formato: "pkg:row:col:page:shortcutId:instanceId|..."
         * I campi finali vuoti vengono omessi per compatibilità.
         */
        fun listToString(slots: List<HomeSlot>): String {
            return slots.joinToString("|") { s ->
                when {
                    s.shortcutId.isEmpty() && s.page == 0 ->
                        // vecchio formato a 3 parti
                        "${s.packageName}:${s.row}:${s.col}"
                    s.shortcutId.isEmpty() && s.instanceId.isEmpty() ->
                        "${s.packageName}:${s.row}:${s.col}:${s.page}"
                    s.instanceId.isEmpty() ->
                        "${s.packageName}:${s.row}:${s.col}:${s.page}:${s.shortcutId}"
                    else ->
                        "${s.packageName}:${s.row}:${s.col}:${s.page}:${s.shortcutId}:${s.instanceId}"
                }
            }
        }

        /**
         * Deserializza una stringa in lista di HomeSlot.
         * Supporta formati a 3, 4, 5 o 6 parti.
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
                    6 -> {
                        val pkg = parts[0]
                        val row = parts[1].toIntOrNull() ?: return@mapNotNull null
                        val col = parts[2].toIntOrNull() ?: return@mapNotNull null
                        val page = parts[3].toIntOrNull() ?: 0
                        val shortcutId = parts[4]
                        val instanceId = parts[5]
                        HomeSlot(pkg, row, col, page, shortcutId, instanceId)
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
