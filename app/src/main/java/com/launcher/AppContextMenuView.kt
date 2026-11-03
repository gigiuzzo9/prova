package com.simplylauncher

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
        val dividerColor = if (drawerIsWhite) 0x33FFFFFF else 0x33000000

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(42, 21, 42, 21)
            val drawable = GradientDrawable().apply {
                cornerRadius = 21f
                setColor(bgColor)
            }
            background = drawable
            elevation = 8f * ctx.resources.displayMetrics.density
        }

        var first = true

        fun addDividerIfNeeded() {
            if (!first) container.addView(createDivider(dividerColor))
            first = false
        }

        if (showRemove) {
            addDividerIfNeeded()
            container.addView(createMenuItem(ctx.getString(R.string.menu_remove), textColor) {
                onRemove?.invoke()
                dismiss()
            })
        }

        addDividerIfNeeded()
        container.addView(createMenuItem(ctx.getString(R.string.menu_uninstall), textColor) {
            onUninstall?.invoke()
            dismiss()
        })

        addDividerIfNeeded()
        container.addView(createMenuItem(ctx.getString(R.string.menu_rename), textColor) {
            onRename?.invoke()
            dismiss()
        })

        addDividerIfNeeded()
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
            textSize = 21f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setPadding(42, 26, 42, 26)
            setOnClickListener { onClick() }
        }
    }

    private fun createDivider(dividerColor: Int): View {
        return View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (1 * ctx.resources.displayMetrics.density).toInt()
            ).apply {
                val margin = (ctx.resources.displayMetrics.widthPixels * 0.05f).toInt()
                marginStart = margin
                marginEnd = margin
            }
            setBackgroundColor(dividerColor)
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
