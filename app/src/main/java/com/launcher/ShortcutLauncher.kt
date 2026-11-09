package com.simplylauncher

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Process
import android.widget.Toast

object ShortcutLauncher {

    private fun tryStart(context: Context, intent: Intent): Boolean {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun launch(
        context: Context,
        packageName: String,
        shortcutId: String,
        instanceId: String = "",
        showErrorToast: Boolean = true
    ) {
        if (shortcutId.isBlank()) {
            AppRepository.launchApp(context, packageName)
            return
        }

        val uniqueKey = when {
            instanceId.isEmpty() -> "$packageName|$shortcutId"
            else -> "$packageName|$shortcutId|$instanceId"
        }

        val savedData = PrefsSync.shortcutIntents(context)[uniqueKey]

        if (savedData != null && savedData.startsWith("url:")) {
            val url = savedData.removePrefix("url:")
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            if (tryStart(context, browserIntent)) return

            val forcedIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                setPackage(packageName)
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            if (tryStart(context, forcedIntent)) return
        }

        if (!savedData.isNullOrBlank() && !savedData.startsWith("url:")) {
            try {
                val pi = Intent.parseUri(savedData, Intent.URI_INTENT_SCHEME)
                if (tryStart(context, pi)) return
            } catch (_: Exception) {}
        }

        try {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            launcherApps.startShortcut(packageName, shortcutId, null, null, Process.myUserHandle())
            return
        } catch (_: Exception) {}

        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null && tryStart(context, launchIntent)) return
        } catch (_: Exception) {}

        if (showErrorToast) {
            Toast.makeText(context.applicationContext, "Impossibile avviare la scorciatoia", Toast.LENGTH_SHORT).show()
        }
    }
}
