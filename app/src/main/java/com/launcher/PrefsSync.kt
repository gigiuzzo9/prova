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

    // --- Custom Icons ---

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
}
