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

    private val mainHandler = Handler(Looper.getMainLooper())

    // Coda per il rendering sequenziale delle preview
    private data class PreviewJob(
        val info: AppWidgetProviderInfo,
        val target: ImageView
    )

    private val previewQueue = ArrayDeque<PreviewJob>()
    private var isRendering = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetId = intent.getIntExtra(EXTRA_APPWIDGET_ID, -1)

        if (appWidgetId == -1) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        try {
            previewHost = AppWidgetHost(this, PREVIEW_HOST_ID).also { it.startListening() }
        } catch (_: Exception) {
            previewHost = null
        }

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

        previewContainer = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(0, 0)
        }
        root.addView(previewContainer)

        setContentView(root)

        root.post { populateList(list) }
    }

    override fun onDestroy() {
        try { mainHandler.removeCallbacksAndMessages(null) } catch (_: Exception) {}
        previewQueue.clear()
        isRendering = false
        try { previewHost?.stopListening() } catch (_: Exception) {}
        previewHost = null
        try { previewContainer.removeAllViews() } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun populateList(list: LinearLayout) {
        if (isFinishing || isDestroyed) return

        val providers: List<AppWidgetProviderInfo> = try {
            appWidgetManager.installedProviders
                .sortedBy { info ->
                    try {
                        info.loadLabel(packageManager).toString().lowercase()
                    } catch (_: Exception) {
                        ""
                    }
                }
        } catch (_: Exception) {
            emptyList()
        }

        if (providers.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "Nessun widget disponibile"
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(0, dp(48), 0, 0)
            })
            return
        }

        val queue = ArrayDeque(providers)

        fun processNext() {
            if (isFinishing || isDestroyed) return
            val info = queue.removeFirstOrNull() ?: return
            try {
                list.addView(createWidgetRow(info))
            } catch (_: Exception) {
                // ignora widget rotto
            }
            mainHandler.postDelayed({ processNext() }, 20L)
        }

        processNext()
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

        // 1) API veloci
        val fastPreview = tryLoadQuickPreview(ctx, info)
        if (fastPreview != null) {
            icon.setImageDrawable(fastPreview)
        } else {
            // 2) Metti in coda per rendering sequenziale
            enqueuePreview(info, icon)
        }

        val texts = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        texts.addView(TextView(ctx).apply {
            text = try { info.loadLabel(ctx.packageManager) } catch (_: Exception) { "" }
            textSize = 16f
        })

        val appName = try {
            val ai = ctx.packageManager.getApplicationInfo(info.provider.packageName, 0)
            ctx.packageManager.getApplicationLabel(ai).toString()
        } catch (_: Exception) {
            ""
        }

        texts.addView(TextView(ctx).apply {
            text = appName
            textSize = 13f
            alpha = 0.6f
        })

        row.addView(texts)

        row.setOnClickListener {
            val bound = try {
                appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, info.provider)
            } catch (_: Exception) {
                false
            }

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

    // ===== CODA SEQUENZIALE =====

    private fun enqueuePreview(info: AppWidgetProviderInfo, target: ImageView) {
        previewQueue.addLast(PreviewJob(info, target))
        if (!isRendering) {
            isRendering = true
            renderNext()
        }
    }

    private fun renderNext() {
        if (isFinishing || isDestroyed) {
            isRendering = false
            previewQueue.clear()
            return
        }

        val job = previewQueue.removeFirstOrNull()
        if (job == null) {
            isRendering = false
            return
        }

        renderSinglePreview(job) {
            // Callback quando la preview corrente è finita (successo o fallimento)
            mainHandler.postDelayed({ renderNext() }, 30L)
        }
    }

    private fun renderSinglePreview(job: PreviewJob, onDone: () -> Unit) {
        val host = previewHost
        if (host == null) { onDone(); return }

        val tempId = try { host.allocateAppWidgetId() } catch (_: Exception) { onDone(); return }

        val bound = try {
            appWidgetManager.bindAppWidgetIdIfAllowed(tempId, job.info.provider)
        } catch (_: Exception) { false }

        if (!bound) {
            try { host.deleteAppWidgetId(tempId) } catch (_: Exception) {}
            onDone()
            return
        }

        val hostView: AppWidgetHostView? = try {
            host.createView(this, tempId, job.info)
        } catch (_: Exception) { null }

        if (hostView == null) {
            try { host.deleteAppWidgetId(tempId) } catch (_: Exception) {}
            onDone()
            return
        }

        try { hostView.setLayerType(View.LAYER_TYPE_SOFTWARE, null) } catch (_: Exception) {}

        val widthPx = maxOf(job.info.minWidth, dp(200))
        val heightPx = maxOf(job.info.minHeight, dp(200))

        val container = FrameLayout(this)
        try {
            container.addView(hostView, FrameLayout.LayoutParams(widthPx, heightPx))
        } catch (_: Exception) {
            try { host.deleteAppWidgetId(tempId) } catch (_: Exception) {}
            onDone()
            return
        }
        container.alpha = 0.01f

        var done = false

        fun cleanupAndDone() {
            if (done) return
            done = true
            try { (container.parent as? ViewGroup)?.removeView(container) } catch (_: Exception) {}
            try { container.removeAllViews() } catch (_: Exception) {}
            try { host.deleteAppWidgetId(tempId) } catch (_: Exception) {}
            onDone()
        }

        try {
            previewContainer.addView(container, ViewGroup.LayoutParams(widthPx, heightPx))
        } catch (_: Exception) {
            cleanupAndDone()
            return
        }

        var attempts = 0
        val maxAttempts = 8

        fun tryCapture() {
            if (done) return
            if (isFinishing || isDestroyed) { cleanupAndDone(); return }
            if (hostView.parent == null) { cleanupAndDone(); return }

            attempts++
            val bmp = captureView(hostView)
            if (bmp != null) {
                try { job.target.setImageBitmap(bmp) } catch (_: Exception) {}
                cleanupAndDone()
                return
            }
            if (attempts < maxAttempts) {
                mainHandler.postDelayed({ tryCapture() }, 100L)
            } else {
                cleanupAndDone()
            }
        }

        mainHandler.postDelayed({ tryCapture() }, 200L)
    }

    private fun captureView(view: View): Bitmap? {
        val w = view.width
        val h = view.height
        if (w <= 0 || h <= 0) return null
        if (!view.isAttachedToWindow) return null
        return try {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bmp))
            bmp
        } catch (_: Throwable) {
            null
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
