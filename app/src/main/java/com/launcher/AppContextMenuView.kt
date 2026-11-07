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

    // Le callback ricevono shortcutId/instanceId dello slot su cui è stato aperto il menu
    var onUninstall: ((shortcutId: String, instanceId: String) -> Unit)? = null
    var onRename: ((shortcutId: String, instanceId: String) -> Unit)? = null
    var onRemove: ((shortcutId: String, instanceId: String) -> Unit)? = null
    var onMove: (() -> Unit)? = null
    var onChangeIcon: ((shortcutId: String, instanceId: String) -> Unit)? = null
    var onDismiss: (() -> Unit)? = null

    private var popupWindow: PopupWindow? = null

    fun show(
        anchor: View,
        showRemove: Boolean,
        drawerIsWhite: Boolean = false,
        shortcutId: String = "",
        instanceId: String = ""
    ) {
        dismiss()

        val bgColor = if (drawerIsWhite) Color.BLACK else Color.WHITE
        val textColor = if (drawerIsWhite) Color.WHITE else Color.BLACK
        val dividerColor = if (drawerIsWhite) 0x33FFFFFF else 0x33000000
        val popupWidth = (ctx.resources.displayMetrics.widthPixels * 0.6f).toInt()

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 21, 0, 21)
            val drawable = GradientDrawable().apply {
                cornerRadius = 21f
                setColor(bgColor)
            }
            background = drawable
            elevation = 8f * ctx.resources.displayMetrics.density
        }

        var first = true
        fun addDividerIfNeeded() {
            if (!first) container.addView(createDivider(popupWidth, dividerColor))
            first = false
        }

        if (showRemove) {
            addDividerIfNeeded()
            container.addView(createMenuItem(ctx.getString(R.string.menu_remove), textColor) {
                onRemove?.invoke(shortcutId, instanceId)
                dismiss()
            })
        }

        addDividerIfNeeded()
        container.addView(createMenuItem(ctx.getString(R.string.menu_uninstall), textColor) {
            onUninstall?.invoke(shortcutId, instanceId)
            dismiss()
        })

        addDividerIfNeeded()
        container.addView(createMenuItem(ctx.getString(R.string.menu_rename), textColor) {
            onRename?.invoke(shortcutId, instanceId)
            dismiss()
        })

        addDividerIfNeeded()
        container.addView(createMenuItem("Cambia icona", textColor) {
            onChangeIcon?.invoke(shortcutId, instanceId)
            dismiss()
        })

        popupWindow = PopupWindow(
            container,
            popupWidth,
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

    private fun createDivider(popupWidth: Int, dividerColor: Int): View {
        return View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                (popupWidth * 0.9f).toInt(),
                (1 * ctx.resources.displayMetrics.density).toInt()
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
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
