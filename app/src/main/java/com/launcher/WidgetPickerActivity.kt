package com.simplylauncher

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
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

        setContentView(root)

        val providers = try {
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
        } else {
            providers.forEach { info ->
                try {
                    list.addView(createWidgetRow(info))
                } catch (_: Exception) {
                    // un widget rotto non deve uccidere la lista
                }
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
            layoutParams = LinearLayout.LayoutParams(dp(96), dp(96)).apply {
                marginEnd = dp(16)
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageResource(android.R.drawable.sym_def_app_icon)
        }
        row.addView(icon)

        // Solo API ufficiali: previewImage / loadPreviewImage.
        // Nessun rendering forzato → nessun crash.
        val preview = tryLoadPreview(ctx, info)
        if (preview != null) {
            icon.setImageDrawable(preview)
        } else {
            // Fallback: icona dell'app (già impostata come default)
            try {
                val ai = ctx.packageManager.getApplicationInfo(info.provider.packageName, 0)
                icon.setImageDrawable(ctx.packageManager.getApplicationIcon(ai))
            } catch (_: Exception) {
                // resta sym_def_app_icon
            }
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

    /**
     * Carica l'anteprima usando SOLO le API ufficiali.
     * - previewImage: resource ID statico (tutte le versioni)
     * - loadPreviewImage: API 31+ (Android 12+)
     *
     * Se entrambe falliscono, restituisce null → il chiamante usa l'icona app.
     */
    private fun tryLoadPreview(ctx: Context, info: AppWidgetProviderInfo): Drawable? {
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

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
