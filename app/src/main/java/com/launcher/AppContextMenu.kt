package com.simplylauncher

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView

object AppContextMenu {

    private var popupWindow: PopupWindow? = null

    fun show(
        context: Context,
        anchor: View,
        app: AppInfo,
        isInHome: Boolean,
        isInDock: Boolean,
        onRemove: () -> Unit,
        onRename: () -> Unit
    ) {
        popupWindow?.dismiss()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
            val drawable = GradientDrawable().apply {
                cornerRadius = 16f
                setColor(Color.WHITE)
            }
            background = drawable
            elevation = 8f * context.resources.displayMetrics.density
        }

        // 1. Disinstalla
        container.addView(createMenuItem(context, context.getString(R.string.menu_uninstall)) {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:${app.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            popupWindow?.dismiss()
        })

        // 2. Rinomina
        container.addView(createDivider(context))
        container.addView(createMenuItem(context, context.getString(R.string.menu_rename)) {
            onRename()
            popupWindow?.dismiss()
        })

        // 3. Rimuovi (solo se in home o dock)
        if (isInHome || isInDock) {
            container.addView(createDivider(context))
            container.addView(createMenuItem(context, context.getString(R.string.menu_remove)) {
                onRemove()
                popupWindow?.dismiss()
            })
        }

        popupWindow = PopupWindow(
            container,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            isFocusable = true
            showAtLocation(anchor, Gravity.CENTER, 0, 0)
        }
    }

    private fun createMenuItem(context: Context, text: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(Color.BLACK)
            textSize = 16f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setPadding(32, 20, 32, 20)
            val outValue = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
            setOnClickListener { onClick() }
        }
    }

    private fun createDivider(context: Context): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (1 * context.resources.displayMetrics.density).toInt()
            ).apply {
                val margin = (context.resources.displayMetrics.widthPixels * 0.05f).toInt()
                marginStart = margin
                marginEnd = margin
            }
            setBackgroundColor(0x33000000)
        }
    }
}
