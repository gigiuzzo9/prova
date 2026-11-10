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
        val userIdString = shortcutInfo.userHandle.toString()
            .replace("UserHandle{", "").replace("}", "")
        
        val uniqueKey = "$packageName$SEP$realShortcutId"
        
        Log.d(TAG, "Pin ricevuto: pkg=$packageName id=$realShortcutId")

        // 1. Salvataggio URL / INTENT
        val savedData = "$packageName$SEP$realShortcutId$SEP$userIdString"
        PrefsSync.setShortcutIntent(this, uniqueKey, savedData)
        
        if (realShortcutId.startsWith("http://") || realShortcutId.startsWith("https://")) {
            PrefsSync.setShortcutIntent(this, uniqueKey, "url:$realShortcutId")
            Log.d(TAG, "URL salvato dall'ID: url:$realShortcutId")
        }

        try {
            val intents = shortcutInfo.intents
            if (intents != null && intents.isNotEmpty()) {
                val serialized = intents[0].toUri(0)
                PrefsSync.setShortcutIntent(this, "$uniqueKey$KEY_INTENT_SUFFIX", serialized)
            }
        } catch (e: Exception) {}

        // 2. Posizionamento nella home
        try {
            ShortcutPlacement.placeOnFirstFreeCell(this, packageName, realShortcutId, "")
            Log.d(TAG, "placeOnFirstFreeCell OK")
        } catch (e: Exception) {
            Log.e(TAG, "Errore placeOnFirstFreeCell", e)
        }

        sendBroadcast(Intent("com.simplylauncher.RELOAD"))
    }
}
