package com.simplylauncher

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("launcher_prefs")

object Prefs {
    private val KEY_COLOR = intPreferencesKey("bg_color")
    private val KEY_ALPHA = floatPreferencesKey("bg_alpha")
    private val KEY_COLS = intPreferencesKey("grid_cols")
    private val KEY_ROWS = intPreferencesKey("grid_rows")
    private val KEY_HOME_ITEMS = stringPreferencesKey("home_items")

    fun colorFlow(context: Context): Flow<Int> =
        context.dataStore.data.map { it[KEY_COLOR] ?: 0xFF000000.toInt() }

    fun alphaFlow(context: Context): Flow<Float> =
        context.dataStore.data.map { it[KEY_ALPHA] ?: 0.9f }

    fun colsFlow(context: Context): Flow<Int> =
        context.dataStore.data.map { it[KEY_COLS] ?: 4 }

    fun rowsFlow(context: Context): Flow<Int> =
        context.dataStore.data.map { it[KEY_ROWS] ?: 6 }

    fun homeItemsFlow(context: Context): Flow<List<String>> =
        context.dataStore.data.map { prefs ->
            prefs[KEY_HOME_ITEMS]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        }

    suspend fun setColor(context: Context, color: Int) {
        context.dataStore.edit { it[KEY_COLOR] = color }
    }

    suspend fun setAlpha(context: Context, alpha: Float) {
        context.dataStore.edit { it[KEY_ALPHA] = alpha }
    }

    suspend fun setCols(context: Context, cols: Int) {
        context.dataStore.edit { it[KEY_COLS] = cols }
    }

    suspend fun setRows(context: Context, rows: Int) {
        context.dataStore.edit { it[KEY_ROWS] = rows }
    }

    suspend fun setHomeItems(context: Context, items: List<String>) {
        context.dataStore.edit { it[KEY_HOME_ITEMS] = items.joinToString(",") }
    }
}
