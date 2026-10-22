package com.simplylauncher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

/**
 * Pallini indicatori delle pagine della home.
 * Disegna N cerchi in fila orizzontale, con il pallino attivo più luminoso.
 */
class PageIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var pageCount: Int = 1
    private var currentPage: Int = 0

    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }

    private val inactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66FFFFFF
        style = Paint.Style.FILL
    }

    private val radiusPx = 4f * resources.displayMetrics.density
    private val spacingPx = 8f * resources.displayMetrics.density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthNeeded = (pageCount * radiusPx * 2 + (pageCount - 1) * spacingPx).toInt() +
                paddingLeft + paddingRight
        val heightNeeded = (radiusPx * 2 + paddingTop + paddingBottom).toInt()

        val width = resolveSize(max(widthNeeded, suggestedMinimumWidth), widthMeasureSpec)
        val height = resolveSize(max(heightNeeded, suggestedMinimumHeight), heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (pageCount <= 0) return

        val totalWidth = pageCount * radiusPx * 2 + (pageCount - 1) * spacingPx
        val startX = (width - totalWidth) / 2f + radiusPx
        val cy = height / 2f

        for (i in 0 until pageCount) {
            val cx = startX + i * (radiusPx * 2 + spacingPx)
            val paint = if (i == currentPage) activePaint else inactivePaint
            canvas.drawCircle(cx, cy, radiusPx, paint)
        }
    }

    fun setPageCount(count: Int) {
        val newCount = max(1, count)
        if (pageCount != newCount) {
            pageCount = newCount
            if (currentPage >= pageCount) currentPage = pageCount - 1
            requestLayout()
            invalidate()
        }
    }

    fun setCurrentPage(page: Int) {
        val newPage = page.coerceIn(0, pageCount - 1)
        if (currentPage != newPage) {
            currentPage = newPage
            invalidate()
        }
    }
}
