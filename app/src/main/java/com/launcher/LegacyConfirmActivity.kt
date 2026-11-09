package com.simplylauncher

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

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
            setTextColor(android.graphics.Color.BLACK)
            setPadding(0, 30, 0, 0)
            gravity = Gravity.CENTER
        }

        layout.addView(iconView)
        layout.addView(textView)

        AlertDialog.Builder(this)
            .setTitle("Aggiungi alla schermata Home")
            .setView(layout)
            .setPositiveButton("AGGIUNGI") { _, _ ->
                LegacyShortcutSaver.save(this, shortcutIntent, packageName, label, iconBitmap)
                finish()
            }
            .setNegativeButton("ANNULLA") { _, _ ->
                finish()
            }
            .setOnCancelListener { finish() }
            .show()
    }
}
