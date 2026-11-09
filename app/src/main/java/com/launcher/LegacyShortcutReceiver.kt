package com.simplylauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Process
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import java.io.FileOutputStream

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
        val instanceId = "legacy_${System.currentTimeMillis()}_${(0..9999).random()}"

        val realShortcutId = findDynamicShortcutId(context, packageName, label)
        val shortcutId = realShortcutId ?: "legacy"

        val uniqueKey = when {
            realShortcutId != null -> "$packageName|$realShortcutId|$instanceId"
            else -> "$packageName|legacy|$instanceId"
        }

        // Salvataggio dell'Intent da usare al click
        if (url != null) {
            if (isFirefoxLike(packageName)) {
                // Per Firefox: Intent ESPLICITO verso IntentReceiverActivity.
                // Senza questo, Firefox si apre ma non naviga all'URL.
                val firefoxIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    setClassName(packageName, "org.mozilla.fenix.IntentReceiverActivity")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                PrefsSync.setShortcutIntent(
                    context, uniqueKey,
                    firefoxIntent.toUri(Intent.URI_INTENT_SCHEME)
                )
            } else {
                PrefsSync.setShortcutIntent(context, uniqueKey, "url:$url")
            }
        } else {
            PrefsSync.setShortcutIntent(
                context, uniqueKey,
                shortcutIntent.toUri(Intent.URI_INTENT_SCHEME)
            )
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

    private fun isFirefoxLike(pkg: String): Boolean {
        val p = pkg.lowercase()
        return p == "org.mozilla.firefox" ||
               p == "org.mozilla.firefox_beta" ||
               p == "org.mozilla.fennec_fdroid" ||
               p == "org.mozilla.fenix" ||
               p.contains("firefox") ||
               p.contains("fennec") ||
               p.contains("waterfox") ||
               p.contains("mull") ||
               p.contains("iceraven") ||
               p.contains("librewolf")
    }

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
