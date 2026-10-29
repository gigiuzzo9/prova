package com.simplylauncher

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
            val byPkg = HashMap<String, AppInfo>()
            dockItems.filter { it.isNotBlank() }.forEach { pkg ->
                AppCache.get(pkg)?.let { byPkg[pkg] = it }
            }

            // Se la cache è vuota o manca qualcuno, ricarica
            if (byPkg.size < dockItems.count { it.isNotBlank() }) {
                val allApps = AppRepository.loadInstalledAppsWithCustomLabels(activity)
                AppCache.putAll(allApps)
                allApps.forEach { byPkg[it.packageName] = it }
            }

            val density = activity.resources.displayMetrics.density
            val size = (62 * density).toInt()
            val customIcons = PrefsSync.customIcons(activity)

            slots.forEachIndexed { index, slot ->
                slot.removeAllViews()
                val pkg = dockItems.getOrNull(index)
                val app = if (pkg.isNullOrBlank()) null else byPkg[pkg]

                if (app != null) {
                    val bitmap = AppCache.getIconBitmap(app.packageName, size)
                    val imageView = ImageView(activity)
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap)
                    }

                    // Padding per le icone personalizzate
                    if (customIcons.containsKey(app.packageName)) {
                        val pad = (size * 0.05f).toInt() // 7.5% per lato = 15% totale
                        imageView.setPadding(pad, pad, pad, pad)
                    }

                    imageView.layoutParams = FrameLayout.LayoutParams(size, size).apply {
                        gravity = Gravity.CENTER
                    }
                    slot.addView(imageView)
                    slot.tag = app.packageName
                    slot.setOnClickListener {
                        AppRepository.launchApp(activity, app.packageName)
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
            val slotPkg = slot.tag as? String
            iv.alpha = if (slotPkg != null && slotPkg == pkg) 0f else 1f
        }
    }

    suspend fun swapDockSlots(pkg: String, slotIndex: Int) {
        val dockItems = PrefsSync.dockItems(activity).toMutableList()
        while (dockItems.size < 4) dockItems.add("")

        val fromIndex = dockItems.indexOf(pkg)
        if (fromIndex != -1 && fromIndex != slotIndex) {
            val a = dockItems[fromIndex]
            val b = dockItems[slotIndex]
            dockItems[fromIndex] = b
            dockItems[slotIndex] = a
            PrefsSync.setDockItems(activity, dockItems)
            refresh()
        }
    }

    suspend fun addToDockAtPublic(pkg: String, slotIndex: Int) {
        addToDockAt(pkg, slotIndex)
    }

    private suspend fun addToDockAt(pkg: String, slotIndex: Int) {
        val dockItems = PrefsSync.dockItems(activity).toMutableList()
        while (dockItems.size < 4) dockItems.add("")

        dockItems.replaceAll { if (it == pkg) "" else it }

        val homeSlots = PrefsSync.homeSlots(activity)
        val filtered = homeSlots.filter { it.packageName != pkg }
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
        refresh()
    }
}
