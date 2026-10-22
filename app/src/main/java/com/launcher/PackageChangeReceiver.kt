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
                    val current = Prefs.homeItemsFlow(context).first()
                    HomeRepository.cleanRemovedApps(context, current)
                    context.sendBroadcast(Intent("com.simplylauncher.RELOAD"))
                }
            }
        }
    }
}
