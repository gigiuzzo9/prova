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
            .replace("UserHandle{", "")
            .replace("}", "")
        val uniqueKey = "$packageName$SEP$realShortcutId"
        Log.d(TAG, "pkg=$packageName id=$realShortcutId user=$userIdString key=$uniqueKey")

        // --- 1. Se l'ID è un URL (Iceraven/Firefox), salvalo come URL puro ---
        // Questo è il caso che fa funzionare Firefox/Iceraven.
        if (realShortcutId.startsWith("http://") || realShortcutId.startsWith("https://")) {
            PrefsSync.setShortcutIntent(this, uniqueKey, "url:$realShortcutId")
            Log.d(TAG, "URL salvato dall'ID: url:$realShortcutId")
        } else {
            // --- 2. Altrimenti salva il formato canonico (Chrome e derivati) ---
            val savedData = "$packageName$SEP$realShortcutId$SEP$userIdString"
            PrefsSync.setShortcutIntent(this, uniqueKey, savedData)
            Log.d(TAG, "salvato canonico: $savedData")
        }

        // --- 3. Posizionamento nella home ---
        try {
            ShortcutPlacement.placeOnFirstFreeCell(this, packageName, realShortcutId, "")
            Log.d(TAG, "placeOnFirstFreeCell OK")
        } catch (e: Throwable) {
            Log.e(TAG, "errore placeOnFirstFreeCell", e)
        }

        sendBroadcast(Intent("com.simplylauncher.RELOAD"))
    }
}
