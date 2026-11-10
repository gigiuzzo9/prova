package com.simplylauncher

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

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

        // Icona della scorciatoia (se disponibile)
        val icon: Drawable? = if (launcherApps.hasShortcutHostPermission()) {
            try { launcherApps.getShortcutIconDrawable(shortcutInfo, 0) } catch (e: Exception) { null }
        } else null

        // --- FINESTRA DI CONFERMA (il pezzo che mancava) ---
        val builder = AlertDialog.Builder(this)
            .setTitle("Aggiungi scorciatoia")
            .setMessage(label)
            .setPositiveButton("Aggiungi") { _, _ -> confirmAndSave(shortcutInfo) }
            .setNegativeButton("Annulla") { _, _ ->
                Log.d(TAG, "Utente ha annullato")
                finish()
            }
            .setCancelable(false)

        if (icon != null) builder.setIcon(icon)

        builder.show()
    }

    private fun confirmAndSave(shortcutInfo: android.content.pm.ShortcutInfo) {
        val request = pinRequest ?: run { finish(); return }

        // Accetta SOLO ORA, dopo la conferma dell'utente
        val accepted = request.accept()
        Log.d(TAG, "accept=$accepted")
        if (!accepted) { finish(); return }

        val packageName = shortcutInfo.`package`
        val shortcutId = shortcutInfo.id
        val userIdString = shortcutInfo.userHandle.toString()
            .replace("UserHandle{", "").replace("}", "")
        val uniqueKey = "$packageName$SEP$shortcutId"

        // Salva formato canonico (per Chrome e altri browser)
        val savedData = "$packageName$SEP$shortcutId$SEP$userIdString"
        PrefsSync.setShortcutIntent(this, uniqueKey, savedData)
        Log.d(TAG, "salvato canonico: $savedData")

        // Se l'ID è un URL (Iceraven/Firefox), salvalo come URL puro
        if (shortcutId.startsWith("http://") || shortcutId.startsWith("https://")) {
            PrefsSync.setShortcutIntent(this, uniqueKey, "url:$shortcutId")
            Log.d(TAG, "URL salvato dall'ID: url:$shortcutId")
        }

        // Posiziona nella home
        try {
            ShortcutPlacement.placeOnFirstFreeCell(this, packageName, shortcutId, "")
            Log.d(TAG, "placeOnFirstFreeCell OK")
        } catch (e: Exception) {
            Log.e(TAG, "errore placeOnFirstFreeCell", e)
        }

        sendBroadcast(Intent("com.simplylauncher.RELOAD"))
        finish()
    }
}
