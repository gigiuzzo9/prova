package com.simplylauncher

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.widget.PopupMenu

class AppContextMenuView(private val ctx: Context) {

    var onUninstall: (() -> Unit)? = null
    var onRename: (() -> Unit)? = null
    var onRemove: (() -> Unit)? = null
    var onMove: (() -> Unit)? = null
    var onDismiss: (() -> Unit)? = null

    private var popupMenu: PopupMenu? = null
    private var anchorView: View? = null
    private var menuRect = Rect()
    private var lastShowRemove = false
    private var lastDrawerIsWhite = false

    fun show(anchor: View, showRemove: Boolean, drawerIsWhite: Boolean = false) {
        dismiss()

        lastShowRemove = showRemove
        lastDrawerIsWhite = drawerIsWhite
        anchorView = anchor

        val wrapper = ContextThemeWrapper(ctx, android.R.style.Theme_DeviceDefault_Light_NoActionBar)
        val menu = PopupMenu(wrapper, anchor, Gravity.TOP or Gravity.END)
        menu.menu.add(0, 1, 0, ctx.getString(R.string.menu_remove))
        menu.menu.add(0, 2, 1, ctx.getString(R.string.menu_uninstall))
        menu.menu.add(0, 3, 2, ctx.getString(R.string.menu_rename))

        menu.menu.findItem(1).isVisible = showRemove

        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> { onRemove?.invoke(); true }
                2 -> { onUninstall?.invoke(); true }
                3 -> { onRename?.invoke(); true }
                else -> false
            }
        }
        menu.setOnDismissListener {
            popupMenu = null
            onDismiss?.invoke()
        }

        popupMenu = menu
        menu.show()
    }

    fun dismiss() {
        popupMenu?.dismiss()
        popupMenu = null
    }

    fun isShowing(): Boolean = popupMenu != null

    fun isPointInside(screenX: Float, screenY: Float): Boolean {
        val menu = popupMenu ?: return false
        val anchor = anchorView ?: return false

        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        // Stima: il menu appare vicino all'anchor. Se il tocco è molto lontano, consideralo fuori.
        val anchorCenterX = loc[0] + anchor.width / 2f
        val anchorCenterY = loc[1] + anchor.height / 2f
        val distX = kotlin.math.abs(screenX - anchorCenterX)
        val distY = kotlin.math.abs(screenY - anchorCenterY)
        return distX < 400f && distY < 400f
    }
}
