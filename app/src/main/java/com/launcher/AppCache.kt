package com.simplylauncher

import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap

object AppCache {
    @Volatile
    private var map: Map<String, AppInfo> = emptyMap()

    private val iconBitmapCache = mutableMapOf<String, Bitmap>()

    fun putAll(apps: List<AppInfo>) {
        map = apps.associateBy { it.packageName }
        // NON svuotiamo iconBitmapCache: i bitmap possono essere riusati
        // (verranno sovrascritti solo se cambia l'icona, tramite put() o remove())
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

            // 1. Ottieni il bitmap originale alla sua risoluzione nativa (es. 512x512)
            val originalWidth = cloned.intrinsicWidth.coerceAtLeast(1)
            val originalHeight = cloned.intrinsicHeight.coerceAtLeast(1)
            val originalBitmap = cloned.toBitmap(originalWidth, originalHeight, Bitmap.Config.ARGB_8888)

            // 2. Ridimensiona con filtro bilineare di alta qualità
            val bmp = if (originalWidth == sizePx && originalHeight == sizePx) {
                originalBitmap
            } else {
                Bitmap.createScaledBitmap(originalBitmap, sizePx, sizePx, true)
            }

            iconBitmapCache[key] = bmp
            bmp
        } catch (e: Exception) {
            null
        }
    }
}
