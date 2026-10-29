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
        
        // 1. Prova con previewLayout (Android 12+)
        if (android.os.Build.VERSION.SDK_INT >= 31 && info.previewLayout != 0) {
            try {
                val remoteViews = android.widget.RemoteViews(info.packageName, info.previewLayout)
                // Gonfia il layout in una View per poterlo disegnare
                val view = remoteViews.apply(ctx, android.widget.FrameLayout(ctx))
                view.measure(
                    android.view.View.MeasureSpec.makeMeasureSpec(dp(64), android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(dp(64), android.view.View.MeasureSpec.EXACTLY)
                )
                view.layout(0, 0, view.measuredWidth, view.measuredHeight)
                
                // Crea un bitmap dall'anteprima e impostalo
                val bmp = android.graphics.Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bmp)
                view.draw(canvas)
                preview = android.graphics.drawable.BitmapDrawable(ctx.resources, bmp)
            } catch (_: Exception) {}
        }

        // 2. Se non c'è previewLayout o è fallito, prova con previewImage
        if (preview == null && info.previewImage != 0) {
            try {
                preview = androidx.core.content.ContextCompat.getDrawable(ctx, info.previewImage)
            } catch (_: Exception) {}
        }

        // 3. Fallback: icona dell'app
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
