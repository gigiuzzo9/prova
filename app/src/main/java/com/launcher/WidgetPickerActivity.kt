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
import android.util.Log
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
        private const val TAG = "WidgetPicker"
    }

    private lateinit var appWidgetManager: AppWidgetManager
    private var appWidgetId = -1

    // Host "usa e getta" per renderizzare i widget in preview
    private var previewHost: AppWidgetHost? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetId = intent.getIntExtra(EXTRA_APPWIDGET_ID, -1)

        if (appWidgetId == -1) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        // Host separato per le preview — id diverso da quello del launcher vero
        previewHost = AppWidgetHost(this, PREVIEW_HOST_ID).also { it.startListening() }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(16), 0, dp(16))
        }

        val title = TextView(this).apply {
            text = "Scegli un widget"
            textSize = 20f
            setPadding(dp(24), dp(16), dp(24), dp(16))
            setTypeface(typeface, Typeface.BOLD)
        }
        root.addView(title)

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

        setContentView(root)

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
                list.addView(createWidgetRow(info))
            }
        }
    }

    override fun onDestroy() {
        previewHost?.stopListening()
        previewHost?.release()
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
            setImageResource(android.R.drawable.sym_def_app_icon) // placeholder
        }
        row.addView(icon)

        // 1) Prova prima il metodo "veloce" (previewImage / loadPreviewImage)
        val fastPreview = tryLoadQuickPreview(ctx, info)
        if (fastPreview != null) {
            icon.setImageDrawable(fastPreview)
        } else {
            // 2) Fallback: renderizza il widget vero in un host fittizio
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

    /**
     * Metodo veloce: prova le API ufficiali. Spesso fallisce su Android 12+,
     * ma quando funziona è istantaneo e non sporca l'AppWidgetHost.
     */
    private fun tryLoadQuickPreview(ctx: Context, info: AppWidgetProviderInfo): Drawable? {
        // 1) previewImage (resource ID) — funziona su tutte le API
        if (info.previewImage != 0) {
            try {
                ContextCompat.getDrawable(ctx, info.previewImage)?.let { return it }
            } catch (_: Exception) {}
        }
        // 2) loadPreviewImage (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                info.loadPreviewImage(ctx, 0)?.let { return it }
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * Metodo definitivo: alloca un id temporaneo nell'host di preview,
     * bindalo al provider, crea l'AppWidgetHostView, aspetta che il widget
     * disegni, poi cattura la Bitmap e rilascia l'id.
     */
    private fun renderWidgetPreview(info: AppWidgetProviderInfo, target: ImageView) {
        val host = previewHost ?: return

        val tempId = host.allocateAppWidgetId()

        val bound = try {
            appWidgetManager.bindAppWidgetIdIfAllowed(tempId, info.provider)
        } catch (_: Exception) { false }

        if (!bound) {
            // Senza permesso di bind non possiamo renderizzare: lasciamo il placeholder
            host.deleteAppWidgetId(tempId)
            Log.w(TAG, "bind failed for ${info.provider}")
            return
        }

        val hostView: AppWidgetHostView = host.createView(
            this,
            tempId,
            info
        )

        // Dimensioni di misura: usiamo la dimensione minima dichiarata,
        // ma almeno 200x200 dp per avere una preview decente
        val widthPx = maxOf(info.minWidth, dp(200))
        val heightPx = maxOf(info.minHeight, dp(200))

        // HostView deve stare in una hierarchy per poter disegnare
        val container = FrameLayout(this)
        container.addView(
            hostView,
            FrameLayout.LayoutParams(widthPx, heightPx)
        )

        // Attach invisibile fuori schermo
        val root = window.decorView as ViewGroup
        val params = ViewGroup.LayoutParams(widthPx, heightPx)
        container.layoutParams = params
        container.alpha = 0f
        root.addView(container)

        // Aspettiamo che il widget finisca di disegnarsi, poi catturiamo
        val handler = Handler(Looper.getMainLooper())
        var attempts = 0
        val maxAttempts = 10

        fun tryCapture() {
            attempts++
            val bmp = captureView(hostView, widthPx, heightPx)
            if (bmp != null) {
                target.setImageBitmap(bmp)
                cleanup()
                return
            }
            if (attempts < maxAttempts && !isFinishing && !isDestroyed) {
                handler.postDelayed({ tryCapture() }, 80L)
            } else {
                cleanup()
            }
        }

        fun cleanup() {
            try { root.removeView(container) } catch (_: Exception) {}
            try { host.deleteAppWidgetId(tempId) } catch (_: Exception) {}
        }

        // Primo tentativo dopo un attimo per dare tempo al widget di inflate
        handler.postDelayed({ tryCapture() }, 150L)
    }

    private fun captureView(view: View, w: Int, h: Int): Bitmap? {
        if (view.width == 0 || view.height == 0) return null
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        view.draw(canvas)
        return if (bmp.width > 0 && bmp.height > 0) bmp else null
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val PREVIEW_HOST_ID = 0xABCD // qualsiasi numero, diverso dall'host reale
    }
}
