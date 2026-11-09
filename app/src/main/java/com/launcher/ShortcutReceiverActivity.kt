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

        // === DEBUG: cosa contiene davvero il shortcutIntent di Firefox? ===
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
        // ====================================================================

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

        // Prova a ricavare l'URL dall'Intent (data o extra noti usati dai browser)
        val url = extractUrlFromIntent(shortcutIntent)
        val intentUri = shortcutIntent.toUri(Intent.URI_INTENT_SCHEME)

        // Se l'URL è presente, salvalo in forma "url:" così ShortcutLauncher lo apre diretto.
        // Altrimenti salva l'URI dell'intent per il fallback via startActivity.
        val instanceId = "legacy_${System.currentTimeMillis()}_${(0..9999).random()}"

        // Prova a trovare l'ID reale della scorciatoia registrata dinamicamente da Firefox.
        // Se esiste, useremo startShortcut() con l'ID corretto.
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

        val placed = ShortcutSlotPlacer.placeOnFirstFreeCell(
            context = context,
            packageName = packageName,
            shortcutId = shortcutId,
            instanceId = instanceId,
            label = label
        )

        if (placed) {
            context.sendBroadcast(Intent("com.simplylauncher.RELOAD"))
        } else {
            Log.w("LegacyShortcut", "Nessuna cella libera per $label")
        }
    }

    /**
     * Cerca l'URL nel shortcutIntent: prima in `data`, poi negli extra più comuni
     * usati dai browser (Firefox, Chrome, ecc.).
     */
    private fun extractUrlFromIntent(intent: Intent): String? {
        // 1. intent.data diretto
        val data = intent.dataString
        if (!data.isNullOrBlank() && (data.startsWith("http://") || data.startsWith("https://"))) {
            return data
        }

        // 2. extra comuni
        val knownKeys = listOf(
            "url", "uri", "URL", "Uri", "page_url", "web_url",
            "android.intent.extra.TEXT", Intent.EXTRA_TEXT,
            "com.android.browser.application_id"
        )
        val extras = intent.extras ?: return null
        for (key in knownKeys) {
            val v = extras.get(key)
            if (v is String && (v.startsWith("http://") || v.startsWith("https://"))) {
                return v
            }
        }

        // 3. scansione generica: primo extra String che sembra un URL http(s)
        for (key in extras.keySet()) {
            val v = extras.get(key)
            if (v is String && (v.startsWith("http://") || v.startsWith("https://"))) {
                return v
            }
        }
        return null
    }

    /**
     * Cerca tra le scorciatoie dinamiche/pinned del pacchetto una con lo stesso label.
     * Se trovata, restituisce il suo ID reale: così ShortcutLauncher può usare
     * startShortcut() correttamente invece di un ID inventato.
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
            val shortcuts = la.getShortcuts(query, Process.myUserHandle())
            shortcuts.firstOrNull { it.shortLabel?.toString() == label }?.id
        } catch (e: Exception) {
            Log.w("LegacyShortcut", "findDynamicShortcutId fallita: ${e.message}")
            null
        }
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
