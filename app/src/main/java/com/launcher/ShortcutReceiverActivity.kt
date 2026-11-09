package com.simplylauncher

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ShortcutReceiverActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val launcherApps = getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps
        val request = launcherApps.getPinItemRequest(intent)

        if (request != null && request.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) {
            val shortcutInfo = request.shortcutInfo
            if (shortcutInfo == null) { finish(); return }

            val packageName = shortcutInfo.`package`
            val label = try {
                shortcutInfo.shortLabel?.toString() ?: run {
                    val ai = packageManager.getApplicationInfo(packageName, 0)
                    packageManager.getApplicationLabel(ai).toString()
                }
            } catch (_: Exception) { packageName }

            val iconDrawable = try { launcherApps.getApplicationIcon(packageName, Process.myUserHandle()) } catch (_: Exception) { null }
            val iconBitmap = (iconDrawable as? BitmapDrawable)?.bitmap

            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(60, 60, 60, 40)
                gravity = Gravity.CENTER
            }
            val iconView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(160, 160)
                if (iconBitmap != null) setImageBitmap(iconBitmap)
                else if (iconDrawable != null) setImageDrawable(iconDrawable)
            }
            val textView = TextView(this).apply {
                text = label
                textSize = 18f
                setTextColor(Color.BLACK)
                setPadding(0, 30, 0, 0)
                gravity = Gravity.CENTER
            }
            layout.addView(iconView)
            layout.addView(textView)

            AlertDialog.Builder(this)
                .setTitle("Aggiungi alla schermata Home")
                .setView(layout)
                .setPositiveButton("AGGIUNGI") { _, _ ->
                    if (request.accept()) {
                        val existingSlots = PrefsSync.homeSlots(this)
                        val sameShortcutExists = existingSlots.any {
                            it.packageName == packageName && it.shortcutId == shortcutInfo.id
                        }
                        val instanceId = if (sameShortcutExists) {
                            "inst_${System.currentTimeMillis()}_${(0..9999).random()}"
                        } else ""

                        val uniqueKey = when {
                            instanceId.isEmpty() -> "$packageName|${shortcutInfo.id}"
                            else -> "$packageName|${shortcutInfo.id}|$instanceId"
                        }

                        val url = extractUrlFromIntent(intent)
                        if (url != null) {
                            PrefsSync.setShortcutIntent(this, uniqueKey, "url:$url")
                        } else {
                            PrefsSync.setShortcutIntent(this, uniqueKey, "")
                        }

                        placeShortcutOnFirstFreeCell(packageName, shortcutInfo.id, instanceId)
                        sendBroadcast(Intent("com.simplylauncher.RELOAD"))
                    }
                    finish()
                }
                .setNegativeButton("ANNULLA") { _, _ -> finish() }
                .setOnCancelListener { finish() }
                .show()
        } else {
            finish()
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

    private fun placeShortcutOnFirstFreeCell(packageName: String, shortcutId: String, instanceId: String): Boolean {
        val cols = PrefsSync.cols(this).coerceAtLeast(1)
        val rows = PrefsSync.rows(this).coerceAtLeast(1)
        val slots = PrefsSync.homeSlots(this).toMutableList()
        val widgets = PrefsSync.widgetSlots(this)
        val occupied = mutableSetOf<Pair<Int, Int>>()

        slots.forEach { occupied.add(it.row to it.col) }
        widgets.forEach { w ->
            for (r in w.row until w.row + w.heightCells) {
                for (c in w.col until w.col + w.widthCells) {
                    occupied.add(r to c)
                }
            }
        }

        var r = 0; var c = 0; var p = 0; var found = false
        while (!found && p < 20) {
            if (!occupied.contains(r to c)) { found = true }
            else { c++; if (c >= cols) { c = 0; r++; if (r >= rows) { r = 0; p++ } } }
        }
        if (!found) return false

        slots.add(HomeSlot(packageName, r, c, p, shortcutId, instanceId))
        PrefsSync.setHomeSlots(this, slots)
        if (p + 1 > PrefsSync.pageCount(this)) PrefsSync.setPageCount(this, p + 1)
        return true
    }
}
