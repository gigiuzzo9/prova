package com.simplylauncher

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class ShortcutReceiverActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ShortcutReceiver"
        private const val SEP = "|"
        private const val KEY_INTENT_SUFFIX = "|intent"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            handlePinRequest()
        } catch (e: Throwable) {
            Log.e(TAG, "CRASH in handlePinRequest", e)
        } finally {
            finish()
        }
    }

    private fun handlePinRequest() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val pinRequest = intent.getParcelableExtra<LauncherApps.PinItemRequest>(
            LauncherApps.EXTRA_PIN_ITEM_REQUEST
        )

        if (pinRequest == null || pinRequest.shortcutInfo == null) {
            Log.e(TAG, "PinItemRequest o ShortcutInfo nulli")
            return
        }

        val shortcutInfo = pinRequest.shortcutInfo!!
        
        if (!pinRequest.accept()) {
            Log.e(TAG, "accept() fallito")
            return
        }

        val packageName = shortcutInfo.`package`
        val realShortcutId = shortcutInfo.id
        val displayName = shortcutInfo.shortLabel?.toString() ?: "Scorciatoia"
        val userIdString = shortcutInfo.userHandle.toString()
            .replace("UserHandle{", "").replace("}", "")
        
        val uniqueKey = "$packageName$SEP$realShortcutId"
        
        Log.d(TAG, "Pin ricevuto: pkg=$packageName id=$realShortcutId label=$displayName")

        // 1. Salvataggio URL / INTENT
        val savedData = "$packageName$SEP$realShortcutId$SEP$userIdString"
        PrefsSync.setShortcutIntent(this, uniqueKey, savedData)
        
        if (realShortcutId.startsWith("http://") || realShortcutId.startsWith("https://")) {
            PrefsSync.setShortcutIntent(this, uniqueKey, "url:$realShortcutId")
            Log.d(TAG, "URL salvato dall'ID: url:$realShortcutId")
        } else {
            Log.d(TAG, "Salvato canonico: $savedData")
        }

        try {
            val intents = shortcutInfo.intents
            if (intents != null && intents.isNotEmpty()) {
                val serialized = intents[0].toUri(0)
                PrefsSync.setShortcutIntent(this, "$uniqueKey$KEY_INTENT_SUFFIX", serialized)
            }
        } catch (e: Exception) {}

        // 2. GESTIONE ICONA (Versione Nova-Style)
        try {
            val iconDrawable = launcherApps.getShortcutIconDrawable(shortcutInfo, 0)
            
            val filename = "shortcut_icon_${uniqueKey.hashCode()}.png"
            val file = File(filesDir, filename)
            var bitmap: Bitmap? = null

            if (iconDrawable != null) {
                // Prendiamo l'icona che Firefox ci passa (come fa Nova)
                bitmap = Bitmap.createBitmap(
                    iconDrawable.intrinsicWidth.coerceAtLeast(1),
                    iconDrawable.intrinsicHeight.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bitmap)
                iconDrawable.setBounds(0, 0, canvas.width, canvas.height)
                iconDrawable.draw(canvas)
                Log.d(TAG, "Icona di Firefox salvata")
            } else {
                // Se Firefox non passa NESSUNA icona, generiamo il cerchio
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
                val letter = displayName.take(1).uppercase()
                canvas.drawText(letter, size / 2f, size / 2f + (paint.textSize / 3), paint)
                Log.d(TAG, "Icona segnaposto generata (nulla da Firefox)")
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

        // 3. POSIZIONAMENTO
        try {
            ShortcutPlacement.placeOnFirstFreeCell(this, packageName, realShortcutId, "")
        } catch (e: Exception) {}

        sendBroadcast(Intent("com.simplylauncher.RELOAD"))
    }
}
