package com.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.first

object AppRepository {

    @Volatile
    private var cachedApps: List<AppInfo>? = null

    @Volatile
    private var cacheTimestamp: Long = 0L

    private const val CACHE_TTL_MS = 30_000L

    fun invalidateCache() {
        cachedApps = null
        cacheTimestamp = 0L
    }

    fun loadInstalledApps(context: Context): List<AppInfo> {
        val cached = cachedApps
        if (cached != null && System.currentTimeMillis() - cacheTimestamp < CACHE_TTL_MS) {
            return cached
        }

        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, 0)

        val result = resolved
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

        cachedApps = result
        cacheTimestamp = System.currentTimeMillis()
        return result
    }

    suspend fun loadInstalledAppsWithCustomLabels(context: Context): List<AppInfo> {
        val base = loadInstalledApps(context)
        val customLabels = Prefs.customLabelsFlow(context).first()
        if (customLabels.isEmpty()) return base

        return base.map { app ->
            val custom = customLabels[app.packageName]
            if (custom == null) app else app.copy(label = custom)
        }.sortedBy { it.label.lowercase() }
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
