package com.simplylauncher

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.graphics.PointF
import android.os.Handler
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs

class LauncherAppWidgetHostView(context: Context) : AppWidgetHostView(context) {
    private val longPressHandler = Handler()
    private val actionDownCoords = PointF()
    private val currentCoords = PointF()
    private val moveGestureThreshold =
        (24 * resources.displayMetrics.density).toInt() / 4

    var hasLongPressed = false
    var ignoreTouches = false
    var onLongPress: (() -> Unit)? = null
    var onIgnoreIntercepted: (() -> Unit)? = null

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return if (ignoreTouches) {
            onIgnoreIntercepted?.invoke()
            true
        } else {
            super.onTouchEvent(event)
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent?): Boolean {
        if (ignoreTouches || event == null) return true
        if (hasLongPressed) {
            hasLongPressed = false
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                longPressHandler.postDelayed(
                    longPressRunnable,
                    ViewConfiguration.getLongPressTimeout().toLong()
                )
                actionDownCoords.x = event.rawX
                actionDownCoords.y = event.rawY
                currentCoords.x = event.rawX
                currentCoords.y = event.rawY
            }
            MotionEvent.ACTION_MOVE -> {
                currentCoords.x = event.rawX
                currentCoords.y = event.rawY
                if (abs(actionDownCoords.x - currentCoords.x) > moveGestureThreshold) {
                    resetTouches()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                resetTouches()
            }
        }
        return false
    }

    private val longPressRunnable = Runnable {
        if (abs(actionDownCoords.x - currentCoords.x) < moveGestureThreshold &&
            abs(actionDownCoords.y - currentCoords.y) < moveGestureThreshold
        ) {
            longPressHandler.removeCallbacksAndMessages(null)
            hasLongPressed = true
            onLongPress?.invoke()
        }
    }

    fun resetTouches() {
        longPressHandler.removeCallbacksAndMessages(null)
    }
}
