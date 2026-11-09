package com.simplylauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Process
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import java.io.FileOutputStream

/**
 * Riceve le scorciatoie "legacy" inviate tramite broadcast INSTALL_SHORTCUT.
 * Usato da Firefox, Chrome (vecchie versioni), e molti altri browser/app.
 */
class LegacyShortcutReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val validActions = setOf(
            "com.android.launcher.action.INSTALL_SHORTCUT",
            "android.intent.action.INSTALL_SHORTCUT"
        )
        if (action !in validActions) return

        val callingPkg = try {
            context.packageManager.getNameForUid(android.os.Binder.getCallingUid())
        } catch (_: Exception) { null }

        val shortcutIntent: Intent? = try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT)
            }
        } catch (_: Exception) { null }

        val shortcutName: String? = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME)

        if (shortcutIntent == null) {
            Log.w("LegacyShortcut", "Nessun EXTRA_SHORTCUT_INTENT ricevuto")
            return
        }

        // === DEBUG: cosa contiene davvero il shortcutIntent? ===
        Log.d("LegacyShortcut", "=== SHORTCUT INTENT DEBUG ===")
        Log.d("LegacyShortcut", "action=${shortcutIntent.action}")
        Log.d("LegacyShortcut", "data=${shortcutIntent.data}")
        Log.d("LegacyShortcut", "package=${shortcutIntent.`package`}")
        Log.d("LegacyShortcut", "component=${shortcutIntent.component}")
        Log.d("LegacyShortcut", "flags=${shortcutIntent.flags}")
        shortcutIntent.extras?.keySet()?.forEach { key ->
            Log.d("LegacyShortcut", "extra[$key]=${shortcutIntent.extras?.get(key)}")
        }
        Log.d("LegacyShortcut", "intentUri=${shortcutIntent.toUri(Intent.URI_INTENT_SCHEME)}")
        // =========================================================

        val packageName = shortcutIntent.`package`
            ?: shortcutIntent.component?.packageName
            ?: callingPkg
            ?: return

        val label = if (!shortcutName.isNullOrBlank()) shortcutName else {
            try {
                val ai = context.packageManager.getApplicationInfo(packageName, 0)
                context.packageManager.getApplicationLabel(ai).toString()
            } catch (_: Exception) { packageName }
        }

        val url = extractUrlFromIntent(shortcutIntent)
        val intentUri = shortcutIntent.toUri(Intent.URI_INTENT_SCHEME)
        val instanceId = "legacy_${System.currentTimeMillis()}_${(0..9999).random()}"

        val realShortcutId = findDynamicShortcutId(context, packageName, label)
        val shortcutId = realShortcutId ?: "legacy"

        val uniqueKey = when {
            realShortcutId != null -> "$packageName|$realShortcutId|$instanceId"
            else -> "$packageName|legacy|$instanceId"
        }

        if (url != null) {
            PrefsSync.setShortcutIntent(context, uniqueKey, "url:$url")
        } else {
            PrefsSync.setShortcutIntent(context, uniqueKey, intentUri)
        }
        saveShortcutIcon(context, uniqueKey, intent)

        val placed = placeShortcutOnFirstFreeCell(
            context = context,
            packageName = packageName,
            shortcutId = shortcutId,
            instanceId = instanceId
        )

        if (placed) {
            context.sendBroadcast(Intent("com.simplylauncher.RELOAD"))
        } else {
            Log.w("LegacyShortcut", "Nessuna cella libera per $label")
        }
    }

    /**
     * Cerca l'URL nel shortcutIntent: prima in `data`, poi negli extra più comuni.
     */
    private fun extractUrlFromIntent(intent: Intent): String? {
        val data = intent.dataString
        if (!data.isNullOrBlank() && (data.startsWith("http://") || data.startsWith("https://"))) {
            return data
        }

        val knownKeys = listOf(
            "url", "uri", "URL", "Uri", "page_url", "web_url",
            "android.intent.extra.TEXT", Intent.EXTRA_TEXT
        )
        val extras = intent.extras ?: return null
        for (key in knownKeys) {
            val v = extras.get(key)
            if (v is String && (v.startsWith("http://") || v.startsWith("https://"))) {
                return v
            }
        }

        for (key in extras.keySet()) {
            val v = extras.get(key)
            if (v is String && (v.startsWith("http://") || v.startsWith("https://"))) {
                return v
            }
        }
        return null
    }

    /**
     * Cerca tra le scorciatoie dinamiche/pinned una con lo stesso label.
     */
    private fun findDynamicShortcutId(context: Context, packageName: String, label: String): String? {
        return try {
            val la = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val query = LauncherApps.ShortcutQuery()
                .setPackage(packageName)
                .setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                )
            val shortcuts = la.getShortcuts(query, Process.myUserHandle()) ?: return null
            shortcuts.firstOrNull { it.shortLabel?.toString() == label }?.id
        } catch (e: Exception) {
            Log.w("LegacyShortcut", "findDynamicShortcutId fallita: ${e.message}")
            null
        }
    }

    /**
     * Trova la prima cella libera e ci posiziona la scorciatoia.
     */
    private fun placeShortcutOnFirstFreeCell(
        context: Context,
        packageName: String,
        shortcutId: String,
        instanceId: String
    ): Boolean {
        val cols = PrefsSync.cols(context).coerceAtLeast(1)
        val rows = PrefsSync.rows(context).coerceAtLeast(1)
        val slots = PrefsSync.homeSlots(context).toMutableList()
        val widgets = PrefsSync.widgetSlots(context)

        val occupiedByPage = HashMap<Int, MutableSet<Pair<Int, Int>>>()
        fun occupied(page: Int): MutableSet<Pair<Int, Int>> =
            occupiedByPage.getOrPut(page) { mutableSetOf() }

        slots.forEach { occupied(it.page).add(it.row to it.col) }
        widgets.forEach { w ->
            for (r in w.row until w.row + w.heightCells) {
                for (c in w.col until w.col + w.widthCells) {
                    occupied(w.page).add(r to c)
                }
            }
        }

        var targetPage = 0
        var targetRow = -1
        var targetCol = -1
        var page = 0
        while (targetRow == -1) {
            val occ = occupied(page)
            outer@ for (r in 0 until rows) {
                for (c in 0 until cols) {
                    if (!occ.contains(r to c)) {
                        targetPage = page
                        targetRow = r
                        targetCol = c
                        break@outer
                    }
                }
            }
            if (targetRow == -1) {
                page++
                if (page >= 20) return false
            }
        }

        val newSlot = HomeSlot(
            packageName = packageName,
            row = targetRow,
            col = targetCol,
            page = targetPage,
            shortcutId = shortcutId,
            instanceId = instanceId
        )
        slots.add(newSlot)
        PrefsSync.setHomeSlots(context, slots)

        if (targetPage + 1 > PrefsSync.pageCount(context)) {
            PrefsSync.setPageCount(context, targetPage + 1)
        }
        return true
    }

    private fun saveShortcutIcon(context: Context, uniqueKey: String, intent: Intent) {
        try {
            val drawable: Drawable = extractIcon(context, intent) ?: return
            val bmp = drawableToBitmap(drawable, 512) ?: return

            val dir = File(context.filesDir, "custom_icons")
            if (!dir.exists()) dir.mkdirs()

            val safeName = uniqueKey.replace(Regex("[^A-Za-z0-9_.-]"), "_")
            val file = File(dir, "${safeName}.png")

            FileOutputStream(file).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            PrefsSync.setCustomShortcutIcon(context, uniqueKey, file.absolutePath)
        } catch (e: Exception) {
            Log.w("LegacyShortcut", "Errore salvataggio icona: ${e.message}")
        }
    }

    private fun extractIcon(context: Context, intent: Intent): Drawable? {
        try {
            val bmp: Bitmap? = if (android.os.Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON, Bitmap::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON)
            }
            if (bmp != null) return BitmapDrawable(context.resources, bmp)
        } catch (_: Exception) {}

        try {
            val res: Intent.ShortcutIconResource? = if (android.os.Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE)
            }
            if (res != null) {
                val pm = context.packageManager
                val resId = pm.getResourcesForApplication(res.packageName)
                    .getIdentifier(res.resourceName, null, null)
                if (resId != 0) {
                    return pm.getDrawable(res.packageName, resId, null)
                }
            }
        } catch (_: Exception) {}

        return null
    }

    private fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap? {
        return try {
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                val src = drawable.bitmap
                if (src.width == sizePx && src.height == sizePx) src
                else Bitmap.createScaledBitmap(src, sizePx, sizePx, true)
            } else {
                drawable.toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            }
        } catch (_: Exception) { null }
    }
}
