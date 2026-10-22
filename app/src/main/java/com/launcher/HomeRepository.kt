package com.simplylauncher

import android.content.Context

object HomeRepository {

    fun addToHome(context: Context, packageName: String, current: List<String>) {
        if (current.contains(packageName)) return
        val newList = current + packageName
        kotlinx.coroutines.GlobalScope.launch {
            Prefs.setHomeItems(context, newList)
        }
    }

    fun removeFromHome(context: Context, packageName: String, current: List<String>) {
        val newList = current - packageName
        kotlinx.coroutines.GlobalScope.launch {
            Prefs.setHomeItems(context, newList)
        }
    }

    fun cleanRemovedApps(context: Context, current: List<String>) {
        val valid = current.filter { AppRepository.isInstalled(context, it) }
        if (valid != current) {
            kotlinx.coroutines.GlobalScope.launch {
                Prefs.setHomeItems(context, valid)
            }
        }
    }
}
