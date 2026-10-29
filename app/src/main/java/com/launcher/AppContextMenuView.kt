package com.simplylauncher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView

class AppContextMenuView(private val ctx: Context) {

    var onUninstall: (() -> Unit)? = null
    var onRename: (() -> Unit)? = null
    var onRemove: (() -> Unit)? = null
    var onMove: (() -> Unit)? = null
    var onChangeIcon: (() -> Unit)? = null
    var onDismiss: (() -> Unit)? = null

    private var popupWindow: PopupWindow? = null

    fun show(anchor: View, showRemove: Boolean, drawerIsWhite: Boolean = false) {
        dismiss()

        val bgColor = if (drawerIsWhite) Color.BLACK else Color.WHITE
        val textColor = if (drawerIsWhite) Color.WHITE else Color.BLACK

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            // Aumentato del 30%: 32 -> 42, 16 -> 21
            setPadding(42, 21, 42, 21)
            val drawable = GradientDrawable().apply {
                // Aumentato del 30%: 16 -> 21
                cornerRadius = 21f
                setColor(bgColor)
            }
            background = drawable
            elevation = 8f * ctx.resources.displayMetrics.density
        }

        if (showRemove) {
            container.addView(createMenuItem(ctx.getString(R.string.menu_remove), textColor) {
                onRemove?.invoke()
                dismiss()
            })
        }

        container.addView(createMenuItem(ctx.getString(R.string.menu_uninstall), textColor) {
            onUninstall?.invoke()
            dismiss()
        })

        container.addView(createMenuItem(ctx.getString(R.string.menu_rename), textColor) {
            onRename?.invoke()
            dismiss()
        })

        container.addView(createMenuItem("Cambia icona", textColor) {
            onChangeIcon?.invoke()
            dismiss()
        })

        popupWindow = PopupWindow(
            container,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            false
        ).apply {
            isOutsideTouchable = false
            isFocusable = false
            setOnDismissListener { onDismiss?.invoke() }
            showAtLocation(anchor, Gravity.CENTER, 0, 0)
        }
    }

    private fun createMenuItem(text: String, textColor: Int, onClick: () -> Unit): TextView {
        return TextView(ctx).apply {
            this.text = text
            setTextColor(textColor)
            // Aumentato del 30%: 16 -> 21
            textSize = 21f
            // Aumentato del 30%: 32 -> 42, 20 -> 26
            setPadding(42, 26, 42, 26)
            setOnClickListener { onClick() }
        }
    }

    fun dismiss() {
        popupWindow?.dismiss()
        popupWindow = null
    }

    fun isShowing(): Boolean = popupWindow?.isShowing == true

    fun isPointInside(screenX: Float, screenY: Float): Boolean {
        val pw = popupWindow ?: return false
        if (!pw.isShowing) return false
        val container = pw.contentView ?: return false
        val loc = IntArray(2)
        container.getLocationOnScreen(loc)
        return screenX >= loc[0] && screenX <= loc[0] + container.width &&
                screenY >= loc[1] && screenY <= loc[1] + container.height
    }
}
