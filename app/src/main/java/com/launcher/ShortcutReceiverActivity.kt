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

        val pinRequest = intent.getParcelableExtra<LauncherApps.PinItemRequest>(
            LauncherApps.EXTRA_PIN_ITEM_REQUEST
        )
        Log.d(TAG, "pinRequest=$pinRequest")
        if (pinRequest == null) { Log.e(TAG, "pinRequest null"); return }

        val shortcutInfo = pinRequest.shortcutInfo
        Log.d(TAG, "shortcutInfo=$shortcutInfo")
        if (shortcutInfo == null) { Log.e(TAG, "shortcutInfo null"); return }

        val accepted = pinRequest.accept()
        Log.d(TAG, "accept=$accepted")
        if (!accepted) { Log.e(TAG, "accept false"); return }

        val packageName = shortcutInfo.`package`
        val realShortcutId = shortcutInfo.id
        val userIdString = shortcutInfo.userHandle.toString()
            .replace("UserHandle{", "").replace("}", "")
        val uniqueKey = "$packageName$SEP$realShortcutId"
        Log.d(TAG, "pkg=$packageName id=$realShortcutId user=$userIdString key=$uniqueKey")

        val savedData = "$packageName$SEP$realShortcutId$SEP$userIdString"
        PrefsSync.setShortcutIntent(this, uniqueKey, savedData)
        Log.d(TAG, "salvato canonico: $savedData")

        // --- Fallback per Firefox: salva l'intent serializzato se disponibile ---
        // ATTENZIONE: shortcutInfo.intents può essere null. Gestito in modo sicuro.
        try {
            val intents = shortcutInfo.intents
            Log.d(TAG, "intents size = ${intents?.size ?: -1}")
            if (intents != null && intents.isNotEmpty()) {
                val serialized = intents[0].toUri(0)
                PrefsSync.setShortcutIntent(this, "$uniqueKey$KEY_INTENT_SUFFIX", serialized)
                Log.d(TAG, "salvato intent serializzato: $serialized")
            } else {
                Log.w(TAG, "intents null o vuoto: Firefox non espone l'URL")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "errore su intents", e)
        }

        // --- Posizionamento nella home ---
        try {
            ShortcutPlacement.placeOnFirstFreeCell(this, packageName, realShortcutId, "")
            Log.d(TAG, "placeOnFirstFreeCell OK")
        } catch (e: Throwable) {
            Log.e(TAG, "errore placeOnFirstFreeCell", e)
        }

        sendBroadcast(Intent("com.simplylauncher.RELOAD"))
    }
}
