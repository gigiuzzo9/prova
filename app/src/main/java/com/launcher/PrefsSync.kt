package com.simplylauncher

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

object PrefsSync {

    private const val PREF_NAME = "launcher_prefs_sync"

    private const val KEY_MAPS_FORMAT_V2 = "maps_format_v2"
    private const val KEY_MAPS_MIGRATED = "maps_migrated_to_json"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /**
     * Pulizia una-tantum: il vecchio formato usava "|" tra entry e ":" tra chiave/valore,
     * ma le chiavi delle shortcut contengono "|" → dati corrotti.
     * Alla prima esecuzione con questo codice cancelliamo le vecchie mappe.
     * Gli utenti riapplicano le icone una volta sola.
     */
    private fun ensureMapsFormat(context: Context) {
        val p = prefs(context)
        if (p.getBoolean(KEY_MAPS_MIGRATED, false)) return
        p.edit()
            .remove("custom_labels")
            .remove("custom_icons")
            .remove("custom_shortcut_icons")
            .putBoolean(KEY_MAPS_MIGRATED, true)
            .putBoolean(KEY_MAPS_FORMAT_V2, true)
            .apply()
    }

    // ---- Helper JSON per mappe ----

    private fun mapToJson(map: Map<String, String>): String {
        val json = JSONObject()
        map.forEach { (k, v) -> json.put(k, v) }
        return json.toString()
    }

    private fun jsonToMap(raw: String?): Map<String, String> {
        if (raw.isNullOrEmpty()) return emptyMap()
        return try {
            val json = JSONObject(raw)
            val result = mutableMapOf<String, String>()
            json.keys().forEach { k -> result[k] = json.optString(k) }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

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
        removeShortcutIntent(context, uniqueKey)
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
            val uniqueKey = when {
                shortcutId.isEmpty() -> pkg
                instanceId.isEmpty() -> "$pkg|$shortcutId"
                else -> "$pkg|$shortcutId|$instanceId"
            }
            removeShortcutIntent(context, uniqueKey)
        }
        return removed
    }

    // --- Custom labels (JSON) ---

    fun customLabels(context: Context): Map<String, String> {
        ensureMapsFormat(context)
        return jsonToMap(prefs(context).getString("custom_labels", ""))
    }

    fun setCustomLabel(context: Context, packageName: String, label: String) {
        ensureMapsFormat(context)
        val map = customLabels(context).toMutableMap()
        map[packageName] = label
        prefs(context).edit().putString("custom_labels", mapToJson(map)).apply()
    }

    // --- Custom Icons (JSON, chiave = packageName) ---

    fun customIcons(context: Context): Map<String, String> {
        ensureMapsFormat(context)
        return jsonToMap(prefs(context).getString("custom_icons", ""))
    }

    fun setCustomIcon(context: Context, packageName: String, path: String) {
        ensureMapsFormat(context)
        val map = customIcons(context).toMutableMap()
        map[packageName] = path
        prefs(context).edit().putString("custom_icons", mapToJson(map)).apply()
    }

    fun removeCustomIcon(context: Context, packageName: String) {
        ensureMapsFormat(context)
        val map = customIcons(context).toMutableMap()
        map.remove(packageName)
        prefs(context).edit().putString("custom_icons", mapToJson(map)).apply()
    }

    // --- Custom Icons per shortcut (JSON, chiave = uniqueKey) ---

    fun customShortcutIcons(context: Context): Map<String, String> {
        ensureMapsFormat(context)
        return jsonToMap(prefs(context).getString("custom_shortcut_icons", ""))
    }

    fun setCustomShortcutIcon(context: Context, uniqueKey: String, path: String) {
        ensureMapsFormat(context)
        val map = customShortcutIcons(context).toMutableMap()
        map[uniqueKey] = path
        prefs(context).edit().putString("custom_shortcut_icons", mapToJson(map)).apply()
    }

    fun removeCustomShortcutIcon(context: Context, uniqueKey: String) {
        ensureMapsFormat(context)
        val map = customShortcutIcons(context).toMutableMap()
        map.remove(uniqueKey)
        prefs(context).edit().putString("custom_shortcut_icons", mapToJson(map)).apply()
    }

    // --- Intent delle shortcut (JSON) ---

    fun shortcutIntents(context: Context): Map<String, String> =
        jsonToMap(prefs(context).getString("shortcut_intents", ""))

    fun setShortcutIntent(context: Context, uniqueKey: String, intentUri: String) {
        val map = shortcutIntents(context).toMutableMap()
        map[uniqueKey] = intentUri
        prefs(context).edit().putString("shortcut_intents", mapToJson(map)).apply()
    }

    fun removeShortcutIntent(context: Context, uniqueKey: String) {
        val map = shortcutIntents(context).toMutableMap()
        if (map.remove(uniqueKey) == null) return
        prefs(context).edit().putString("shortcut_intents", mapToJson(map)).apply()
    }

    // --- Home labels ---

    fun hideAppLabels(context: Context): Boolean = prefs(context).getBoolean("hide_app_labels", false)
    fun setHideAppLabels(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean("hide_app_labels", value).apply()
}
