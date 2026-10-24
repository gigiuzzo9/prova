package com.simplylauncher

import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
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
            val dockItems = Prefs.dockItemsFlow(activity).first()
            val allApps = AppRepository.loadInstalledAppsWithCustomLabels(activity)
            val byPkg = allApps.associateBy { it.packageName }

            slots.forEachIndexed { index, slot ->
                slot.removeAllViews()
                val pkg = dockItems.getOrNull(index)
                val app = if (pkg.isNullOrBlank()) null else byPkg[pkg]

                if (app != null) {
                    val imageView = ImageView(activity).apply {
                        setImageDrawable(app.icon)
                        val size = (62 * resources.displayMetrics.density).toInt()
                        layoutParams = FrameLayout.LayoutParams(size, size).apply {
                            gravity = Gravity.CENTER
                        }
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
        Log.d("GHOST_DOCK", "setDraggingPackage($pkg) slots=${slots.size}")
        slots.forEachIndexed { i, slot ->
            val iv = slot.getChildAt(0) as? ImageView ?: return@forEachIndexed
            val slotPkg = slot.tag as? String
            val hide = slotPkg != null && slotPkg == pkg
            iv.alpha = if (hide) 0f else 1f
            Log.d("GHOST_DOCK", "  slot=$i pkg=$slotPkg hide=$hide")
        }
    }

    suspend fun swapDockSlots(pkg: String, slotIndex: Int) {
        val dockItems = Prefs.dockItemsFlow(activity).first().toMutableList()
        while (dockItems.size < 4) dockItems.add("")

        val fromIndex = dockItems.indexOf(pkg)
        if (fromIndex != -1 && fromIndex != slotIndex) {
            val a = dockItems[fromIndex]
            val b = dockItems[slotIndex]
            dockItems[fromIndex] = b
            dockItems[slotIndex] = a
            Prefs.setDockItems(activity, dockItems)
            refresh()
        }
    }

    suspend fun addToDockAtPublic(pkg: String, slotIndex: Int) {
        addToDockAt(pkg, slotIndex)
    }

    private suspend fun addToDockAt(pkg: String, slotIndex: Int) {
        val dockItems = Prefs.dockItemsFlow(activity).first().toMutableList()
        while (dockItems.size < 4) dockItems.add("")

        dockItems.replaceAll { if (it == pkg) "" else it }

        val homeSlots = Prefs.homeSlotsFlow(activity).first()
        val filtered = homeSlots.filter { it.packageName != pkg }
        if (filtered.size != homeSlots.size) {
            Prefs.setHomeSlots(activity, filtered)
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

        Prefs.setDockItems(activity, dockItems)
        refresh()
    }
}
