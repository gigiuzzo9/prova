package com.simplylauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class LegacyShortcutReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.android.launcher.action.INSTALL_SHORTCUT" &&
            intent.action != "android.intent.action.INSTALL_SHORTCUT") {
            return
        }

        val shortcutIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_SHORTCUT_INTENT)
        val shortcutName = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME) ?: "Link"

        if (shortcutIntent == null) {
            Log.e("LEGACY_SHORTCUT", "Nessun Intent trovato nel broadcast")
            return
        }

        val packageName = shortcutIntent.component?.packageName ?: shortcutIntent.`package` ?: ""
        val shortcutId = shortcutIntent.getStringExtra("shortcut_id") ?: shortcutIntent.dataString ?: ""

        if (packageName.isEmpty() || shortcutId.isEmpty()) {
            Log.e("LEGACY_SHORTCUT", "Package o ID mancante")
            return
        }

        val instanceId = "legacy_${System.currentTimeMillis()}"

        val uniqueKey = when {
            instanceId.isEmpty() -> "$packageName|$shortcutId"
            else -> "$packageName|$shortcutId|$instanceId"
        }

        try {
            val intentUri = shortcutIntent.toUri(Intent.URI_INTENT_SCHEME)
            PrefsSync.setShortcutIntent(context, uniqueKey, intentUri)
            Log.d("LEGACY_SHORTCUT", "Intent salvato: $intentUri")
        } catch (e: Exception) {
            Log.e("LEGACY_SHORTCUT", "Errore salvataggio Intent", e)
            return
        }

        addShortcutToHome(context, packageName, shortcutId, instanceId)
    }

    private fun addShortcutToHome(
        context: Context,
        packageName: String,
        shortcutId: String,
        instanceId: String
    ) {
        val cols = PrefsSync.cols(context).coerceAtLeast(1)
        val rows = PrefsSync.rows(context).coerceAtLeast(1)
        val slots = PrefsSync.homeSlots(context).toMutableList()
        val widgets = PrefsSync.widgetSlots(context)

        val occupied = mutableSetOf<Pair<Int, Int>>()
        slots.forEach { occupied.add(it.row to it.col) }
        widgets.forEach { w ->
            for (r in w.row until w.row + w.heightCells) {
                for (c in w.col until w.col + w.widthCells) {
                    occupied.add(r to c)
                }
            }
        }

        var found = false
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (!occupied.contains(r to c)) {
                    slots.add(
                        HomeSlot(
                            packageName = packageName,
                            row = r,
                            col = c,
                            page = 0,
                            shortcutId = shortcutId,
                            instanceId = instanceId
                        )
                    )
                    found = true
                    break
                }
            }
            if (found) break
        }

        if (found) {
            PrefsSync.setHomeSlots(context, slots)
            context.sendBroadcast(Intent("com.simplylauncher.RELOAD"))
        }
    }
}
