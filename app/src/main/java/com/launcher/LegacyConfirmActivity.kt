package com.simplylauncher

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class LegacyConfirmActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val shortcutIntent = if (android.os.Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra("shortcut_intent", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Intent>("shortcut_intent")
        }

        if (shortcutIntent == null) {
            finish()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Aggiungi alla Home")
            .setMessage("Vuoi aggiungere questa scorciatoia?")
            .setPositiveButton("AGGIUNGI") { _, _ ->
                // Salvataggio super base per non far crashare
                val pkg = shortcutIntent.`package` ?: "unknown"
                val uniqueKey = "$pkg|legacy|${System.currentTimeMillis()}"
                PrefsSync.setShortcutIntent(this, uniqueKey, shortcutIntent.toUri(Intent.URI_INTENT_SCHEME))
                
                // (Qui manca la chiamata a PrefsSync.homeSlots per aggiungere l'icona, 
                // ma per ora testiamo solo se non crasha)
                
                finish()
            }
            .setNegativeButton("ANNULLA") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }
}
