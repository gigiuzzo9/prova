package com.simplylauncher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class WidgetPickerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_APPWIDGET_ID = "picker_appwidget_id"
        const val EXTRA_NEEDS_BIND = "picker_needs_bind"
        const val EXTRA_PROVIDER = "picker_provider"

        private const val PREVIEW_HOST_ID = 0xABCD
    }

    private lateinit var appWidgetManager: AppWidgetManager
    private var appWidgetId = -1
    private var previewHost: AppWidgetHost? = null
    private lateinit var previewContainer: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetId = intent.getIntExtra(EXTRA_APPWIDGET_ID, -1)

        if (appWidgetId == -1) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        previewHost = AppWidgetHost(this, PREVIEW_HOST_ID).also { it.startListening() }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(16), 0, dp(16))
        }

        root.addView(TextView(this).apply {
            text = "Scegli un widget"
            textSize = 20f
            setPadding(dp(24), dp(16), dp(24), dp(16))
            setTypeface(typeface, Typeface.BOLD)
        })

        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, dp(8), dp(16))
        }
        scroll.addView(list)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        // Container invisibile per renderizzare le preview widget
        previewContainer = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(0, 0)
        }
        root.addView(previewContainer)

        setContentView(root)

        // Popoliamo DOPO che il root è attached: nessun accesso a decorView
        root.post {
            if (isFinishing || isDestroyed) return@post
            try {
                val providers = appWidgetManager.installedProviders
                    .sortedBy { it.loadLabel(packageManager).toString().lowercase() }

                if (providers.isEmpty()) {
                    list.addView(TextView(this).apply {
                        text = "Nessun widget disponibile"
                        textSize = 16f
                        gravity = Gravity.CENTER
                        setPadding(0, dp(48), 0, 0)
                    })
                } else {
                    providers.forEach { info ->
                        try {
                            list.addView(createWidgetRow(info))
                        } catch (_: Exception) {
                            // un widget rotto non deve uccidere la lista
                        }
                    }
                }
            } catch (_: Exception) {
                // non crashare mai per la lista
            }
        }
    }

    override fun onDestroy() {
        try { previewHost?.stopListening() } catch (_: Exception) {}
        previewHost = null
        super.onDestroy()
    }

    private fun createWidgetRow(info: AppWidgetProviderInfo): LinearLayout {
        val ctx: Context = this

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            isClickable = true
            isFocusable = true
            val outValue = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
        }

        val icon = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(96), dp(96)).apply {
                marginEnd = dp(16)
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageResource(android.R.drawable.sym_def_app_icon)
        }
        row.addView(icon)

        // 1) API veloci (previewImage / loadPreviewImage)
        val fastPreview = tryLoadQuickPreview(ctx, info)
        if (fastPreview != null) {
            icon.setImageDrawable(fastPreview)
        } else {
            // 2) Fallback: renderizza davvero il widget
            renderWidgetPreview(info, icon)
        }

        val texts = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        texts.addView(TextView(ctx).apply {
            text = info.loadLabel(ctx.packageManager)
            textSize = 16f
        })

        val appName = try {
            val ai = ctx.packageManager.getApplicationInfo(info.provider.packageName, 0)
            ctx.packageManager.getApplicationLabel(ai).toString()
        } catch (_: Exception) { "" }

        texts.addView(TextView(ctx).apply {
            text = appName
            textSize = 13f
            alpha = 0.6f
        })

        row.addView(texts)

        row.setOnClickListener {
            val bound = try {
                appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, info.provider)
            } catch (_: Exception) { false }

            val result = Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(EXTRA_NEEDS_BIND, !bound)
                putExtra(EXTRA_PROVIDER, info.provider)
            }
            setResult(Activity.RESULT_OK, result)
            finish()
        }

        return row
    }

    private fun tryLoadQuickPreview(ctx: Context, info: AppWidgetProviderInfo): Drawable? {
        if (info.previewImage != 0) {
            try {
                ContextCompat.getDrawable(ctx, info.previewImage)?.let { return it }
            } catch (_: Exception) {}
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                info.loadPreviewImage(ctx, 0)?.let { return it }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun renderWidgetPreview(info: AppWidgetProviderInfo, target: ImageView) {
        val host = previewHost ?: return

        val tempId = try { host.allocateAppWidgetId() } catch (_: Exception) { return }

        val bound = try {
            appWidgetManager.bindAppWidgetIdIfAllowed(tempId, info.provider)
        } catch (_: Exception) { false }

        if (!bound) {
            try { host.deleteAppWidgetId(tempId) } catch (_: Exception) {}
            return
        }

        val hostView: AppWidgetHostView? = try {
            host.createView(this, tempId, info)
        } catch (_: Exception) {
            null
        }

        if (hostView == null) {
            try { host.deleteAppWidgetId(tempId) } catch (_: Exception) {}
            return
        }

        val widthPx = maxOf(info.minWidth, dp(200))
        val heightPx = maxOf(info.minHeight, dp(200))

        val container = FrameLayout(this)
        try {
            container.addView(hostView, FrameLayout.LayoutParams(widthPx, heightPx))
        } catch (_: Exception) {
            try { host.deleteAppWidgetId(tempId) } catch (_: Exception) {}
            return
        }
        // Non alpha=0: alcuni widget non disegnano se invisibili
        container.alpha = 0.01f

        val cleanup: () -> Unit = {
            try { previewContainer.removeView(container) } catch (_: Exception) {}
            try { host.deleteAppWidgetId(tempId) } catch (_: Exception) {}
        }

        try {
            previewContainer.addView(
                container,
                ViewGroup.LayoutParams(widthPx, heightPx)
            )
        } catch (_: Exception) {
            cleanup()
            return
        }

        val handler = Handler(Looper.getMainLooper())
        var attempts = 0
        val maxAttempts = 12

        fun tryCapture() {
            attempts++
            if (isFinishing || isDestroyed) {
                cleanup()
                return
            }
            val bmp = captureView(hostView)
            if (bmp != null) {
                target.setImageBitmap(bmp)
                cleanup()
                return
            }
            if (attempts < maxAttempts) {
                handler.postDelayed({ tryCapture() }, 80L)
            } else {
                cleanup()
            }
        }

        handler.postDelayed({ tryCapture() }, 150L)
    }

    private fun captureView(view: View): Bitmap? {
        if (view.width <= 0 || view.height <= 0) return null
        return try {
            val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bmp))
            bmp
        } catch (_: Exception) {
            null
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
