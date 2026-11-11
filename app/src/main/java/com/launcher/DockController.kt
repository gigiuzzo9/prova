package com.simplylauncher

import android.content.pm.LauncherApps
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class DockController(
    private val activity: HomeActivity,
    private val slots: List<FrameLayout>,
    private val onAppLongPressed: (AppInfo, View, String, String) -> Unit
) {

    fun setup() {
    }

    fun refresh() {
        activity.lifecycleScope.launch {
            val dockItems = PrefsSync.dockItems(activity)
            val dockShortcutIds = PrefsSync.dockShortcutIds(activity)
            val dockInstanceIds = PrefsSync.dockInstanceIds(activity)

            val byPkg = HashMap<String, AppInfo>()
            dockItems.filter { it.isNotBlank() }.forEach { pkg ->
                AppCache.get(pkg)?.let { byPkg[pkg] = it }
            }

            if (byPkg.size < dockItems.count { it.isNotBlank() }) {
                val allApps = AppRepository.loadInstalledAppsWithCustomLabels(activity)
                AppCache.putAll(allApps)
                allApps.forEach { byPkg[it.packageName] = it }
            }

            val density = activity.resources.displayMetrics.density
            val size = (62 * density).toInt()
            val customIcons = PrefsSync.customIcons(activity)
            val customShortcutIcons = PrefsSync.customShortcutIcons(activity)

            slots.forEachIndexed { index, slot ->
                slot.removeAllViews()
                val pkg = dockItems.getOrNull(index)
                val shortcutId = dockShortcutIds.getOrNull(index) ?: ""
                val instanceId = dockInstanceIds.getOrNull(index) ?: ""
                val app = if (pkg.isNullOrBlank()) null else byPkg[pkg]

                if (app != null) {
                    val uniqueKey = when {
                        shortcutId.isEmpty() -> app.packageName
                        instanceId.isEmpty() -> "${app.packageName}|$shortcutId"
                        else -> "${app.packageName}|$shortcutId|$instanceId"
                    }

                    // Calcola la chiave "pulita" per trovare il file dell'icona
                    val safeKey = uniqueKey.replace("|", "_").replace(":", "_").replace("/", "_")

                    val bitmap: android.graphics.Bitmap? = if (shortcutId.isNotEmpty()) {
                        // Prova prima con la chiave pulita, poi con quella originale
                        var customPath = customShortcutIcons[safeKey]
                        if (customPath == null) customPath = customShortcutIcons[uniqueKey]
                        
                        val customBmp = if (customPath != null) {
                            val f = java.io.File(customPath)
                            if (f.exists()) {
                                // Carica a size*3 e poi ridimensiona a size per non sgranare
                                val bmp = android.graphics.BitmapFactory.decodeFile(customPath)
                                if (bmp != null) android.graphics.Bitmap.createScaledBitmap(bmp, size, size, true) else null
                            } else null
                        } else null
                        customBmp
                            ?: loadShortcutIconBitmap(app.packageName, shortcutId, size * 3)
                            ?: AppCache.getIconBitmap(app.packageName, size * 3)
                    } else {
                        val customPath = customIcons[app.packageName]
                        val customBmp = if (customPath != null) {
                            val f = java.io.File(customPath)
                            if (f.exists()) {
                                // Carica a size*3 e poi ridimensiona a size per non sgranare
                                val bmp = android.graphics.BitmapFactory.decodeFile(customPath)
                                if (bmp != null) android.graphics.Bitmap.createScaledBitmap(bmp, size, size, true) else null
                            } else null
                        } else null
                        customBmp ?: AppCache.getIconBitmap(app.packageName, size * 3)
                    }

                    val imageView = ImageView(activity)
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap)
                    }

                    val hasCustom = if (shortcutId.isNotEmpty()) {
                        customShortcutIcons.containsKey(safeKey) || customShortcutIcons.containsKey(uniqueKey)
                    } else {
                        customIcons.containsKey(app.packageName)
                    }
                    if (hasCustom) {
                        val pad = (size * 0.035f).toInt()
                        imageView.setPadding(pad, pad, pad, pad)
                    }

                    imageView.layoutParams = FrameLayout.LayoutParams(size, size).apply {
                        gravity = Gravity.CENTER
                    }
                    slot.addView(imageView)
                    slot.tag = uniqueKey

                    // --- CLICK: usa ShortcutLauncher come la griglia ---
                    slot.setOnClickListener {
                        if (shortcutId.isNotEmpty()) {
                            // Usa lo stesso metodo della griglia
                            ShortcutLauncher.launch(
                                context = activity,
                                packageName = app.packageName,
                                shortcutId = shortcutId,
                                instanceId = instanceId
                            )
                        } else {
                            AppRepository.launchApp(activity, app.packageName)
                        }
                    }
                    slot.setOnLongClickListener { view ->
                        onAppLongPressed(app, view, shortcutId, instanceId)
                        true
                    }
                } else {
                    slot.tag = null
                    slot.setOnClickListener(null)
                    slot.setOnLongClickListener(null)
                }
            }
        }
    }

    fun setDraggingPackage(pkg: String?, shortcutId: String = "", instanceId: String = "") {
        val targetKey: String? = if (pkg == null) null else when {
            shortcutId.isEmpty() -> pkg
            instanceId.isEmpty() -> "$pkg|$shortcutId"
            else -> "$pkg|$shortcutId|$instanceId"
        }
        slots.forEach { slot ->
            val iv = slot.getChildAt(0) as? ImageView ?: return@forEach
            val slotKey = slot.tag as? String
            iv.alpha = if (targetKey != null && slotKey == targetKey) 0f else 1f
        }
    }

    suspend fun swapDockSlots(pkg: String, slotIndex: Int, shortcutId: String = "", instanceId: String = "") {
        val dockItems = PrefsSync.dockItems(activity).toMutableList()
        while (dockItems.size < 4) dockItems.add("")
        val dockShortcutIds = PrefsSync.dockShortcutIds(activity).toMutableList()
        while (dockShortcutIds.size < 4) dockShortcutIds.add("")
        val dockInstanceIds = PrefsSync.dockInstanceIds(activity).toMutableList()
        while (dockInstanceIds.size < 4) dockInstanceIds.add("")

        var fromIndex = -1
        for (i in dockItems.indices) {
            if (dockItems[i] == pkg &&
                (dockShortcutIds.getOrNull(i) ?: "") == shortcutId &&
                (dockInstanceIds.getOrNull(i) ?: "") == instanceId
            ) {
                fromIndex = i
                break
            }
        }

        if (fromIndex != -1 && fromIndex != slotIndex) {
            val aPkg = dockItems[fromIndex]; val bPkg = dockItems[slotIndex]
            dockItems[fromIndex] = bPkg; dockItems[slotIndex] = aPkg

            val aId = dockShortcutIds[fromIndex]; val bId = dockShortcutIds[slotIndex]
            dockShortcutIds[fromIndex] = bId; dockShortcutIds[slotIndex] = aId

            val aInst = dockInstanceIds[fromIndex]; val bInst = dockInstanceIds[slotIndex]
            dockInstanceIds[fromIndex] = bInst; dockInstanceIds[slotIndex] = aInst

            PrefsSync.setDockItems(activity, dockItems)
            PrefsSync.setDockShortcutIds(activity, dockShortcutIds)
            PrefsSync.setDockInstanceIds(activity, dockInstanceIds)

            refresh()
        }
    }

    suspend fun addToDockAtPublic(
        pkg: String,
        slotIndex: Int,
        shortcutId: String = "",
        instanceId: String = ""
    ) {
        addToDockAt(pkg, slotIndex, shortcutId, instanceId)
    }

    private suspend fun addToDockAt(pkg: String, slotIndex: Int, shortcutId: String, instanceId: String) {
        val dockItems = PrefsSync.dockItems(activity).toMutableList()
        while (dockItems.size < 4) dockItems.add("")
        val dockShortcutIds = PrefsSync.dockShortcutIds(activity).toMutableList()
        while (dockShortcutIds.size < 4) dockShortcutIds.add("")
        val dockInstanceIds = PrefsSync.dockInstanceIds(activity).toMutableList()
        while (dockInstanceIds.size < 4) dockInstanceIds.add("")

        for (i in dockItems.indices) {
            if (dockItems[i] == pkg &&
                (dockShortcutIds.getOrNull(i) ?: "") == shortcutId &&
                (dockInstanceIds.getOrNull(i) ?: "") == instanceId
            ) {
                dockItems[i] = ""
                dockShortcutIds[i] = ""
                dockInstanceIds[i] = ""
            }
        }

        val homeSlots = PrefsSync.homeSlots(activity)
        val filtered = homeSlots.filterNot { it.packageName == pkg && it.shortcutId == shortcutId && it.instanceId == instanceId }
        if (filtered.size != homeSlots.size) {
            PrefsSync.setHomeSlots(activity, filtered)
        }

        val previousPkg = dockItems[slotIndex]
        val previousId = dockShortcutIds[slotIndex]
        val previousInst = dockInstanceIds[slotIndex]

        dockItems[slotIndex] = pkg
        dockShortcutIds[slotIndex] = shortcutId
        dockInstanceIds[slotIndex] = instanceId

        if (previousPkg.isNotBlank()) {
            val freeIndex = dockItems.indexOfFirst { it.isBlank() }
            if (freeIndex != -1) {
                dockItems[freeIndex] = previousPkg
                dockShortcutIds[freeIndex] = previousId
                dockInstanceIds[freeIndex] = previousInst
            }
        }

        PrefsSync.setDockItems(activity, dockItems)
        PrefsSync.setDockShortcutIds(activity, dockShortcutIds)
        PrefsSync.setDockInstanceIds(activity, dockInstanceIds)

        refresh()
    }

    private fun loadShortcutIconBitmap(packageName: String, shortcutId: String, sizePx: Int): android.graphics.Bitmap? {
        if (sizePx <= 0 || shortcutId.isEmpty()) return null
        return try {
            val launcherApps = activity.getSystemService(android.content.Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val query = LauncherApps.ShortcutQuery().apply {
                setPackage(packageName)
                setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                )
            }
            val shortcuts = launcherApps.getShortcuts(query, android.os.Process.myUserHandle())
            val shortcut = shortcuts?.firstOrNull { it.id == shortcutId } ?: return null
            val density = activity.resources.displayMetrics.densityDpi
            val drawable = launcherApps.getShortcutIconDrawable(shortcut, density) ?: return null
            val bmp = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.draw(canvas)
            bmp
        } catch (e: Exception) {
            null
        }
    }
}
