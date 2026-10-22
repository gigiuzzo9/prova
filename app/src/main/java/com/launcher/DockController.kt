package com.simplylauncher

import android.graphics.Color
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Gestisce i 4 slot della dock.
 * Ogni slot è un target di drop indipendente.
 */
class DockController(
    private val activity: HomeActivity,
    private val slots: List<FrameLayout>,
    private val onAppLongPressed: (AppInfo, View) -> Unit
) {
    private val highlightColor = Color.parseColor("#33FFFFFF")

    fun setup() {
        slots.forEachIndexed { index, slot ->
            slot.setOnDragListener { _, event -> handleSlotDrop(event, index) }
        }
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
                    slot.setOnClickListener {
                        AppRepository.launchApp(activity, app.packageName)
                    }
                    slot.setOnLongClickListener { view ->
                        onAppLongPressed(app, view)
                        true
                    }
                } else {
                    slot.setOnClickListener(null)
                    slot.setOnLongClickListener(null)
                }
            }
        }
    }

    private fun handleSlotDrop(event: DragEvent, slotIndex: Int): Boolean {
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> {
                return event.clipDescription.hasMimeType(android.content.ClipDescription.MIMETYPE_TEXT_PLAIN)
            }

            DragEvent.ACTION_DRAG_ENTERED -> {
                slots[slotIndex].setBackgroundColor(highlightColor)
                return true
            }

            DragEvent.ACTION_DRAG_EXITED -> {
                slots[slotIndex].setBackgroundColor(Color.TRANSPARENT)
                return true
            }

            DragEvent.ACTION_DROP -> {
                slots[slotIndex].setBackgroundColor(Color.TRANSPARENT)
                val pkg = event.clipData.getItemAt(0).text.toString()
                val source = event.clipData.description.label?.toString() ?: ""

                activity.lifecycleScope.launch {
                    if (source == DragController.SOURCE_DOCK) {
                        swapDockSlots(pkg, slotIndex)
                    } else {
                        addToDockAt(pkg, slotIndex)
                    }
                }
                return true
            }

            DragEvent.ACTION_DRAG_ENDED -> {
                slots[slotIndex].setBackgroundColor(Color.TRANSPARENT)
                return true
            }
        }
        return false
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

        // rimuovi TUTTE le occorrenze del pkg dalla dock
        dockItems.replaceAll { if (it == pkg) "" else it }

        // rimuovi TUTTE le occorrenze dalla home
        val homeSlots = Prefs.homeSlotsFlow(activity).first()
        val filtered = homeSlots.filter { it.packageName != pkg }
        if (filtered.size != homeSlots.size) {
            Prefs.setHomeSlots(activity, filtered)
            activity.reloadHomeGrid()
        }

        // metti pkg nella slot target
        val previousOccupant = dockItems[slotIndex]
        dockItems[slotIndex] = pkg

        // se la slot era occupata, sposta l'occupante in una slot libera
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
