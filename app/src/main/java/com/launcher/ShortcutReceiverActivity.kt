package com.simplylauncher

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class ShortcutReceiverActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ShortcutReceiver"
        private const val SEP = "|"
        private const val KEY_INTENT_SUFFIX = "|intent"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            finish()
            return
        }

        val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val pinRequest = intent.getParcelableExtra<LauncherApps.PinItemRequest>(
            LauncherApps.EXTRA_PIN_ITEM_REQUEST
        )

        if (pinRequest == null || pinRequest.shortcutInfo == null || !pinRequest.accept()) {
            finish()
            return
        }

        val shortcutInfo = pinRequest.shortcutInfo!!
        val packageName = shortcutInfo.`package`
        val realShortcutId = shortcutInfo.id

        // Estraiamo l'ID utente esattamente come fa Discreet:
        // "UserHandle{0}" -> "0"
        val userIdString = shortcutInfo.userHandle.toString()
            .replace("UserHandle{", "")
            .replace("}", "")

        val uniqueKey = "$packageName$SEP$realShortcutId"

        // --- 1. Formato canonico (Discreet): package|id|user_id ---
        val savedData = "$packageName$SEP$realShortcutId$SEP$userIdString"
        PrefsSync.setShortcutIntent(this, uniqueKey, savedData)
        Log.d(TAG, "Salvato formato canonico: $savedData")

        // --- 2. Fallback per Firefox e derivati ---
        // Firefox pubblica l'URL negli extras dell'intent. LauncherApps.startShortcut()
        // non propaga gli extras, quindi al click il browser si apre senza pagina.
        // Salvando l'intent serializzato possiamo rilanciarlo con Intent.parseUri(),
        // che invece preserva gli extras.
        val intents = shortcutInfo.intents
        if (!intents.isNullOrEmpty()) {
            try {
                val serialized = intents[0].toUri(0)
                PrefsSync.setShortcutIntent(this, "$uniqueKey$KEY_INTENT_SUFFIX", serialized)
                Log.d(TAG, "Salvato intent serializzato: $serialized")
            } catch (e: Exception) {
                Log.e(TAG, "Impossibile serializzare l'intent", e)
            }
        } else {
            Log.w(TAG, "shortcutInfo.intents è null/vuoto per $packageName/$realShortcutId")
        }

        // --- 3. Posizionamento nella home ---
        ShortcutPlacement.placeOnFirstFreeCell(this, packageName, realShortcutId, "")

        sendBroadcast(Intent("com.simplylauncher.RELOAD"))
        finish()
    }
}
