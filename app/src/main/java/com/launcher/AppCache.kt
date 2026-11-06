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

object AppCache {

    @Volatile
    private var map: Map<String, AppInfo> = emptyMap()

    private val iconBitmapCache = mutableMapOf<String, Bitmap>()

    fun putAll(apps: List<AppInfo>) {
        val newMap = apps.associateBy { it.packageName }
        val removed = map.keys - newMap.keys
        // Rimuovi i bitmap dei pkg spariti
        iconBitmapCache.keys.removeAll { key ->
            val pkg = key.substringBefore('@')
            pkg in removed
        }
        map = newMap
        // NON svuotiamo più tutti i bitmap: chi chiama put(app) o remove(pkg)
        // si occupa di invalidare i singoli pkg cambiati.
    }

    fun put(app: AppInfo) {
        map = map + (app.packageName to app)
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

            // Gestione speciale per le icone adattive (evita il bordo nero su Huawei)
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

            // Per le icone normali
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

    // ---------- Persistenza su disco (per cold start veloce) ----------

    fun persistIconsToDisk(context: android.content.Context, sizePx: Int) {
        val dir = File(context.filesDir, "icons_cache")
        if (!dir.exists()) dir.mkdirs()

        val existing = dir.listFiles()?.mapNotNull { it.nameWithoutExtension }?.toSet() ?: emptySet()
        val currentPkgs = map.keys

        existing.forEach { pkg ->
            if (pkg !in currentPkgs) {
                File(dir, "$pkg.png").delete()
            }
        }

        for (app in map.values) {
            val file = File(dir, "${app.packageName}.png")
            if (file.exists()) continue
            try {
                val bmp = getIconBitmap(app.packageName, sizePx) ?: continue
                FileOutputStream(file).use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            } catch (_: Exception) {}
        }
    }

    fun warmFromDisk(
        context: android.content.Context,
        labelsByPkg: Map<String, String>
    ): Int {
        val dir = File(context.filesDir, "icons_cache")
        if (!dir.exists()) return 0

        var loaded = 0
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
        return loaded
    }

    fun clearBitmaps() {
        iconBitmapCache.clear()
    }
}
