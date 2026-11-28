package com.simplylauncher

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object AppRepository {

    @Volatile
    private var cacheTimestamp: Long = 0L

    private const val CACHE_TTL_MS = 5 * 60_000L
    private const val DISK_CACHE_FILE = "apps_cache.json"
    private const val ICON_SIZE_PX = 96 * 3  // 96dp approx a xxhdpi

    fun invalidateCache(context: Context) {
        AppCache.clear()
        cacheTimestamp = 0L
        try { File(context.filesDir, DISK_CACHE_FILE).delete() } catch (_: Exception) {}
    }

    /**
     * Invalida SOLO un package: rimuove il suo AppInfo dalla cache in memoria.
     * La prossima loadInstalledAppsWithCustomLabels rasterizzerà solo quel pkg,
     * riusando gli AppInfo già validi per gli altri.
     */
    fun invalidatePackage(context: Context, pkg: String) {
        AppCache.remove(pkg)
        cacheTimestamp = 0L
    }

    suspend fun loadInstalledAppsWithCustomLabels(context: Context): List<AppInfo> =
        withContext(Dispatchers.IO) {
            // Cache in memoria valida → zero query PMS
            if (AppCache.size() > 0 && System.currentTimeMillis() - cacheTimestamp < CACHE_TTL_MS) {
                return@withContext AppCache.snapshot()
            }

            // 1) Prova a scaldare AppCache da disco (cold start veloce)
            if (AppCache.size() == 0) {
                val diskCache = readDiskCache(context)
                if (diskCache.isNotEmpty()) {
                    AppCache.warmFromDisk(context, diskCache)
                }
            }

            // 2) Scansione reale del PackageManager
            val pm = context.packageManager
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
            val userHandle = Process.myUserHandle()

            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolved = pm.queryIntentActivities(intent, 0)
            val customLabels = PrefsSync.customLabels(context)
            val customIcons = PrefsSync.customIcons(context)

            val result = resolved
                .map { it.activityInfo.applicationInfo }
                .distinctBy { it.packageName }
                .map { appInfo ->
                    val pkg = appInfo.packageName
                    val label = customLabels[pkg] ?: pm.getApplicationLabel(appInfo).toString()

                    // Riusa l'AppInfo esistente se label invariata e nessuna custom icon
                    // (né prima né ora). In tutti gli altri casi rasterizziamo.
                    val cached = AppCache.get(pkg)
                    val customIconPath: String? = customIcons[pkg]
                    if (cached != null &&
                        cached.label == label &&
                        customIconPath == null
                    ) {
                        return@map cached
                    }

                    // Icona custom ha priorità
                    var icon: Drawable? = null
                    if (customIconPath != null && customIconPath.isNotEmpty()) {
                        val file = File(customIconPath)
                        if (file.exists()) {
                            icon = Drawable.createFromPath(customIconPath)
                        }
                    }

                    if (icon == null) {
                        val launcherIcon = try {
                            launcherApps?.getActivityList(pkg, userHandle)?.firstOrNull()?.getBadgedIcon(0)
                        } catch (_: Exception) {
                            null
                        }
                        val raw = launcherIcon ?: pm.getApplicationIcon(appInfo)

                        val bmp = try {
                            val b = Bitmap.createBitmap(ICON_SIZE_PX, ICON_SIZE_PX, Bitmap.Config.ARGB_8888)
                            val c = Canvas(b)
                            if (raw is AdaptiveIconDrawable) {
                                val inset = (ICON_SIZE_PX * 0.1f).toInt()
                                raw.setBounds(inset, inset, ICON_SIZE_PX - inset, ICON_SIZE_PX - inset)
                            } else {
                                raw.setBounds(0, 0, ICON_SIZE_PX, ICON_SIZE_PX)
                            }
                            raw.draw(c)
                            b
                        } catch (_: Exception) {
                            null
                        }

                        icon = if (bmp != null) BitmapDrawable(context.resources, bmp) else raw
                    }

                    AppInfo(label = label, packageName = pkg, icon = icon!!)
                }
                .sortedBy { it.label.lowercase() }

            AppCache.putAll(result)
            cacheTimestamp = System.currentTimeMillis()

            // 3) Salva su disco in background (non blocca il chiamante)
            Thread {
                try {
                    AppCache.persistIconsToDisk(context, ICON_SIZE_PX)
                    writeDiskCache(context, result)
                } catch (_: Exception) {}
            }.start()

            result
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

    // ---------- Disco: file JSON con pkg -> label (per warm start) ----------

    private fun readDiskCache(context: Context): Map<String, String> {
        val file = File(context.filesDir, DISK_CACHE_FILE)
        if (!file.exists()) return emptyMap()
        return try {
            val text = file.readText()
            val arr = JSONArray(text)
            val result = mutableMapOf<String, String>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val pkg = o.optString("pkg", "")
                val label = o.optString("label", "")
                if (pkg.isNotEmpty() && label.isNotEmpty()) result[pkg] = label
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun writeDiskCache(context: Context, apps: List<AppInfo>) {
        try {
            val arr = JSONArray()
            for (app in apps) {
                val o = JSONObject()
                o.put("pkg", app.packageName)
                o.put("label", app.label)
                arr.put(o)
            }
            File(context.filesDir, DISK_CACHE_FILE).writeText(arr.toString())
        } catch (_: Exception) {}
    }
}
