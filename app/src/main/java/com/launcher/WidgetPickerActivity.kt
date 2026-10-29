package com.simplylauncher

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
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
        }

        // Header con titolo e pulsante chiudi
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(16), dp(12), dp(16))
        }

        val title = TextView(this).apply {
            text = "Scegli un widget"
            textSize = 22f
            setTextColor(getThemeColor(android.R.attr.textColorPrimary))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(title)

        val closeBtn = TextView(this).apply {
            text = "✕"
            textSize = 24f
            setTextColor(getThemeColor(android.R.attr.textColorPrimary))
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        }
        header.addView(closeBtn)

        root.addView(header)

        // Divider sottile
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)
            )
            setBackgroundColor(getThemeColor(android.R.attr.colorControlNormal) and 0x22FFFFFF or 0x22000000)
        }
        root.addView(divider)

        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(16))
        }
        scroll.addView(list)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        setContentView(root)

        val providers = try {
            appWidgetManager.installedProviders
                .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
        } catch (e: Exception) { emptyList() }

        if (providers.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "Nessun widget disponibile"
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(getThemeColor(android.R.attr.textColorSecondary))
                setPadding(0, dp(48), 0, 0)
            })
        } else {
            providers.forEach { info ->
                try { list.addView(createWidgetRow(info)) } catch (_: Exception) {}
            }
        }
    }

    private fun createWidgetRow(info: AppWidgetProviderInfo): LinearLayout {
        val ctx: Context = this

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            isClickable = true
            isFocusable = true
            val outValue = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
        }

        // Preview con aspect ratio corretto
        val previewContainer = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(96), dp(96)).apply {
                marginEnd = dp(16)
            }
        }

        val preview = tryLoadPreview(ctx, info)
        if (preview != null) {
            val imageView = ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageDrawable(preview)
            }
            previewContainer.addView(imageView, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        } else {
            // Fallback: icona app
            val appIcon = try {
                val ai = ctx.packageManager.getApplicationInfo(info.provider.packageName, 0)
                ctx.packageManager.getApplicationIcon(ai)
            } catch (e: Exception) { null }

            val imageView = ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                if (appIcon != null) setImageDrawable(appIcon)
                else setImageResource(android.R.drawable.sym_def_app_icon)
            }
            previewContainer.addView(imageView, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }
        row.addView(previewContainer)

        // Testi: nome widget + nome app
        val texts = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        // Nome widget (titolo)
        texts.addView(TextView(ctx).apply {
            text = try { info.loadLabel(ctx.packageManager) } catch (e: Exception) { "Widget" }
            textSize = 16f
            setTextColor(getThemeColor(android.R.attr.textColorPrimary))
        })

        // Nome app (sottotitolo) — ora visibile con colore del tema
        val appName = try {
            val ai = ctx.packageManager.getApplicationInfo(info.provider.packageName, 0)
            ctx.packageManager.getApplicationLabel(ai).toString()
        } catch (e: Exception) { "" }

        if (appName.isNotEmpty()) {
            texts.addView(TextView(ctx).apply {
                text = appName
                textSize = 13f
                setTextColor(getThemeColor(android.R.attr.textColorSecondary))
                setPadding(0, dp(2), 0, 0)
            })
        }

        row.addView(texts)

        row.setOnClickListener {
            val bound = try {
                appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, info.provider)
            } catch (e: Exception) { false }

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
     * Carica l'anteprima con dimensioni corrette.
     * - Prova loadPreviewImage (API 31+) che rispetta minWidth/minHeight
     * - Fallback a previewImage (resource ID)
     * - Se il drawable ha dimensioni intrinseche nulle/assurde, restituisce null
     */
    private fun tryLoadPreview(ctx: Context, info: AppWidgetProviderInfo): Drawable? {
        // Calcola densità di rendering basata sulla dimensione minima dichiarata
        val densityDpi = resources.displayMetrics.densityDpi

        // 1) loadPreviewImage (API 31+) — il sistema sceglie la dimensione giusta
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val d = info.loadPreviewImage(ctx, densityDpi)
                if (d != null && d.intrinsicWidth > 0 && d.intrinsicHeight > 0) {
                    return scaleDrawable(d, info)
                }
            } catch (e: Exception) {}
        }

        // 2) previewImage (resource ID) — funziona su tutte le API
        if (info.previewImage != 0) {
            try {
                val d = ContextCompat.getDrawable(ctx, info.previewImage)
                if (d != null) {
                    return scaleDrawable(d, info)
                }
            } catch (e: Exception) {}
        }

        return null
    }

    /**
     * Scala il drawable per adattarlo al contenitore 96x96dp rispettando
     * l'aspect ratio dichiarato dal widget (minWidth x minHeight).
     * Se il drawable è già piccolo, lo lascia com'è.
     */
    private fun scaleDrawable(d: Drawable, info: AppWidgetProviderInfo): Drawable {
        // Se il widget dichiara minWidth/minHeight, li usiamo come riferimento per l'aspect ratio
        val w = if (info.minWidth > 0) info.minWidth else d.intrinsicWidth
        val h = if (info.minHeight > 0) info.minHeight else d.intrinsicHeight

        if (w <= 0 || h <= 0) return d

        val ratio = w.toFloat() / h.toFloat()

        // Calcola la dimensione target in dp mantenendo il ratio dentro un box 96x96dp
        val maxDp = 96
        val targetW: Int
        val targetH: Int
        if (ratio >= 1f) {
            targetW = maxDp
            targetH = (maxDp / ratio).toInt().coerceAtLeast(1)
        } else {
            targetH = maxDp
            targetW = (maxDp * ratio).toInt().coerceAtLeast(1)
        }

        // Imposta i bounds del drawable alla dimensione calcolata
        val wPx = dp(targetW)
        val hPx = dp(targetH)
        d.setBounds(0, 0, wPx, hPx)
        return d
    }

    private fun getThemeColor(attr: Int): Int {
        val typedValue = TypedValue()
        if (theme.resolveAttribute(attr, typedValue, true)) {
            return if (typedValue.resourceId != 0) {
                ContextCompat.getColor(this, typedValue.resourceId)
            } else {
                typedValue.data
            }
        }
        return 0xFF000000.toInt()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
