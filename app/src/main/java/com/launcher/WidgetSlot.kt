package com.simplylauncher

/**
 * Rappresenta un widget posizionato nella home.
 * @param appWidgetId ID assegnato da AppWidgetHost
 * @param row riga griglia (0-based)
 * @param col colonna griglia (0-based)
 * @param widthCells larghezza in celle
 * @param heightCells altezza in celle
 * @param page pagina (0-based)
 */
data class WidgetSlot(
    val appWidgetId: Int,
    val row: Int,
    val col: Int,
    val widthCells: Int,
    val heightCells: Int,
    val page: Int
) {
    companion object {
        fun listToString(list: List<WidgetSlot>): String =
            list.joinToString("|") { "${it.appWidgetId}:${it.row}:${it.col}:${it.widthCells}:${it.heightCells}:${it.page}" }

        fun stringToList(raw: String): List<WidgetSlot> {
            if (raw.isBlank()) return emptyList()
            return raw.split("|").mapNotNull { entry ->
                val p = entry.split(":")
                if (p.size != 6) return@mapNotNull null
                val id = p[0].toIntOrNull() ?: return@mapNotNull null
                val r = p[1].toIntOrNull() ?: return@mapNotNull null
                val c = p[2].toIntOrNull() ?: return@mapNotNull null
                val w = p[3].toIntOrNull() ?: return@mapNotNull null
                val h = p[4].toIntOrNull() ?: return@mapNotNull null
                val pg = p[5].toIntOrNull() ?: return@mapNotNull null
                WidgetSlot(id, r, c, w, h, pg)
            }
        }
    }
}
