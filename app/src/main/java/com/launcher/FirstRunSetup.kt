package com.simplylauncher

import android.content.Context
import android.content.Intent
import android.provider.MediaStore

object FirstRunSetup {

    fun isFirstRun(context: Context): Boolean {
        val prefs = context.getSharedPreferences("launcher_flags", Context.MODE_PRIVATE)
        return !prefs.getBoolean("first_run_done", false)
    }

    fun markDone(context: Context) {
        val prefs = context.getSharedPreferences("launcher_flags", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("first_run_done", true).apply()
    }

    suspend fun populateHome(context: Context) {
        // HOME: 5 app standard
        val homeDefaults = listOfNotNull(
            findPackageForIntent(context, Intent(Intent.ACTION_DIAL)),                          // Telefono
            findPackageForIntent(context, Intent(Intent.ACTION_VIEW).setType("vnd.android-dir/mms-sms")), // Messaggi
            findPackageForIntent(context, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)), // Fotocamera
            findPackageForIntent(context, Intent(Intent.ACTION_VIEW).setData(android.net.Uri.parse("https://www.google.com"))), // Browser
            findPackageForIntent(context, Intent(android.provider.Settings.ACTION_SETTINGS))    // Impostazioni
        )

        // DOCK: 4 app standard
        val dockDefaults = listOfNotNull(
            findPackageForIntent(context, Intent(Intent.ACTION_DIAL)),                          // Telefono
            findPackageForIntent(context, Intent(Intent.ACTION_VIEW).setType("vnd.android-dir/mms-sms")), // Messaggi
            findPackageForIntent(context, Intent(Intent.ACTION_VIEW).setData(android.net.Uri.parse("market://search?q=apps"))), // Store
            findPackageForIntent(context, Intent(Intent.ACTION_VIEW).setDataAndType(android.net.Uri.parse("content://"), "resource/folder")) // File manager
        )

        Prefs.setHomeItems(context, homeDefaults.distinct())
        Prefs.setDockItems(context, dockDefaults.distinct())
    }

    /**
     * Trova il package name dell'app che risponde a un Intent specifico.
     * Funziona con qualsiasi telefono, senza hardcodare package name.
     */
    private fun findPackageForIntent(context: Context, intent: Intent): String? {
        val pm = context.packageManager
        // Trova tutte le activity che rispondono all'intent
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        // Prende la prima (che di solito è quella predefinita)
        return resolveInfos.firstOrNull()?.activityInfo?.packageName
    }
}
