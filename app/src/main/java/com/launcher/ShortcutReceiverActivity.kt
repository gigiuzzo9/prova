package com.simplylauncher

import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class ShortcutReceiverActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val launcherApps = getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps
        val request = launcherApps.getPinItemRequest(intent)

        if (request != null && request.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) {
            val shortcutInfo = request.shortcutInfo
            if (shortcutInfo != null && request.accept()) {
                val packageName = shortcutInfo.`package`
                val realShortcutId = shortcutInfo.id

                val existingSlots = PrefsSync.homeSlots(this)
                val sameShortcutExists = existingSlots.any {
                    it.packageName == packageName && it.shortcutId == realShortcutId
                }
                val instanceId = if (sameShortcutExists) {
                    "inst_${System.currentTimeMillis()}_${(0..9999).random()}"
                } else {
                    ""
                }

                val uniqueKey = when {
                    instanceId.isEmpty() -> "$packageName|$realShortcutId"
                    else -> "$packageName|$realShortcutId|$instanceId"
                }

                // ---- Estrai l'URL/Intent della scorciatoia ----
                // shortcutInfo.intent può essere null per le scorciatoie web.
                // Proviamo diverse fonti in ordine di affidabilità.
                val url: String? = extractUrlFromShortcut(shortcutInfo)

                // Salva l'Intent se disponibile
                val originalIntent = shortcutInfo.intent
                if (originalIntent != null) {
                    try {
                        val intentUri = originalIntent.toUri(Intent.URI_INTENT_SCHEME)
                        PrefsSync.setShortcutIntent(this, uniqueKey, intentUri)
                    } catch (_: Exception) {}
                } else if (url != null) {
                    // Nessun Intent ma abbiamo l'URL: costruiamo un Intent VIEW e salviamolo
                    try {
                        val syntheticIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        val intentUri = syntheticIntent.toUri(Intent.URI_INTENT_SCHEME)
                        PrefsSync.setShortcutIntent(this, uniqueKey, intentUri)
                    } catch (_: Exception) {}
                }

                placeShortcutOnFirstFreeCell(packageName, realShortcutId, instanceId)
                sendBroadcast(Intent("com.simplylauncher.RELOAD"))
            }
        }
        finish()
    }

    /**
     * Prova a estrarre l'URL della scorciatoia da varie fonti:
     * 1. shortcutInfo.intent (se presente)
     * 2. extras (chiavi note usate dai browser)
     * 3. longLabel/shortLabel (a volte contengono l'URL)
     */
    private fun extractUrlFromShortcut(shortcutInfo: android.content.pm.ShortcutInfo): String? {
        // 1. Intent
        val intentData = shortcutInfo.intent?.data
        if (intentData != null && (intentData.scheme == "http" || intentData.scheme == "https")) {
            return intentData.toString()
        }

        // 2. Extras note
        val extras = shortcutInfo.extras
        if (extras != null) {
            val keys = listOf(
                "url", "URL", "uri", "URI", "web_url", "webUrl",
                "android.intent.extra.TEXT",
                "com.android.browser.application_id"
            )
            for (key in keys) {
                val value = extras.getString(key)
                if (!value.isNullOrEmpty() && (value.startsWith("http://") || value.startsWith("https://"))) {
                    return value
                }
            }
        }

        // 3. Label (fallback raro ma succede con alcuni browser)
        val label = shortcutInfo.longLabel?.toString() ?: shortcutInfo.shortLabel?.toString()
        if (!label.isNullOrEmpty() && (label.startsWith("http://") || label.startsWith("https://"))) {
            return label
        }

        return null
    }

    private fun placeShortcutOnFirstFreeCell(
        packageName: String,
        shortcutId: String,
        instanceId: String
    ): Boolean {
        val cols = PrefsSync.cols(this).coerceAtLeast(1)
        val rows = PrefsSync.rows(this).coerceAtLeast(1)
        val slots = PrefsSync.homeSlots(this).toMutableList()
        val widgets = PrefsSync.widgetSlots(this)

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
                if (page >= 20) {
                    return false
                }
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
        PrefsSync.setHomeSlots(this, slots)

        if (targetPage + 1 > PrefsSync.pageCount(this)) {
            PrefsSync.setPageCount(this, targetPage + 1)
        }
        return true
    }
}
