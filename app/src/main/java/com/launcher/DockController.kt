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
    private val onAppLongPressed: (AppInfo, View) -> Unit
) {

    fun setup() {
    }

    fun refresh() {
        activity.lifecycleScope.launch {
            val dockItems = PrefsSync.dockItems(activity)
            val dockShortcutIds = PrefsSync.dockShortcutIds(activity)
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
                val app = if (pkg.isNullOrBlank()) null else byPkg[pkg]
                val uniqueKey = if (shortcutId.isNotEmpty()) "$pkg|$shortcutId" else pkg

                if (app != null) {
                    val bitmap = AppCache.getIconBitmap(app.packageName, size * 3)
                    val imageView = ImageView(activity)
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap)
                    }

                    // Usa l'icona personalizzata della scorciatoia o quella dell'app
                    val hasCustom = if (shortcutId.isNotEmpty()) {
                        customShortcutIcons.containsKey(uniqueKey)
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
                    slot.setOnClickListener {
                        if (shortcutId.isNotEmpty()) {
                            try {
                                val launcherApps = activity.getSystemService(android.content.Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                                launcherApps.startShortcut(app.packageName, shortcutId, null, null, android.os.Process.myUserHandle())
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(activity, "Impossibile avviare la scorciatoia", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            AppRepository.launchApp(activity, app.packageName)
                        }
                    }
                    slot.setOnLongClickListener { view ->
                        onAppLongPressed(app, view)
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

    fun setDraggingPackage(pkg: String?) {
        slots.forEach { slot ->
            val iv = slot.getChildAt(0) as? ImageView ?: return@forEach
            val slotKey = slot.tag as? String
            iv.alpha = if (slotKey != null && (slotKey == pkg || slotKey.startsWith("$pkg|"))) 0f else 1f
        }
    }

    suspend fun swapDockSlots(pkg: String, slotIndex: Int) {
        val dockItems = PrefsSync.dockItems(activity).toMutableList()
        while (dockItems.size < 4) dockItems.add("")
        val dockShortcutIds = PrefsSync.dockShortcutIds(activity).toMutableList()
        while (dockShortcutIds.size < 4) dockShortcutIds.add("")

        // Trova lo slot di partenza: prima per packageName, poi per shortcutId
        var fromIndex = dockItems.indexOf(pkg)
        if (fromIndex == -1) {
            fromIndex = dockShortcutIds.indexOfFirst { it.isNotEmpty() }
        }

        if (fromIndex != -1 && fromIndex != slotIndex) {
            val a = dockItems[fromIndex]
            val b = dockItems[slotIndex]
            dockItems[fromIndex] = b
            dockItems[slotIndex] = a
            PrefsSync.setDockItems(activity, dockItems)

            val aId = dockShortcutIds[fromIndex]
            val bId = dockShortcutIds[slotIndex]
            dockShortcutIds[fromIndex] = bId
            dockShortcutIds[slotIndex] = aId
            PrefsSync.setDockShortcutIds(activity, dockShortcutIds)

            refresh()
        }
    }

    suspend fun addToDockAtPublic(pkg: String, slotIndex: Int, shortcutId: String = "") {
        addToDockAt(pkg, slotIndex, shortcutId)
    }

    private suspend fun addToDockAt(pkg: String, slotIndex: Int, shortcutId: String) {
        val dockItems = PrefsSync.dockItems(activity).toMutableList()
        while (dockItems.size < 4) dockItems.add("")

        dockItems.replaceAll { if (it == pkg) "" else it }

        val homeSlots = PrefsSync.homeSlots(activity)
        val originalSlot = homeSlots.firstOrNull {
            if (shortcutId.isNotEmpty()) it.packageName == pkg && it.shortcutId == shortcutId
            else it.packageName == pkg
        }
        val actualShortcutId = shortcutId.ifEmpty { originalSlot?.shortcutId ?: "" }

        val filtered = homeSlots.filter {
            if (actualShortcutId.isNotEmpty()) !(it.packageName == pkg && it.shortcutId == actualShortcutId)
            else it.packageName != pkg
        }
        if (filtered.size != homeSlots.size) {
            PrefsSync.setHomeSlots(activity, filtered)
            activity.removeAppFromGridOnly(pkg)
        }

        val previousOccupant = dockItems[slotIndex]
        dockItems[slotIndex] = pkg

        if (previousOccupant.isNotBlank()) {
            val freeIndex = dockItems.indexOfFirst { it.isBlank() }
            if (freeIndex != -1) {
                dockItems[freeIndex] = previousOccupant
            }
        }

        PrefsSync.setDockItems(activity, dockItems)

        val dockShortcutIds = PrefsSync.dockShortcutIds(activity).toMutableList()
        while (dockShortcutIds.size < 4) dockShortcutIds.add("")
        dockShortcutIds[slotIndex] = actualShortcutId
        PrefsSync.setDockShortcutIds(activity, dockShortcutIds)

        refresh()
    }
}
