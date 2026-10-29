package com.simplylauncher

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Process

object AppRepository {

    @Volatile
    private var cacheTimestamp: Long = 0L

    private const val CACHE_TTL_MS = 5 * 60_000L

    fun invalidateCache() {
        AppCache.clear()
        cacheTimestamp = 0L
    }

    fun loadInstalledAppsWithCustomLabels(context: Context): List<AppInfo> {
        // Se la cache è già popolata e "fresca", usa quella. ZERO query PMS.
        if (AppCache.size() > 0 && System.currentTimeMillis() - cacheTimestamp < CACHE_TTL_MS) {
            return AppCache.snapshot()
        }

        val pm = context.packageManager
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
        val userHandle = Process.myUserHandle()

        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, 0)
        val customLabels = PrefsSync.customLabels(context)

        val result = resolved
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .map { appInfo ->
                val pkg = appInfo.packageName
                val label = customLabels[pkg] ?: pm.getApplicationLabel(appInfo).toString()

                // Usa LauncherApps per ottenere l'icona dell'alias attivo (es. Fossify Messages)
                val launcherIcon = try {
                    launcherApps?.getActivityList(pkg, userHandle)?.firstOrNull()?.getBadgedIcon(0)
                } catch (_: Exception) {
                    null
                }

                val icon = launcherIcon ?: pm.getApplicationIcon(appInfo)

                AppInfo(label = label, packageName = pkg, icon = icon)
            }
            .sortedBy { it.label.lowercase() }

        AppCache.putAll(result)
        cacheTimestamp = System.currentTimeMillis()
        return result
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
