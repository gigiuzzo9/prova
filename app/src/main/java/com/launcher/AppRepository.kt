package com.simplylauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.first

object AppRepository {

    fun loadInstalledApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, 0)

        return resolved
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .map { appInfo ->
                AppInfo(
                    label = pm.getApplicationLabel(appInfo).toString(),
                    packageName = appInfo.packageName,
                    icon = pm.getApplicationIcon(appInfo)
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    suspend fun loadInstalledAppsWithCustomLabels(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val customLabels = Prefs.customLabelsFlow(context).first()
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, 0)

        return resolved
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .map { appInfo ->
                AppInfo(
                    label = customLabels[appInfo.packageName]
                        ?: pm.getApplicationLabel(appInfo).toString(),
                    packageName = appInfo.packageName,
                    icon = pm.getApplicationIcon(appInfo)
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    fun launchApp(context: Context, packageName: String) {
        if (packageName == context.packageName) {
            val intent = Intent(context, SettingsActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        }

        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        intent?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            context.startActivity(it)
        }
    }

    fun isInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getApplicationInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
