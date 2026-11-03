package com.simplylauncher

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs

class LauncherAppWidgetHostView(context: Context) : AppWidgetHostView(context) {
    
    private val longPressHandler = Handler(Looper.getMainLooper())
    private val actionDownCoords = PointF()
    private val currentCoords = PointF()
    private val moveGestureThreshold = (24 * resources.displayMetrics.density).toInt() / 4

    var hasLongPressed = false
    var ignoreTouches = false
    var onLongPress: (() -> Unit)? = null
    var onIgnoreIntercepted: (() -> Unit)? = null

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (ignoreTouches) {
            onIgnoreIntercepted?.invoke()
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
                hasLongPressed = false
            }
            MotionEvent.ACTION_MOVE -> {
                currentCoords.x = event.rawX
                currentCoords.y = event.rawY
                // Se si muove, annulla il long press
                if (abs(actionDownCoords.x - currentCoords.x) > moveGestureThreshold || 
                    abs(actionDownCoords.y - currentCoords.y) > moveGestureThreshold) {
                    resetTouches()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                resetTouches()
            }
        }

        return super.dispatchTouchEvent(event)
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
