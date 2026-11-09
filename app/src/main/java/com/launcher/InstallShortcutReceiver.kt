package com.simplylauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Parcelable

class InstallShortcutReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Firefox e derivati usano il broadcast legacy
        if (intent.action != ACTION_INSTALL_SHORTCUT) return

        @Suppress("DEPRECATION")
        val shortcutIntent = intent.getParcelableExtra<Parcelable>(Intent.EXTRA_SHORTCUT_INTENT)
            as? Intent ?: return

        // Nome e icona (opzionali, ma utili se in futuro vuoi mostrarli)
        val name = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME).orEmpty()

        val packageName = shortcutIntent.`package` ?: return

        // Serializziamo l'intent originale: ShortcutLauncher.launch() lo riattiverà
        // tramite Intent.parseUri(..., URI_INTENT_SCHEME) nel punto 3.
        val serialized = shortcutIntent.toUri(Intent.URI_INTENT_SCHEME)

        // Se Firefox manda un intent di tipo ACTION_VIEW verso il proprio package,
        // forziamo il browser del sistema per evitare che si riapra sempre Firefox.
        val finalSerialized = if (
            shortcutIntent.action == Intent.ACTION_VIEW &&
            shortcutIntent.data != null
        ) {
            val url = shortcutIntent.data.toString()
            // Salviamo l'URL puro: ShortcutLauncher lo gestisce nel punto 1
            "url:$url"
        } else {
            serialized
        }

        // Genera un id univoco per questa scorciatoia (non abbiamo ShortcutInfo)
        val shortcutId = "web_${System.currentTimeMillis()}_${(0..9999).random()}"

        // Verifica duplicati: se esiste già lo stesso package+url, riusa l'id
        val existing = PrefsSync.homeSlots(context).firstOrNull {
            it.packageName == packageName && it.shortcutId == shortcutId
        }
        val instanceId = if (existing != null) {
            "inst_${System.currentTimeMillis()}_${(0..9999).random()}"
        } else ""

        val uniqueKey = when {
            instanceId.isEmpty() -> "$packageName|$shortcutId"
            else -> "$packageName|$shortcutId|$instanceId"
        }

        PrefsSync.setShortcutIntent(context, uniqueKey, finalSerialized)

        ShortcutPlacement.placeOnFirstFreeCell(context, packageName, shortcutId, instanceId)
        context.sendBroadcast(Intent("com.simplylauncher.RELOAD"))
    }

    companion object {
        private const val ACTION_INSTALL_SHORTCUT = "com.android.launcher.action.INSTALL_SHORTCUT"
    }
}
