package com.simplylauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import java.io.FileOutputStream

/**
 * Riceve le scorciatoie "legacy" inviate tramite broadcast INSTALL_SHORTCUT.
 * Usato da Firefox, Chrome (vecchie versioni), e molti altri browser/app.
 *
 * Extra attesi:
 *  - Intent.EXTRA_SHORTCUT_INTENT  -> Intent da eseguire per aprire il link
 *  - Intent.EXTRA_SHORTCUT_NAME    -> nome visualizzato
 *  - Intent.EXTRA_SHORTCUT_ICON    -> Bitmap (opzionale)
 *  - Intent.EXTRA_SHORTCUT_ICON_RESOURCE -> ShortcutIconResource (opzionale)
 */
class LegacyShortcutReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Supporta sia il nome "vecchio" sia quello nuovo dell'action
        val action = intent.action ?: return
        val validActions = setOf(
            "com.android.launcher.action.INSTALL_SHORTCUT",
            "android.intent.action.INSTALL_SHORTCUT"
        )
        if (action !in validActions) return

        // Se il pacchetto chiamante è sconosciuto, ignora (sicurezza)
        val callingPkg = try {
            context.packageManager.getNameForUid(android.os.Binder.getCallingUid())
        } catch (_: Exception) { null }

        // Estrazione dati
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

        // Il package di riferimento: prima l'app che ha inviato la scorciatoia,
        // poi quello dell'Intent stesso, infine il chiamante.
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

        // Serializza l'Intent in URI per persistenza
        val intentUri = shortcutIntent.toUri(Intent.URI_INTENT_SCHEME)

        // Genera un instanceId univoco per permettere più link della stessa app
        val instanceId = "legacy_${System.currentTimeMillis()}_${(0..9999).random()}"

        // uniqueKey usata da HomeSlot / PrefsSync
        val uniqueKey = "$packageName|legacy|$instanceId"

        // Salva l'Intent nel dizionario
        PrefsSync.setShortcutIntent(context, uniqueKey, intentUri)

        // Salva l'icona custom, se fornita
        saveShortcutIcon(context, uniqueKey, intent)

        // Inserisci lo slot nella home
        val placed = ShortcutSlotPlacer.placeOnFirstFreeCell(
            context = context,
            packageName = packageName,
            shortcutId = "legacy",
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
     * Salva l'icona della scorciatoia (se presente) come PNG in custom_icons.
     * La chiave usata è la uniqueKey della shortcut.
     */
    private fun saveShortcutIcon(context: Context, uniqueKey: String, intent: Intent) {
        try {
            val drawable: Drawable? = extractIcon(context, intent) ?: return
            val bmp = drawableToBitmap(drawable, 512) ?: return

            val dir = File(context.filesDir, "custom_icons")
            if (!dir.exists()) dir.mkdirs()

            // nome file: sanitizza la uniqueKey (rimuovi caratteri non validi)
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
        // 1. Bitmap diretta
        try {
            val bmp: Bitmap? = if (android.os.Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON, Bitmap::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON)
            }
            if (bmp != null) return BitmapDrawable(context.resources, bmp)
        } catch (_: Exception) {}

        // 2. ShortcutIconResource
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
