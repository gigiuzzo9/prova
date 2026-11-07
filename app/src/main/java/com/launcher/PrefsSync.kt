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

    /** Cerca uno slot per chiave univoca (packageName o packageName|shortcutId[|instanceId]). */
    fun findSlotByUniqueKey(context: Context, uniqueKey: String): HomeSlot? =
        homeSlots(context).firstOrNull { it.uniqueKey == uniqueKey }

    /**
     * Rimuove lo slot con la chiave univoca indicata.
     * Ritorna true se qualcosa è stato rimosso.
     */
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

    // --- Dock shortcuts (per supportare le scorciatoie nella dock) ---

    fun dockShortcutIds(context: Context): List<String> {
        val raw = prefs(context).getString("dock_shortcut_ids", "") ?: ""
        return if (raw.isEmpty()) emptyList() else raw.split(",")
    }

    fun setDockShortcutIds(context: Context, ids: List<String>) {
        val normalized = ids.toMutableList()
        while (normalized.size < 4) normalized.add("")
        prefs(context).edit().putString("dock_shortcut_ids", normalized.joinToString(",")).apply()
    }

    // --- Dock instance IDs (distingue istanze multiple della stessa scorciatoia) ---

    fun dockInstanceIds(context: Context): List<String> {
        val raw = prefs(context).getString("dock_instance_ids", "") ?: ""
        return if (raw.isEmpty()) emptyList() else raw.split(",")
    }

    fun setDockInstanceIds(context: Context, ids: List<String>) {
        val normalized = ids.toMutableList()
        while (normalized.size < 4) normalized.add("")
        prefs(context).edit().putString("dock_instance_ids", normalized.joinToString(",")).apply()
    }

    /**
     * Rimuove dalla dock lo slot identificato da (pkg, shortcutId, instanceId).
     * Ritorna true se qualcosa è stato rimosso.
     */
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

    // --- Custom Icons (per app normali: chiave = packageName) ---

    fun customIcons(context: Context): Map<String, String> {
        val raw = prefs(context).getString("custom_icons", "") ?: ""
        if (raw.isEmpty()) return emptyMap()
        return raw.split("|").filter { it.isNotBlank() }.associate {
            val parts = it.split(":", limit = 2)
            parts[0] to (parts.getOrNull(1) ?: "")
        }
    }

    fun setCustomIcon(context: Context, packageName: String, path: String) {
        val map = customIcons(context).toMutableMap()
        map[packageName] = path
        val raw = map.entries.joinToString("|") { "${it.key}:${it.value}" }
        prefs(context).edit().putString("custom_icons", raw).apply()
    }

    fun removeCustomIcon(context: Context, packageName: String) {
        val map = customIcons(context).toMutableMap()
        map.remove(packageName)
        val raw = map.entries.joinToString("|") { "${it.key}:${it.value}" }
        prefs(context).edit().putString("custom_icons", raw).apply()
    }

    // --- Custom Icons per le scorciatoie (chiave = uniqueKey = pkg|shortcutId[|instanceId]) ---

    fun customShortcutIcons(context: Context): Map<String, String> {
        val raw = prefs(context).getString("custom_shortcut_icons", "") ?: ""
        if (raw.isEmpty()) return emptyMap()
        return raw.split("|").filter { it.isNotBlank() }.associate {
            val parts = it.split(":", limit = 2)
            parts[0] to (parts.getOrNull(1) ?: "")
        }
    }

    fun setCustomShortcutIcon(context: Context, uniqueKey: String, path: String) {
        val map = customShortcutIcons(context).toMutableMap()
        map[uniqueKey] = path
        val raw = map.entries.joinToString("|") { "${it.key}:${it.value}" }
        prefs(context).edit().putString("custom_shortcut_icons", raw).apply()
    }

    fun removeCustomShortcutIcon(context: Context, uniqueKey: String) {
        val map = customShortcutIcons(context).toMutableMap()
        map.remove(uniqueKey)
        val raw = map.entries.joinToString("|") { "${it.key}:${it.value}" }
        prefs(context).edit().putString("custom_shortcut_icons", raw).apply()
    }

    // --- Home labels ---

    fun hideAppLabels(context: Context): Boolean = prefs(context).getBoolean("hide_app_labels", false)
    fun setHideAppLabels(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean("hide_app_labels", value).apply()
}
