package com.simplylauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PackageChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REMOVED,
            Intent.ACTION_PACKAGE_CHANGED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                GlobalScope.launch {
                    // pulizia: rimuovi slot di app disinstallate
                    val slots = Prefs.homeSlotsFlow(context).first()
                    val validSlots = slots.filter { AppRepository.isInstalled(context, it.packageName) }
                    if (validSlots.size != slots.size) {
                        Prefs.setHomeSlots(context, validSlots)
                    }

                    // pulizia dock
                    val dockItems = Prefs.dockItemsFlow(context).first()
                    val validDock = dockItems.filter { AppRepository.isInstalled(context, it) }
                    if (validDock.size != dockItems.size) {
                        Prefs.setDockItems(context, validDock)
                    }

                    context.sendBroadcast(Intent("com.simplylauncher.RELOAD"))
                }
            }
        }
    }
}
