package com.simplylauncher

import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class ShortcutReceiverActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val launcherApps = getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps
        val pinRequest = intent.getParcelableExtra<LauncherApps.PinItemRequest>(LauncherApps.EXTRA_PIN_ITEM_REQUEST)

        if (pinRequest != null && pinRequest.shortcutInfo != null && pinRequest.accept()) {
            val shortcutInfo = pinRequest.shortcutInfo!!
            val packageName = shortcutInfo.`package`
            val realShortcutId = shortcutInfo.id
            
            // Estraiamo l'ID utente esattamente come fa Discreet
            val userIdString = shortcutInfo.userHandle.toString().replace("UserHandle{", "").replace("}", "")

            // Salviamo tutto in una stringa: package|id|user_id
            val savedData = "$packageName|$realShortcutId|$userIdString"
            val uniqueKey = "$packageName|$realShortcutId"

            Log.d("ShortcutReceiver", "Salvato: $savedData")
            PrefsSync.setShortcutIntent(this, uniqueKey, savedData)

            ShortcutPlacement.placeOnFirstFreeCell(this, packageName, realShortcutId, "")
            sendBroadcast(Intent("com.simplylauncher.RELOAD"))
        }
        finish()
    }
}
