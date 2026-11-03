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
                // Controlla ENTRAMBI gli assi (x e y) per annullare il long-press se si muove
                if (abs(actionDownCoords.x - currentCoords.x) > moveGestureThreshold || 
                    abs(actionDownCoords.y - currentCoords.y) > moveGestureThreshold) {
                    resetTouches()
                    return true // Intercetta il tocco, il widget non lo riceve
                }
            }
            
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // Il dito è stato sollevato PRIMA del timeout -> NON è un long-press
                resetTouches()
                hasLongPressed = false
                return false // Lascia passare l'evento al widget (click normale)
            }
        }
        return false
    }

    private val longPressRunnable = Runnable {
        // Sicurezza extra: controlla che il dito sia ancora fermo
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
