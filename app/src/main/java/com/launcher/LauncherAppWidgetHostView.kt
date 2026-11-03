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
    private val moveGestureThreshold = (24 * resources.displayMetrics.density).toInt() / 4
    private var isLongPressTriggered = false

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
                isLongPressTriggered = false
                actionDownCoords.x = event.rawX
                actionDownCoords.y = event.rawY
                
                longPressHandler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(event.rawX - actionDownCoords.x)
                val dy = abs(event.rawY - actionDownCoords.y)
                
                if (dx > moveGestureThreshold || dy > moveGestureThreshold) {
                    longPressHandler.removeCallbacks(longPressRunnable)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                longPressHandler.removeCallbacks(longPressRunnable)
                if (isLongPressTriggered) {
                    // Se è stato un long-press, consuma l'evento (non farlo arrivare al widget)
                    isLongPressTriggered = false
                    return true 
                }
                // Altrimenti lascia passare l'evento al widget (aprirà l'app)
            }
        }

        // Passa l'evento al widget
        return super.dispatchTouchEvent(event)
    }

    private val longPressRunnable = Runnable {
        isLongPressTriggered = true
        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        onLongPress?.invoke()
    }

    fun resetTouches() {
        longPressHandler.removeCallbacksAndMessages(null)
        isLongPressTriggered = false
    }
}
