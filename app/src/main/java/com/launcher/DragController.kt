package com.simplylauncher

import android.appwidget.AppWidgetProviderInfo
import android.content.ClipData
import android.content.ClipDescription
import android.view.DragEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Toast

class DragController(
    private val activity: HomeActivity,
    private val homeRecyclerView: View,
    private val drawerOverlay: View,
    private val onDropOnHome: (pkg: String, x: Float, y: Float) -> Boolean,
    private val onDropOnDrawer: (pkg: String) -> Unit
) {

    companion object {
        private const val DRAG_THRESHOLD_DP = 24f

        const val SOURCE_DRAWER = "DRAWER"
        const val SOURCE_HOME = "HOME"
        const val SOURCE_DOCK = "DOCK"
        const val SOURCE_WIDGET = "WIDGET"
    }

    private val thresholdPx = DRAG_THRESHOLD_DP * activity.resources.displayMetrics.density

    private var pendingApp: AppInfo? = null
    private var pendingWidget: WidgetSlot? = null
    private var pendingWidgetInfo: AppWidgetProviderInfo? = null
    private var pendingAnchor: View? = null
    private var pendingSource: String? = null
    private var pendingFromDrawer = false
    private var pendingStartRawX = -1f
    private var pendingStartRawY = -1f

    private var isDragging = false

    fun isActive(): Boolean = isDragging || pendingAnchor != null

    fun resetDrag() {
        isDragging = false
        clearPending()
    }

    fun onLongPress(app: AppInfo, anchor: View, source: String, fromDrawer: Boolean) {
        if (isDragging) return
        pendingApp = app
        pendingWidget = null
        pendingWidgetInfo = null
        pendingAnchor = anchor
        pendingSource = source
        pendingFromDrawer = fromDrawer
        pendingStartRawX = -1f
        pendingStartRawY = -1f

        activity.showContextMenuForDrag(app, anchor, fromDrawer)
    }

    fun onWidgetLongPress(slot: WidgetSlot, info: AppWidgetProviderInfo, anchor: View) {
        if (isDragging) return
        pendingApp = null
        pendingWidget = slot
        pendingWidgetInfo = info
        pendingAnchor = anchor
        pendingSource = SOURCE_WIDGET
        pendingFromDrawer = false
        pendingStartRawX = -1f
        pendingStartRawY = -1f
    }

    fun cancelPendingDrag() {
        clearPending()
    }

    fun onInterceptTouch(event: MotionEvent): Boolean {
        val anchor = pendingAnchor ?: return false
        val source = pendingSource ?: return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (pendingStartRawX < 0f) {
                    pendingStartRawX = event.rawX
                    pendingStartRawY = event.rawY
                }
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (pendingStartRawX < 0f) {
                    pendingStartRawX = event.rawX
                    pendingStartRawY = event.rawY
                    return false
                }
                val dx = event.rawX - pendingStartRawX
                val dy = event.rawY - pendingStartRawY
                val dist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                if (dist > thresholdPx) {
                    activity.dismissDragPopup()
                    val widget = pendingWidget
                    val widgetInfo = pendingWidgetInfo
                    val app = pendingApp
                    if (widget != null && widgetInfo != null) {
                        activity.dismissWidgetPopup()
                        startWidgetDrag(widget, widgetInfo, anchor)
                    } else if (app != null) {
                        startDrag(app, anchor, source)
                    }
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                clearPending()
                return false
            }
        }
        return false
    }

    private fun startDrag(app: AppInfo, anchor: View, source: String) {
        val clip = ClipData.newPlainText(source, app.packageName)
        val shadow = View.DragShadowBuilder(anchor)
        val started = anchor.startDragAndDrop(clip, shadow, null, 0)

        if (!started) {
            Toast.makeText(activity, "Drag non supportato", Toast.LENGTH_SHORT).show()
            clearPending()
            return
        }

        isDragging = true

        if (source == SOURCE_DRAWER) {
            activity.onDragFromDrawerStarted()
        }

        clearPending()
    }

    private fun startWidgetDrag(slot: WidgetSlot, info: AppWidgetProviderInfo, anchor: View) {
        val clip = ClipData.newPlainText(SOURCE_WIDGET, slot.appWidgetId.toString())
        val shadow = View.DragShadowBuilder(anchor)
        val started = anchor.startDragAndDrop(clip, shadow, null, 0)

        if (!started) {
            Toast.makeText(activity, "Drag non supportato", Toast.LENGTH_SHORT).show()
            clearPending()
            return
        }

        isDragging = true
        clearPending()
    }

    fun onDragEnded() {
        isDragging = false
        if (pendingFromDrawer) {
            activity.onDragFromDrawerEnded()
        }
        pendingFromDrawer = false
    }

    fun handleHomeDragEvent(event: DragEvent): Boolean {
        val pagerLoc = IntArray(2)
        homeRecyclerView.getLocationOnScreen(pagerLoc)
        val screenX = event.x + pagerLoc[0]
        val screenY = event.y + pagerLoc[1]

        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED ->
                return event.clipDescription.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)

            DragEvent.ACTION_DRAG_LOCATION -> {
                if (activity.isPointInDock(screenX, screenY)) {
                    return false
                }
                return true
            }

            DragEvent.ACTION_DROP -> {
                if (activity.isPointInDock(screenX, screenY)) {
                    return false
                }
                val pkg = event.clipData.getItemAt(0).text.toString()
                return onDropOnHome(pkg, event.x, event.y)
            }

            DragEvent.ACTION_DRAG_ENDED -> {
                onDragEnded()
                return true
            }
        }
        return false
    }

    fun handleDrawerDragEvent(event: DragEvent): Boolean {
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED ->
                return event.clipDescription.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)

            DragEvent.ACTION_DROP -> {
                val pkg = event.clipData.getItemAt(0).text.toString()
                val source = event.clipData.description.label?.toString() ?: ""
                if (source == SOURCE_HOME || source == SOURCE_DOCK) {
                    onDropOnDrawer(pkg)
                }
                return true
            }

            DragEvent.ACTION_DRAG_ENDED -> {
                onDragEnded()
                return true
            }
        }
        return false
    }

    fun handleWidgetDragEvent(event: DragEvent, pageIndex: Int, cellW: Int, cellH: Int, paddingTop: Int): Boolean {
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> {
                val source = event.clipDescription.label?.toString() ?: ""
                if (source != SOURCE_WIDGET) {
                    return false
                }
                return event.clipDescription.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)
            }

            DragEvent.ACTION_DROP -> {
                val source = event.clipDescription.label?.toString() ?: ""
                if (source == SOURCE_WIDGET) {
                    val widgetId = event.clipData.getItemAt(0).text.toString().toIntOrNull()
                    if (widgetId != null) {
                        val col = (event.x / cellW).toInt().coerceIn(0, activity.getCurrentCols() - 1)
                        val row = ((event.y - paddingTop) / cellH).toInt().coerceIn(0, activity.getCurrentRows() - 1)
                        activity.moveWidget(widgetId, row, col, pageIndex)
                    }
                    return true
                }
                return false
            }

            DragEvent.ACTION_DRAG_ENDED -> {
                return false
            }
        }
        return false
    }

    private fun clearPending() {
        pendingApp = null
        pendingWidget = null
        pendingWidgetInfo = null
        pendingAnchor = null
        pendingSource = null
        pendingFromDrawer = false
        pendingStartRawX = -1f
        pendingStartRawY = -1f
    }
}
