package com.simplylauncher

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class ShortcutReceiverActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ShortcutReceiver"
        private const val SEP = "|"
    }

    private var pinRequest: LauncherApps.PinItemRequest? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            finish()
            return
        }

        val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        pinRequest = intent.getParcelableExtra(LauncherApps.EXTRA_PIN_ITEM_REQUEST)

        val request = pinRequest
        val shortcutInfo = request?.shortcutInfo

        if (request == null || shortcutInfo == null || !request.isValid) {
            Log.e(TAG, "PinItemRequest non valido")
            finish()
            return
        }

        val packageName = shortcutInfo.`package`
        val shortcutId = shortcutInfo.id
        val label = shortcutInfo.shortLabel?.toString() ?: packageName
        val userIdString = shortcutInfo.userHandle.toString()
            .replace("UserHandle{", "").replace("}", "")

        Log.d(TAG, "Pin ricevuto: pkg=$packageName id=$shortcutId label=$label user=$userIdString")

        val icon: Drawable? = if (launcherApps.hasShortcutHostPermission()) {
            try { launcherApps.getShortcutIconDrawable(shortcutInfo, 0) } catch (e: Exception) { null }
        } else null

        val builder = AlertDialog.Builder(this)
            .setTitle("Aggiungi scorciatoia")
            .setMessage(label)
            .setPositiveButton("Aggiungi") { _, _ -> confirmAndSave(shortcutInfo) }
            .setNegativeButton("Annulla") { _, _ -> finish() }
            .setCancelable(false)

        if (icon != null) builder.setIcon(icon)

        builder.show()
    }

    private fun confirmAndSave(shortcutInfo: ShortcutInfo) {
        val request = pinRequest ?: run { finish(); return }

        val accepted = request.accept()
        Log.d(TAG, "accept=$accepted")
        if (!accepted) { finish(); return }

        val packageName = shortcutInfo.`package`
        val shortcutId = shortcutInfo.id
        val displayName = shortcutInfo.shortLabel?.toString() ?: "Scorciatoia"
        val userIdString = shortcutInfo.userHandle.toString()
            .replace("UserHandle{", "").replace("}", "")

        // --- SALVATAGGIO ---
        // Se l'ID è un URL (Firefox/Chrome), salvalo come URL puro.
        // Altrimenti salva package|id|user, ma con un timestamp per evitare sovrascritture.
        val isUrl = shortcutId.startsWith("http://") || shortcutId.startsWith("https://")
        
        // Chiave univoca: se è URL, uso l'URL come parte della chiave (per evitare duplicati)
        // altrimenti uso un timestamp.
        val uniqueKey = if (isUrl) {
            // Puliamo l'URL per renderlo sicuro come chiave
            val safeUrl = shortcutId.replace("|", "_").replace(":", "_").replace("/", "_")
            "$packageName$SEP$safeUrl"
        } else {
            "$packageName$SEP$shortcutId$SEP${System.currentTimeMillis()}"
        }

        if (isUrl) {
            PrefsSync.setShortcutIntent(this, uniqueKey, "url:$shortcutId")
            Log.d(TAG, "Salvato URL: url:$shortcutId")
        } else {
            val savedData = "$packageName$SEP$shortcutId$SEP$userIdString"
            PrefsSync.setShortcutIntent(this, uniqueKey, savedData)
            Log.d(TAG, "Salvato canonico: $savedData")
        }

        // --- ICONA ---
        try {
            val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val iconDrawable = launcherApps.getShortcutIconDrawable(shortcutInfo, 0)
            val appIcon = packageManager.getApplicationIcon(packageName)

            val isDifferentFromApp = iconDrawable != null &&
                                     iconDrawable.constantState != appIcon.constantState

            val filename = "shortcut_icon_${uniqueKey.hashCode()}.png"
            val file = File(filesDir, filename)
            var bitmap: Bitmap? = null

            if (isDifferentFromApp) {
                bitmap = Bitmap.createBitmap(
                    iconDrawable!!.intrinsicWidth.coerceAtLeast(1),
                    iconDrawable.intrinsicHeight.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bitmap)
                iconDrawable.setBounds(0, 0, canvas.width, canvas.height)
                iconDrawable.draw(canvas)
            } else {
                val size = 192
                bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                val colors = intArrayOf(0xFFF44336.toInt(), 0xFF2196F3.toInt(), 0xFF4CAF50.toInt(), 0xFFFF9800.toInt())
                paint.color = colors[Math.abs(displayName.hashCode()) % colors.size]
                canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
                paint.color = android.graphics.Color.WHITE
                paint.textSize = size / 2f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(displayName.take(1).uppercase(), size / 2f, size / 2f + (paint.textSize / 3), paint)
            }

            if (bitmap != null) {
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                PrefsSync.setCustomShortcutIcon(this, uniqueKey, file.absolutePath)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore salvataggio icona", e)
        }

        // --- POSIZIONAMENTO ---
        try {
            ShortcutPlacement.placeOnFirstFreeCell(this, packageName, uniqueKey, "")
            Log.d(TAG, "placeOnFirstFreeCell OK")
        } catch (e: Exception) {
            Log.e(TAG, "errore placeOnFirstFreeCell", e)
        }

        sendBroadcast(Intent("com.simplylauncher.RELOAD"))
        finish()
    }
}
