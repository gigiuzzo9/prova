package com.simplylauncher

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("launcher_prefs")

object Prefs {
    private val KEY_COLOR = intPreferencesKey("bg_color")
    private val KEY_ALPHA = floatPreferencesKey("bg_alpha")
    private val KEY_COLS = intPreferencesKey("grid_cols")
    private val KEY_ROWS = intPreferencesKey("grid_rows")
    private val KEY_HOME_ITEMS = stringPreferencesKey("home_items")
    private val KEY_HOME_SLOTS = stringPreferencesKey("home_slots")
    private val KEY_PAGE_COUNT = intPreferencesKey("page_count")
    private val KEY_DOCK_ITEMS = stringPreferencesKey("dock_items")
    private val KEY_CUSTOM_LABELS = stringPreferencesKey("custom_labels")
    private val KEY_WIDGET_SLOTS = stringPreferencesKey("widget_slots")

    // ---- Flow (usati da SettingsActivity, drawer background, ecc.) ----

    fun colorFlow(context: Context): Flow<Int> =
        context.dataStore.data.map { it[KEY_COLOR] ?: 0xFF000000.toInt() }

    fun alphaFlow(context: Context): Flow<Float> =
        context.dataStore.data.map { it[KEY_ALPHA] ?: 0.9f }

    fun colsFlow(context: Context): Flow<Int> =
        context.dataStore.data.map { it[KEY_COLS] ?: 4 }

    fun rowsFlow(context: Context): Flow<Int> =
        context.dataStore.data.map { it[KEY_ROWS] ?: 6 }

    fun pageCountFlow(context: Context): Flow<Int> =
        context.dataStore.data.map { prefs ->
            val explicit = prefs[KEY_PAGE_COUNT]
            if (explicit != null && explicit > 0) explicit
            else {
                val rawSlots = prefs[KEY_HOME_SLOTS]
                if (rawSlots != null) {
                    val maxPage = HomeSlot.stringToList(rawSlots).maxOfOrNull { it.page } ?: 0
                    maxPage + 1
                } else 1
            }
        }

    fun homeSlotsFlow(context: Context): Flow<List<HomeSlot>> =
        context.dataStore.data.map { prefs ->
            val raw = prefs[KEY_HOME_SLOTS]
            if (raw != null) HomeSlot.stringToList(raw)
            else {
                val oldRaw = prefs[KEY_HOME_ITEMS] ?: ""
                val oldItems = oldRaw.split(",").filter { it.isNotBlank() }
                val cols = prefs[KEY_COLS] ?: 4
                HomeSlot.migrateFromLinear(oldItems, cols)
            }
        }

    fun widgetSlotsFlow(context: Context): Flow<List<WidgetSlot>> =
        context.dataStore.data.map { prefs ->
            WidgetSlot.stringToList(prefs[KEY_WIDGET_SLOTS] ?: "")
        }

    fun dockItemsFlow(context: Context): Flow<List<String>> =
        context.dataStore.data.map { prefs ->
            val raw = prefs[KEY_DOCK_ITEMS]
            if (raw.isNullOrEmpty()) emptyList() else raw.split(",")
        }

    fun customLabelsFlow(context: Context): Flow<Map<String, String>> =
        context.dataStore.data.map { prefs ->
            val raw = prefs[KEY_CUSTOM_LABELS] ?: ""
            if (raw.isEmpty()) emptyMap()
            else raw.split("|").filter { it.isNotBlank() }.associate {
                val parts = it.split(":", limit = 2)
                parts[0] to (parts.getOrNull(1) ?: "")
            }
        }

    // ---- Scritture (doppia scrittura: DataStore + PrefsSync) ----

    suspend fun setColor(context: Context, color: Int) {
        context.dataStore.edit { it[KEY_COLOR] = color }
    }

    suspend fun setAlpha(context: Context, alpha: Float) {
        context.dataStore.edit { it[KEY_ALPHA] = alpha }
    }

    suspend fun setCols(context: Context, cols: Int) {
        context.dataStore.edit { it[KEY_COLS] = cols }
        PrefsSync.setCols(context, cols)
    }

    suspend fun setRows(context: Context, rows: Int) {
        context.dataStore.edit { it[KEY_ROWS] = rows }
        PrefsSync.setRows(context, rows)
    }

    suspend fun setPageCount(context: Context, count: Int) {
        context.dataStore.edit { it[KEY_PAGE_COUNT] = count.coerceAtLeast(1) }
        PrefsSync.setPageCount(context, count)
    }

    suspend fun setHomeSlots(context: Context, slots: List<HomeSlot>) {
        context.dataStore.edit {
            it[KEY_HOME_SLOTS] = HomeSlot.listToString(slots)
            it.remove(KEY_HOME_ITEMS)
            val maxPage = slots.maxOfOrNull { s -> s.page } ?: 0
            it[KEY_PAGE_COUNT] = maxPage + 1
        }
        PrefsSync.setHomeSlots(context, slots)
    }

    suspend fun setWidgetSlots(context: Context, slots: List<WidgetSlot>) {
        context.dataStore.edit { it[KEY_WIDGET_SLOTS] = WidgetSlot.listToString(slots) }
        PrefsSync.setWidgetSlots(context, slots)
    }

    suspend fun setHomeItems(context: Context, items: List<String>) {
        val cols = colsFlow(context).first()
        val slots = HomeSlot.migrateFromLinear(items, cols)
        setHomeSlots(context, slots)
    }

    suspend fun setDockItems(context: Context, items: List<String>) {
        val normalized = items.toMutableList()
        while (normalized.size < 4) normalized.add("")
        context.dataStore.edit { it[KEY_DOCK_ITEMS] = normalized.joinToString(",") }
        PrefsSync.setDockItems(context, normalized)
    }

    suspend fun setCustomLabel(context: Context, packageName: String, label: String) {
        context.dataStore.edit { prefs ->
            val raw = prefs[KEY_CUSTOM_LABELS] ?: ""
            val map = if (raw.isEmpty()) mutableMapOf()
            else raw.split("|").filter { it.isNotBlank() }.associate {
                val parts = it.split(":", limit = 2)
                parts[0] to (parts.getOrNull(1) ?: "")
            }.toMutableMap()
            map[packageName] = label
            prefs[KEY_CUSTOM_LABELS] = map.entries.joinToString("|") { "${it.key}:${it.value}" }
        }
        PrefsSync.setCustomLabel(context, packageName, label)
    }
}
