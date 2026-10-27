package com.simplylauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PackageChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REMOVED,
            Intent.ACTION_PACKAGE_CHANGED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                // Invalida la cache PRIMA di qualsiasi query
                AppRepository.invalidateCache()
                AppCache.clear()

                // Pulizia sincrona (SharedPreferences, non DataStore)
                val slots = PrefsSync.homeSlots(context)
                val validSlots = slots.filter { AppRepository.isInstalled(context, it.packageName) }
                if (validSlots.size != slots.size) {
                    PrefsSync.setHomeSlots(context, validSlots)
                }

                val dockItems = PrefsSync.dockItems(context)
                val validDock = dockItems.filter {
                    it.isBlank() || AppRepository.isInstalled(context, it)
                }
                if (validDock.size != dockItems.size) {
                    PrefsSync.setDockItems(context, validDock)
                }

                context.sendBroadcast(Intent("com.simplylauncher.RELOAD"))
            }
        }
    }
}
