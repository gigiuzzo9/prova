package com.simplylauncher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.PopupMenu

object AppContextMenu {

    fun show(
        context: Context,
        anchor: View,
        app: AppInfo,
        isInHome: Boolean,
        isInDock: Boolean,
        onRemove: () -> Unit,
        onRename: () -> Unit
    ) {
        val popup = PopupMenu(context, anchor)
        popup.menu.add(0, 1, 0, context.getString(R.string.menu_uninstall))
        popup.menu.add(0, 2, 1, context.getString(R.string.menu_rename))
        if (isInHome || isInDock) {
            popup.menu.add(0, 3, 2, context.getString(R.string.menu_remove))
        }

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    val intent = Intent(Intent.ACTION_DELETE).apply {
                        data = Uri.parse("package:${app.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    true
                }
                2 -> {
                    onRename()
                    true
                }
                3 -> {
                    onRemove()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }
}
