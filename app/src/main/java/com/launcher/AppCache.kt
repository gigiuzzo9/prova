package com.simplylauncher

import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap

object AppCache {
    @Volatile
    private var map: Map<String, AppInfo> = emptyMap()

    private val iconBitmapCache = mutableMapOf<String, Bitmap>()

    fun putAll(apps: List<AppInfo>) {
        map = apps.associateBy { it.packageName }
        // Non svuotare iconBitmapCache: i bitmap restano validi
    }

    fun put(app: AppInfo) {
        map = map + (app.packageName to app)
        // Rimuovi i bitmap di quel pkg, che potrebbero essere cambiati
        iconBitmapCache.keys.removeAll { it.startsWith("${app.packageName}@") }
    }

    fun get(pkg: String): AppInfo? = map[pkg]

    fun remove(pkg: String) {
        map = map - pkg
        iconBitmapCache.keys.removeAll { it.startsWith("$pkg@") }
    }

    fun clear() {
        map = emptyMap()
        iconBitmapCache.clear()
    }

    fun size(): Int = map.size

    fun snapshot(): List<AppInfo> = map.values.sortedBy { it.label.lowercase() }

    fun getIconBitmap(pkg: String, sizePx: Int): Bitmap? {
        if (sizePx <= 0) return null
        val key = "$pkg@$sizePx"
        iconBitmapCache[key]?.let { return it }
        val app = map[pkg] ?: return null
        return try {
            val cloned = app.icon.constantState?.newDrawable()?.mutate() ?: app.icon
            val bmp = cloned.toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            iconBitmapCache[key] = bmp
            bmp
        } catch (e: Exception) {
            null
        }
    }
}
