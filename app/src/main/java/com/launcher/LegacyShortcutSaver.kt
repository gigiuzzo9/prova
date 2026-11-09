package com.simplylauncher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

object LegacyShortcutSaver {

    fun save(context: Context, shortcutIntent: Intent, packageName: String, label: String, iconBitmap: Bitmap?) {
        val url = extractUrlFromIntent(shortcutIntent)
        val instanceId = "legacy_${System.currentTimeMillis()}_${(0..9999).random()}"
        val uniqueKey = "$packageName|legacy|$instanceId"

        if (url != null) {
            if (packageName.contains("firefox") || packageName.contains("fennec")) {
                val firefoxIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    setClassName(packageName, "org.mozilla.fenix.IntentReceiverActivity")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                PrefsSync.setShortcutIntent(context, uniqueKey, firefoxIntent.toUri(Intent.URI_INTENT_SCHEME))
            } else {
                PrefsSync.setShortcutIntent(context, uniqueKey, "url:$url")
            }
        } else {
            PrefsSync.setShortcutIntent(context, uniqueKey, shortcutIntent.toUri(Intent.URI_INTENT_SCHEME))
        }

        saveIcon(context, uniqueKey, iconBitmap)
        val placed = placeShortcut(context, packageName, "legacy", instanceId)

        if (placed) {
            context.sendBroadcast(Intent("com.simplylauncher.RELOAD"))
            Toast.makeText(context, "Collegamento aggiunto", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Nessuna cella libera", Toast.LENGTH_SHORT).show()
        }
    }

    private fun extractUrlFromIntent(intent: Intent): String? {
        val data = intent.dataString
        if (!data.isNullOrBlank() && (data.startsWith("http://") || data.startsWith("https://"))) return data
        val extras = intent.extras ?: return null
        for (key in extras.keySet()) {
            val v = extras.get(key)
            if (v is String && (v.startsWith("http://") || v.startsWith("https://"))) return v
        }
        return null
    }

    private fun saveIcon(context: Context, uniqueKey: String, iconBitmap: Bitmap?) {
        if (iconBitmap == null) return
        try {
            val dir = File(context.filesDir, "custom_icons")
            if (!dir.exists()) dir.mkdirs()
            val safeName = uniqueKey.replace(Regex("[^A-Za-z0-9_.-]"), "_")
            val file = File(dir, "${safeName}.png")
            FileOutputStream(file).use { out ->
                iconBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            PrefsSync.setCustomShortcutIcon(context, uniqueKey, file.absolutePath)
        } catch (e: Exception) { Log.w("SaveHelper", "Errore icona: ${e.message}") }
    }

    private fun placeShortcut(context: Context, packageName: String, shortcutId: String, instanceId: String): Boolean {
        val cols = PrefsSync.cols(context).coerceAtLeast(1)
        val rows = PrefsSync.rows(context).coerceAtLeast(1)
        val slots = PrefsSync.homeSlots(context).toMutableList()
        val widgets = PrefsSync.widgetSlots(context)

        val occupiedByPage = HashMap<Int, MutableSet<Pair<Int, Int>>>()
        fun occupied(page: Int): MutableSet<Pair<Int, Int>> = occupiedByPage.getOrPut(page) { mutableSetOf() }

        slots.forEach { occupied(it.page).add(it.row to it.col) }
        widgets.forEach { w ->
            for (r in w.row until w.row + w.heightCells) {
                for (c in w.col until w.col + w.widthCells) {
                    occupied(w.page).add(r to c)
                }
            }
        }

        var targetPage = 0; var targetRow = -1; var targetCol = -1; var page = 0
        while (targetRow == -1) {
            val occ = occupied(page)
            outer@ for (r in 0 until rows) {
                for (c in 0 until cols) {
                    if (!occ.contains(r to c)) {
                        targetPage = page; targetRow = r; targetCol = c
                        break@outer
                    }
                }
            }
            if (targetRow == -1) { page++; if (page >= 20) return false }
        }

        slots.add(HomeSlot(packageName, targetRow, targetCol, targetPage, shortcutId, instanceId))
        PrefsSync.setHomeSlots(context, slots)
        if (targetPage + 1 > PrefsSync.pageCount(context)) PrefsSync.setPageCount(context, targetPage + 1)
        return true
    }
}
