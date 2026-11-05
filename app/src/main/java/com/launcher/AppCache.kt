package com.simplylauncher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

    /**
     * Salva su disco i bitmap delle icone (una sola dimensione: 96dp di default,
     * sufficiente per la home e il drawer, scalabile poi in memoria).
     */
    fun persistIconsToDisk(context: android.content.Context, sizePx: Int) {
        val dir = File(context.filesDir, "icons_cache")
        if (!dir.exists()) dir.mkdirs()

        val existing = dir.listFiles()?.mapNotNull { it.nameWithoutExtension }?.toSet() ?: emptySet()
        val currentPkgs = map.keys

        // Rimuovi icone di pacchetti non più presenti
        existing.forEach { pkg ->
            if (pkg !in currentPkgs) {
                File(dir, "$pkg.png").delete()
            }
        }

        // Salva icone mancanti
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

    /**
     * Carica da disco le icone dei pacchetti richiesti e popola AppCache con
     * AppInfo "minimali" (label dal disco se disponibile, icona dal PNG).
     * NON sovrascrive AppInfo già presenti: se un pkg è già in cache, lo lascia.
     *
     * Ritorna il numero di icone caricate.
     */
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

    /** Svuota solo i bitmap in memoria (utile al resume per liberare RAM). */
    fun clearBitmaps() {
        iconBitmapCache.clear()
    }
}
