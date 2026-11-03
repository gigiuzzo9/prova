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

class HomePopupMenu(private val ctx: Context) {

    var onAddWidget: (() -> Unit)? = null
    var onAddPage: (() -> Unit)? = null
    var onChangeWallpaper: (() -> Unit)? = null
    var onRemovePage: (() -> Unit)? = null
    var onDismiss: (() -> Unit)? = null

    private var popupWindow: PopupWindow? = null

    fun show(anchor: View, showRemovePage: Boolean) {
        dismiss()

        val popupWidth = (ctx.resources.displayMetrics.widthPixels * 0.6f).toInt()

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 21, 0, 21)
            val drawable = GradientDrawable().apply {
                cornerRadius = 21f
                setColor(Color.WHITE)
            }
            background = drawable
            elevation = 8f * ctx.resources.displayMetrics.density
        }

        container.addView(createMenuItem(ctx.getString(R.string.menu_add_widget)) {
            onAddWidget?.invoke()
            dismiss()
        })

        container.addView(createDivider(popupWidth))

        container.addView(createMenuItem(ctx.getString(R.string.menu_add_page)) {
            onAddPage?.invoke()
            dismiss()
        })

        container.addView(createDivider(popupWidth))

        container.addView(createMenuItem(ctx.getString(R.string.menu_change_wallpaper)) {
            onChangeWallpaper?.invoke()
            dismiss()
        })

        if (showRemovePage) {
            container.addView(createDivider(popupWidth))
            container.addView(createMenuItem(ctx.getString(R.string.menu_remove_page)) {
                onRemovePage?.invoke()
                dismiss()
            })
        }

        popupWindow = PopupWindow(
            container,
            popupWidth,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            isFocusable = true
            setOnDismissListener { onDismiss?.invoke() }
            showAtLocation(anchor, Gravity.CENTER, 0, 0)
        }
    }

    private fun createMenuItem(text: String, onClick: () -> Unit): TextView {
        return TextView(ctx).apply {
            this.text = text
            setTextColor(Color.BLACK)
            textSize = 21f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setPadding(42, 26, 42, 26)
            setOnClickListener { onClick() }
        }
    }

    private fun createDivider(popupWidth: Int): View {
        return View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                (popupWidth * 0.9f).toInt(),
                (1 * ctx.resources.displayMetrics.density).toInt()
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
            setBackgroundColor(0x33000000)
        }
    }

    fun dismiss() {
        popupWindow?.dismiss()
        popupWindow = null
    }

    fun isShowing(): Boolean = popupWindow?.isShowing == true
}
