package com.simplylauncher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

object AppCache {

    @Volatile
    private var map: Map<String, AppInfo> = emptyMap()

    // Fix 1: cache thread-safe
    private val iconBitmapCache = ConcurrentHashMap<String, Bitmap>()

    // Lock per operazioni che coinvolgono map + cache insieme
    private val cacheLock = Any()

    fun putAll(apps: List<AppInfo>) {
        synchronized(cacheLock) {
            val newMap = apps.associateBy { it.packageName }
            val removed = map.keys - newMap.keys

            // Invalida bitmap dei pkg rimossi O con icona cambiata
            iconBitmapCache.keys.removeAll { key ->
                val pkg = key.substringBefore('@')
                if (pkg in removed) return@removeAll true
                val oldApp = map[pkg]
                val newApp = newMap[pkg]
                // Se l'icona è cambiata (riferimento diverso), invalida
                oldApp != null && newApp != null && oldApp.icon !== newApp.icon
            }
            map = newMap
        }
    }

    fun put(app: AppInfo) {
        synchronized(cacheLock) {
            map = map + (app.packageName to app)
            iconBitmapCache.keys.removeAll { it.startsWith("${app.packageName}@") }
        }
    }

    fun get(pkg: String): AppInfo? = map[pkg]

    fun remove(pkg: String) {
        synchronized(cacheLock) {
            map = map - pkg
            iconBitmapCache.keys.removeAll { it.startsWith("$pkg@") }
        }
    }

    fun clear() {
        synchronized(cacheLock) {
            map = emptyMap()
            iconBitmapCache.clear()
        }
    }

    fun size(): Int = map.size

    fun snapshot(): List<AppInfo> = map.values.sortedBy { it.label.lowercase() }

    fun getIconBitmap(pkg: String, sizePx: Int): Bitmap? {
        if (sizePx <= 0) return null
        val key = "$pkg@$sizePx"

        // Fix 1: lettura thread-safe
        iconBitmapCache[key]?.let { return it }

        synchronized(cacheLock) {
            // Ricontrolla dentro il lock (race con putAll)
            iconBitmapCache[key]?.let { return it }

            val app = map[pkg] ?: return null
            return try {
                val cloned = app.icon.constantState?.newDrawable()?.mutate() ?: app.icon

                if (cloned is AdaptiveIconDrawable) {
                    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    val inset = (sizePx * 0.1f).toInt()
                    val left = inset
                    val top = inset
                    val right = sizePx - inset
                    val bottom = sizePx - inset

                    cloned.background?.let {
                        it.setBounds(left, top, right, bottom)
                        it.draw(canvas)
                    }
                    cloned.foreground?.let {
                        it.setBounds(left, top, right, bottom)
                        it.draw(canvas)
                    }
                    iconBitmapCache[key] = bmp
                    return bmp
                }

                val originalWidth = cloned.intrinsicWidth.coerceAtLeast(1)
                val originalHeight = cloned.intrinsicHeight.coerceAtLeast(1)
                val originalBitmap = cloned.toBitmap(originalWidth, originalHeight, Bitmap.Config.ARGB_8888)
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

    // ---------- Persistenza su disco (per cold start veloce) ----------

    fun persistIconsToDisk(context: android.content.Context, sizePx: Int) {
        val dir = File(context.filesDir, "icons_cache")
        if (!dir.exists()) dir.mkdirs()

        synchronized(cacheLock) {
            val existing = dir.listFiles()?.mapNotNull { it.nameWithoutExtension }?.toSet() ?: emptySet()
            val currentPkgs = map.keys

            existing.forEach { pkg ->
                if (pkg !in currentPkgs) {
                    File(dir, "$pkg.png").delete()
                }
            }

            for (app in map.values) {
                val file = File(dir, "${app.packageName}.png")
                // Fix 3: riscrivi SEMPRE (non skip se esiste già)
                try {
                    val bmp = getIconBitmap(app.packageName, sizePx) ?: continue
                    FileOutputStream(file).use { out ->
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun warmFromDisk(
        context: android.content.Context,
        labelsByPkg: Map<String, String>
    ): Int {
        val dir = File(context.filesDir, "icons_cache")
        if (!dir.exists()) return 0

        var loaded = 0
        synchronized(cacheLock) {
            for ((pkg, label) in labelsByPkg) {
                if (map.containsKey(pkg)) continue
                val file = File(dir, "$pkg.png")
                if (!file.exists()) continue
                try {
                    val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: continue
                    val drawable = BitmapDrawable(context.resources, bmp)
                    val info = AppInfo(label = label, packageName = pkg, icon = drawable)
                    map = map + (pkg to info)
                    loaded++
                } catch (_: Exception) {}
            }
        }
        return loaded
    }

    fun clearBitmaps() {
        iconBitmapCache.clear()
    }
}
