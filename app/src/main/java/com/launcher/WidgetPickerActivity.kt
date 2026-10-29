package com.simplylauncher

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class WidgetPickerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_APPWIDGET_ID = "picker_appwidget_id"
        const val EXTRA_NEEDS_BIND = "picker_needs_bind"
        const val EXTRA_PROVIDER = "picker_provider"
    }

    private lateinit var appWidgetManager: AppWidgetManager
    private var appWidgetId = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetId = intent.getIntExtra(EXTRA_APPWIDGET_ID, -1)

        if (appWidgetId == -1) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

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
            .filter {
                it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0
            }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }

        if (providers.isEmpty()) {
            val empty = TextView(this).apply {
                text = "Nessun widget disponibile"
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(0, dp(48), 0, 0)
            }
            list.addView(empty)
        } else {
            providers.forEach { info ->
                list.addView(createWidgetRow(info))
            }
        }
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
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(64)).apply {
                marginEnd = dp(16)
            }
            scaleType = ImageView.ScaleType.CENTER_INSIDE

            var preview: Drawable? = null

            // 1. Anteprima reale (Android 12+)
            if (Build.VERSION.SDK_INT >= 31 && info.previewLayout != 0) {
                try {
                    val remoteViews = RemoteViews(info.provider.packageName, info.previewLayout)
                    val previewView = remoteViews.apply(ctx, FrameLayout(ctx))
                    previewView.measure(
                        View.MeasureSpec.makeMeasureSpec(dp(64), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(dp(64), View.MeasureSpec.EXACTLY)
                    )
                    previewView.layout(0, 0, previewView.measuredWidth, previewView.measuredHeight)

                    val bmp = Bitmap.createBitmap(
                        previewView.measuredWidth,
                        previewView.measuredHeight,
                        Bitmap.Config.ARGB_8888
                    )
                    val canvas = Canvas(bmp)
                    previewView.draw(canvas)
                    preview = BitmapDrawable(ctx.resources, bmp)
                } catch (_: Exception) {}
            }

            // 2. Fallback: previewImage
            if (preview == null && info.previewImage != 0) {
                try {
                    preview = androidx.core.content.ContextCompat.getDrawable(ctx, info.previewImage)
                } catch (_: Exception) {}
            }

            // 3. Fallback: icona app
            if (preview == null) {
                try {
                    preview = info.loadIcon(ctx, 0)
                } catch (_: Exception) {}
            }

            setImageDrawable(preview)
        }
        row.addView(icon)

        val texts = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val label = TextView(ctx).apply {
            text = info.loadLabel(ctx.packageManager)
            textSize = 16f
        }
        texts.addView(label)

        val appName = try {
            val ai = ctx.packageManager.getApplicationInfo(info.provider.packageName, 0)
            ctx.packageManager.getApplicationLabel(ai).toString()
        } catch (e: Exception) {
            ""
        }
        val providerName = TextView(ctx).apply {
            text = appName
            textSize = 13f
            alpha = 0.6f
        }
        texts.addView(providerName)

        row.addView(texts)

        row.setOnClickListener {
            val bound = try {
                appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, info.provider)
            } catch (e: Exception) {
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

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
