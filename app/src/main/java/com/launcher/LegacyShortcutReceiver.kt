package com.simplylauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class LegacyShortcutReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val validActions = setOf(
            "com.android.launcher.action.INSTALL_SHORTCUT",
            "android.intent.action.INSTALL_SHORTCUT"
        )
        if (action !in validActions) return

        val shortcutIntent: Intent? = try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT)
            }
        } catch (_: Exception) { null }

        if (shortcutIntent == null) {
            Log.w("LegacyShortcut", "Nessun EXTRA_SHORTCUT_INTENT ricevuto")
            return
        }

        val packageName = shortcutIntent.`package`
            ?: shortcutIntent.component?.packageName
            ?: return

        val iconBitmap = if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON, android.graphics.Bitmap::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<android.graphics.Bitmap>(Intent.EXTRA_SHORTCUT_ICON)
        }

        // Avviamo la finestra di conferma!
        val confirmIntent = Intent(context, LegacyConfirmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("shortcut_intent", shortcutIntent)
            putExtra("shortcut_name", intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME))
            putExtra("package_name", packageName)
            if (iconBitmap != null) putExtra("shortcut_icon", iconBitmap)
        }
        context.startActivity(confirmIntent)
    }
}
