package com.simplylauncher

import android.content.Context
import android.content.SharedPreferences

object PrefsSync {

    private const val PREF_NAME = "launcher_prefs_sync"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // --- Grid ---

    fun cols(context: Context): Int = prefs(context).getInt("grid_cols", 4)
    fun setCols(context: Context, value: Int) =
        prefs(context).edit().putInt("grid_cols", value).apply()

    fun rows(context: Context): Int = prefs(context).getInt("grid_rows", 6)
    fun setRows(context: Context, value: Int) =
        prefs(context).edit().putInt("grid_rows", value).apply()

    // --- Pages ---

    fun pageCount(context: Context): Int = prefs(context).getInt("page_count", 1)
    fun setPageCount(context: Context, value: Int) =
        prefs(context).edit().putInt("page_count", value.coerceAtLeast(1)).apply()

    // --- Home slots ---

    fun homeSlotsRaw(context: Context): String = prefs(context).getString("home_slots", "") ?: ""
    fun homeSlots(context: Context): List<HomeSlot> =
        HomeSlot.stringToList(homeSlotsRaw(context))

    fun setHomeSlots(context: Context, slots: List<HomeSlot>) {
        val maxPage = slots.maxOfOrNull { it.page } ?: 0
        prefs(context).edit()
            .putString("home_slots", HomeSlot.listToString(slots))
            .putInt("page_count", maxPage + 1)
            .apply()
    }

    fun findSlotByUniqueKey(context: Context, uniqueKey: String): HomeSlot? =
        homeSlots(context).firstOrNull { it.uniqueKey == uniqueKey }

    fun removeHomeSlotByKey(context: Context, uniqueKey: String): Boolean {
        val slots = homeSlots(context)
        val filtered = slots.filterNot { it.uniqueKey == uniqueKey }
        if (filtered.size == slots.size) return false
        setHomeSlots(context, filtered)
        return true
    }

    // --- Widget slots ---

    fun widgetSlotsRaw(context: Context): String = prefs(context).getString("widget_slots", "") ?: ""
    fun widgetSlots(context: Context): List<WidgetSlot> =
        WidgetSlot.stringToList(widgetSlotsRaw(context))

    fun setWidgetSlots(context: Context, slots: List<WidgetSlot>) =
        prefs(context).edit().putString("widget_slots", WidgetSlot.listToString(slots)).apply()

    // --- Dock ---

    fun dockItems(context: Context): List<String> {
        val raw = prefs(context).getString("dock_items", "") ?: ""
        return if (raw.isEmpty()) emptyList() else raw.split(",")
    }

    fun setDockItems(context: Context, items: List<String>) {
        val normalized = items.toMutableList()
        while (normalized.size < 4) normalized.add("")
        prefs(context).edit().putString("dock_items", normalized.joinToString(",")).apply()
    }

    fun dockShortcutIds(context: Context): List<String> {
        val raw = prefs(context).getString("dock_shortcut_ids", "") ?: ""
        return if (raw.isEmpty()) emptyList() else raw.split(",")
    }

    fun setDockShortcutIds(context: Context, ids: List<String>) {
        val normalized = ids.toMutableList()
        while (normalized.size < 4) normalized.add("")
        prefs(context).edit().putString("dock_shortcut_ids", normalized.joinToString(",")).apply()
    }

    fun dockInstanceIds(context: Context): List<String> {
        val raw = prefs(context).getString("dock_instance_ids", "") ?: ""
        return if (raw.isEmpty()) emptyList() else raw.split(",")
    }

    fun setDockInstanceIds(context: Context, ids: List<String>) {
        val normalized = ids.toMutableList()
        while (normalized.size < 4) normalized.add("")
        prefs(context).edit().putString("dock_instance_ids", normalized.joinToString(",")).apply()
    }

    fun removeDockSlotByKey(
        context: Context,
        pkg: String,
        shortcutId: String,
        instanceId: String
    ): Boolean {
        val items = dockItems(context).toMutableList()
        while (items.size < 4) items.add("")
        val ids = dockShortcutIds(context).toMutableList()
        while (ids.size < 4) ids.add("")
        val insts = dockInstanceIds(context).toMutableList()
        while (insts.size < 4) insts.add("")

        var removed = false
        for (i in items.indices) {
            if (items[i] == pkg &&
                (ids.getOrNull(i) ?: "") == shortcutId &&
                (insts.getOrNull(i) ?: "") == instanceId
            ) {
                items[i] = ""
                ids[i] = ""
                insts[i] = ""
                removed = true
                break
            }
        }
        if (removed) {
            setDockItems(context, items)
            setDockShortcutIds(context, ids)
            setDockInstanceIds(context, insts)
        }
        return removed
    }

    // --- Custom labels ---

    fun customLabels(context: Context): Map<String, String> {
        val raw = prefs(context).getString("custom_labels", "") ?: ""
        if (raw.isEmpty()) return emptyMap()
        return raw.split("|").filter { it.isNotBlank() }.associate {
            val parts = it.split(":", limit = 2)
            parts[0] to (parts.getOrNull(1) ?: "")
        }
    }

    fun setCustomLabel(context: Context, packageName: String, label: String) {
        val map = customLabels(context).toMutableMap()
        map[packageName] = label
        val raw = map.entries.joinToString("|") { "${it.key}:${it.value}" }
        prefs(context).edit().putString("custom_labels", raw).apply()
    }

    // --- Custom Icons (MAPPA UNICA per app e scorciatoie) ---
    // Chiave:
    //   app normale          -> "packageName"
    //   scorciatoia singola  -> "packageName|shortcutId"
    //   scorciatoia multipla -> "packageName|shortcutId|instanceId"
    // Valore: path assoluto del PNG.

    fun customIcons(context: Context): Map<String, String> {
        val raw = prefs(context).getString("custom_icons", "") ?: ""
        if (raw.isEmpty()) return emptyMap()
        return raw.split("|").filter { it.isNotBlank() }.mapNotNull {
            val idx = it.indexOf(':')
            if (idx <= 0) null
            else it.substring(0, idx) to it.substring(idx + 1)
        }.toMap()
    }

    /** Chiave univoca per un'icona custom (app o scorciatoia). */
    fun iconKeyFor(pkg: String, shortcutId: String, instanceId: String): String = when {
        shortcutId.isEmpty() -> pkg
        instanceId.isEmpty() -> "$pkg|$shortcutId"
        else -> "$pkg|$shortcutId|$instanceId"
    }

    /** Legge l'icona custom per uno slot (app o shortcut). Ritorna il path o null. */
    fun getCustomIconPath(
        context: Context,
        pkg: String,
        shortcutId: String,
        instanceId: String
    ): String? {
        val key = iconKeyFor(pkg, shortcutId, instanceId)
        return customIcons(context)[key]
    }

    /** Salva l'icona custom per uno slot (app o shortcut). */
    fun setCustomIconFor(
        context: Context,
        pkg: String,
        shortcutId: String,
        instanceId: String,
        path: String
    ) {
        val map = customIcons(context).toMutableMap()
        map[iconKeyFor(pkg, shortcutId, instanceId)] = path
        val raw = map.entries.joinToString("|") { "${it.key}:${it.value}" }
        prefs(context).edit().putString("custom_icons", raw).apply()
    }

    /** Rimuove l'icona custom per uno slot (app o shortcut). */
    fun removeCustomIconFor(
        context: Context,
        pkg: String,
        shortcutId: String,
        instanceId: String
    ) {
        val map = customIcons(context).toMutableMap()
        map.remove(iconKeyFor(pkg, shortcutId, instanceId))
        val raw = map.entries.joinToString("|") { "${it.key}:${it.value}" }
        prefs(context).edit().putString("custom_icons", raw).apply()
    }

    // --- Compat: vecchie API per app normali (mantengono la stessa mappa) ---

    fun setCustomIcon(context: Context, packageName: String, path: String) {
        setCustomIconFor(context, packageName, "", "", path)
    }

    fun removeCustomIcon(context: Context, packageName: String) {
        removeCustomIconFor(context, packageName, "", "")
    }

    // --- Compat: vecchie API per scorciatoie (mantengono la stessa mappa) ---

    fun customShortcutIcons(context: Context): Map<String, String> = customIcons(context)

    fun setCustomShortcutIcon(context: Context, uniqueKey: String, path: String) {
        val map = customIcons(context).toMutableMap()
        map[uniqueKey] = path
        val raw = map.entries.joinToString("|") { "${it.key}:${it.value}" }
        prefs(context).edit().putString("custom_icons", raw).apply()
    }

    fun removeCustomShortcutIcon(context: Context, uniqueKey: String) {
        val map = customIcons(context).toMutableMap()
        map.remove(uniqueKey)
        val raw = map.entries.joinToString("|") { "${it.key}:${it.value}" }
        prefs(context).edit().putString("custom_icons", raw).apply()
    }

    // --- Home labels ---

    fun hideAppLabels(context: Context): Boolean = prefs(context).getBoolean("hide_app_labels", false)
    fun setHideAppLabels(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean("hide_app_labels", value).apply()
}
