package com.simplylauncher

import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap

object AppCache {
    private val map = mutableMapOf<String, AppInfo>()
    private val iconBitmapCache = mutableMapOf<String, Bitmap>()

    fun putAll(apps: List<AppInfo>) {
        map.clear()
        iconBitmapCache.clear()
        apps.forEach { map[it.packageName] = it }
    }

    fun put(app: AppInfo) {
        map[app.packageName] = app
        iconBitmapCache.remove(app.packageName)
    }

    fun get(pkg: String): AppInfo? = map[pkg]

    fun remove(pkg: String) {
        map.remove(pkg)
        iconBitmapCache.remove(pkg)
    }

    fun clear() {
        map.clear()
        iconBitmapCache.clear()
    }

    fun size(): Int = map.size

    fun snapshot(): List<AppInfo> = map.values.sortedBy { it.label.lowercase() }

    /**
     * Restituisce il bitmap dell'icona già scalato alla dimensione richiesta.
     * Il bitmap viene creato UNA SOLA VOLTA e poi riusato.
     * Se il Drawable originale non può essere convertito, ritorna null.
     */
    fun getIconBitmap(pkg: String, sizePx: Int): Bitmap? {
        val key = "$pkg@$sizePx"
        iconBitmapCache[key]?.let { return it }

        val app = map[pkg] ?: return null
        return try {
            val w = app.icon.intrinsicWidth.takeIf { it > 0 } ?: sizePx
            val h = app.icon.intrinsicHeight.takeIf { it > 0 } ?: sizePx
            val bmp = app.icon.toBitmap(w, h, Bitmap.Config.ARGB_8888)
            val scaled = Bitmap.createScaledBitmap(bmp, sizePx, sizePx, true)
            if (scaled != bmp) bmp.recycle()
            iconBitmapCache[key] = scaled
            scaled
        } catch (e: Exception) {
            null
        }
    }
}
