package com.simplylauncher

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.view.MotionEvent

class LauncherAppWidgetHostView(context: Context) : AppWidgetHostView(context) {
    var ignoreTouches = false
    var onIgnoreIntercepted: (() -> Unit)? = null

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (ignoreTouches) {
            onIgnoreIntercepted?.invoke()
            return true
        }
        return super.onTouchEvent(event)
    }
}
