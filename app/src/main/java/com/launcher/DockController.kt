package com.simplylauncher

import android.appwidget.AppWidgetProviderInfo
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import kotlin.math.hypot

class DragController(
    private val activity: HomeActivity,
    private val homeRecyclerView: View,
    private val drawerOverlay: View,
    private val onDragStart: (source: String, pkg: String, widgetId: Int, x: Float, y: Float) -> Unit,
    private val onDragMove: (x: Float, y: Float) -> Unit,
    private val onDropOnHome: (source: String, pkg: String, widgetId: Int, x: Float, y: Float) -> Boolean,
    private val onDropOnDrawer: (pkg: String) -> Unit,
    private val onDropOnDock: (source: String, pkg: String, x: Float, y: Float) -> Unit,
    private val onDragEnd: () -> Unit
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
    private var dragSource: String? = null
    private var dragPkg: String? = null
    private var dragWidgetId: Int = -1

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

    fun onTouch(event: MotionEvent): Boolean {
        if (isDragging) {
            when (event.actionMasked) {
                MotionEvent.ACTION_MOVE -> {
                    onDragMove(event.rawX, event.rawY)
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val source = dragSource ?: ""
                    val pkg = dragPkg ?: ""
                    val widgetId = dragWidgetId
                    val x = event.rawX
                    val y = event.rawY

                    var dropped = false
                    if (source == SOURCE_WIDGET) {
                        dropped = onDropOnHome(source, pkg, widgetId, x, y)
                    } else {
                        if (activity.isPointInDock(x, y)) {
                            onDropOnDock(source, pkg, x, y)
                            dropped = true
                        } else if (activity.isPointInDrawer(x, y)) {
                            onDropOnDrawer(pkg)
                            dropped = true
                        } else {
                            dropped = onDropOnHome(source, pkg, widgetId, x, y)
                        }
                    }

                    isDragging = false
                    dragSource = null
                    dragPkg = null
                    dragWidgetId = -1
                    onDragEnd()
                    return true
                }
            }
            return true
        }

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
                val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                if (dist > thresholdPx) {
                    activity.dismissDragPopup()

                    val widget = pendingWidget
                    val widgetInfo = pendingWidgetInfo
                    val app = pendingApp

                    isDragging = true
                    dragSource = source

                    if (widget != null && widgetInfo != null) {
                        dragWidgetId = widget.appWidgetId
                        activity.dismissWidgetPopup()
                        onDragStart(source, "", widget.appWidgetId, event.rawX, event.rawY)
                    } else if (app != null) {
                        dragPkg = app.packageName
                        if (source == SOURCE_DRAWER) {
                            activity.onDragFromDrawerStarted()
                        }
                        onDragStart(source, app.packageName, -1, event.rawX, event.rawY)
                    }

                    clearPending()
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
