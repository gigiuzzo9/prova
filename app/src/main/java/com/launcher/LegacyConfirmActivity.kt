package com.simplylauncher

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class LegacyConfirmActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val shortcutIntent = if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra("shortcut_intent", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("shortcut_intent")
        }

        val shortcutName = intent.getStringExtra("shortcut_name")
        val packageName = intent.getStringExtra("package_name")
        val iconBitmap = if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra("shortcut_icon", Bitmap::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Bitmap>("shortcut_icon")
        }

        if (shortcutIntent == null || packageName == null) {
            finish()
            return
        }

        val label = if (!shortcutName.isNullOrBlank()) shortcutName else {
            try {
                val ai = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(ai).toString()
            } catch (_: Exception) { packageName }
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 60, 60, 40)
            gravity = Gravity.CENTER
        }

        val iconView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(160, 160)
            if (iconBitmap != null) {
                setImageBitmap(iconBitmap)
            } else {
                try { setImageDrawable(packageManager.getApplicationIcon(packageName)) } catch (_: Exception) {}
            }
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
                saveShortcut(shortcutIntent, packageName, label, iconBitmap)
                finish()
            }
            .setNegativeButton("ANNULLA") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun saveShortcut(shortcutIntent: Intent, packageName: String, label: String, iconBitmap: Bitmap?) {
        val instanceId = "legacy_${System.currentTimeMillis()}_${(0..9999).random()}"
        val uniqueKey = "$packageName|legacy|$instanceId"
        val url = extractUrlFromIntent(shortcutIntent)

        if (url != null) {
            if (packageName.contains("firefox") || packageName.contains("fennec")) {
                val firefoxIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    setClassName(packageName, "org.mozilla.fenix.IntentReceiverActivity")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                PrefsSync.setShortcutIntent(this, uniqueKey, firefoxIntent.toUri(Intent.URI_INTENT_SCHEME))
            } else {
                PrefsSync.setShortcutIntent(this, uniqueKey, "url:$url")
            }
        } else {
            PrefsSync.setShortcutIntent(this, uniqueKey, shortcutIntent.toUri(Intent.URI_INTENT_SCHEME))
        }

        if (iconBitmap != null) {
            try {
                val dir = File(filesDir, "custom_icons")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, "${uniqueKey.replace(Regex("[^A-Za-z0-9_.-]"), "_")}.png")
                FileOutputStream(file).use { out -> iconBitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
                PrefsSync.setCustomShortcutIcon(this, uniqueKey, file.absolutePath)
            } catch (_: Exception) {}
        }

        placeShortcutOnFirstFreeCell(packageName, "legacy", instanceId)
        sendBroadcast(Intent("com.launcher.RELOAD"))
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
