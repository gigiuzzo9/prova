package com.simplylauncher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.min

class HomeScreenGrid @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val drawingArea = HomeScreenGridDrawingArea(context)

    private var columnCount = 4
    private var rowCount = 6
    private var pageCount = 1
    private var currentPage = 0

    private var items: List<HomeSlot> = emptyList()

    private var cellWidth = 0
    private var cellHeight = 0
    private var iconSize = 0
    private var iconMargin = 0
    private var labelSideMargin = 0

    private val cells = mutableMapOf<Pair<Int, Int>, Rect>()

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 11f * resources.displayMetrics.scaledDensity
        setShadowLayer(3f, 0f, 0f, Color.BLACK)
    }

    private val dragShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66FFFFFF
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
    }

    var itemClickListener: ((HomeSlot) -> Unit)? = null
    var itemLongClickListener: ((HomeSlot, View) -> Unit)? = null
    var emptyCellLongClickListener: ((View) -> Unit)? = null
    var pageChangeListener: ((Int) -> Unit)? = null
    var onSwipeOffsetChanged: ((Float) -> Unit)? = null

    private var draggingItem: HomeSlot? = null
    private var draggingCoords = Pair(-1f, -1f)

    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchDownTime = 0L
    private var longPressTriggered = false
    private val longPressTimeout = android.view.ViewConfiguration.getLongPressTimeout().toLong()
    private val longPressRunnable = Runnable {
        val slot = findSlotAt(touchDownX, touchDownY)
        longPressTriggered = true
        if (slot != null && slot.packageName.isNotEmpty()) {
            itemLongClickListener?.invoke(slot, this)
        } else {
            emptyCellLongClickListener?.invoke(this)
        }
    }

    private var swipeOffset = 0f
    private var targetSwipeOffset = 0f
    private var isSwiping = false
    private var isAnimatingSwipe = false
    private val tapThreshold = 12f * resources.displayMetrics.density

    private val swipeDamping = 1.0f
    private val swipeCompleteThresholdRatio = 0.2f
    private val swipeAnimDuration = 250L
    private val smoothingFactor = 0.25f

    private var swipeAnimator: ValueAnimator? = null

    init {
        setWillNotDraw(false)
        drawingArea.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addView(drawingArea)
    }

    private fun invalidateDrawing() {
        drawingArea.invalidate()
    }

    fun setGridSize(cols: Int, rows: Int) {
        if (columnCount != cols || rows != rowCount) {
            columnCount = cols
            rowCount = rows
            cells.clear()
            requestLayout()
            invalidateDrawing()
        }
    }

    fun setPageCount(count: Int) {
        pageCount = count.coerceAtLeast(1)
        if (currentPage >= pageCount) currentPage = pageCount - 1
        invalidateDrawing()
    }

    fun getPageCount(): Int = pageCount
    fun getCurrentPage(): Int = currentPage

    fun setItems(newItems: List<HomeSlot>) {
        items = newItems
        invalidateDrawing()
    }

    fun skipToPage(page: Int) {
        if (page in 0 until pageCount && page != currentPage) {
            currentPage = page
            swipeOffset = 0f
            targetSwipeOffset = 0f
            onSwipeOffsetChanged?.invoke(0f)
            invalidateDrawing()
        }
    }

    fun nextPage(): Boolean {
        if (currentPage < pageCount - 1) {
            currentPage++
            invalidateDrawing()
            return true
        }
        return false
    }

    fun prevPage(): Boolean {
        if (currentPage > 0) {
            currentPage--
            invalidateDrawing()
            return true
        }
        return false
    }

    fun setDraggingItem(slot: HomeSlot?) {
        draggingItem = slot
        invalidateDrawing()
    }

    fun setDraggingPackage(pkg: String?, page: Int) {
        draggingItem = if (pkg == null) {
            null
        } else {
            items.firstOrNull { it.packageName == pkg && it.page == page }
                ?: HomeSlot(pkg, -1, -1, page)
        }
        invalidateDrawing()
    }

    fun setDraggingCoords(x: Float, y: Float) {
        draggingCoords = Pair(x, y)
        invalidateDrawing()
    }

    fun clearDragging() {
        draggingItem = null
        draggingCoords = Pair(-1f, -1f)
        invalidateDrawing()
    }

    fun getItemAt(x: Float, y: Float): HomeSlot? = findSlotAt(x, y)

    fun getCellCoords(x: Float, y: Float): Pair<Int, Int>? {
        ensureCells()
        val col = (x / cellWidth).toInt()
        val row = (y / cellHeight).toInt()
        if (col in 0 until columnCount && row in 0 until rowCount) return Pair(row, col)
        return null
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (width > 0 && height > 0) ensureCells()
    }

    private fun ensureCells() {
        if (cells.isNotEmpty() && cellWidth > 0) return
        cellWidth = width / columnCount
        cellHeight = height / rowCount
        iconMargin = (cellWidth * 0.08f).toInt()
        iconSize = (62 * resources.displayMetrics.density).toInt()
        labelSideMargin = (cellWidth * 0.04f).toInt()
        cells.clear()
        for (r in 0 until rowCount) {
            for (c in 0 until columnCount) {
                cells[Pair(r, c)] = Rect(
                    c * cellWidth,
                    r * cellHeight,
                    (c + 1) * cellWidth,
                    (r + 1) * cellHeight
                )
            }
        }
    }

    private fun findSlotAt(x: Float, y: Float): HomeSlot? {
        if (cells.isEmpty()) return null
        val cell = getCellCoords(x, y) ?: return null
        return items.firstOrNull {
            it.page == currentPage && it.row == cell.first && it.col == cell.second
        } ?: HomeSlot("", cell.first, cell.second, currentPage)
    }

    private fun getIconBitmap(pkg: String): Bitmap? {
        if (iconSize <= 0) return null
        return AppCache.getIconBitmap(pkg, iconSize)
    }

    private val smoothingRunnable = object : Runnable {
        override fun run() {
            if (isAnimatingSwipe) return
            val diff = targetSwipeOffset - swipeOffset
            if (abs(diff) > 0.5f) {
                swipeOffset += diff * smoothingFactor
                onSwipeOffsetChanged?.invoke(swipeOffset)
                invalidateDrawing()
                drawingArea.postOnAnimation(this)
            } else {
                swipeOffset = targetSwipeOffset
                onSwipeOffsetChanged?.invoke(swipeOffset)
                invalidateDrawing()
            }
        }
    }

    private fun startSmoothing() {
        drawingArea.removeCallbacks(smoothingRunnable)
        drawingArea.postOnAnimation(smoothingRunnable)
    }

    fun drawInto(canvas: Canvas) {
        if (cells.isEmpty()) ensureCells()
        if (cells.isEmpty()) return

        drawPage(canvas, currentPage, swipeOffset)

        if (swipeOffset > 0 && currentPage > 0) {
            drawPage(canvas, currentPage - 1, swipeOffset - width)
        } else if (swipeOffset < 0 && currentPage < pageCount - 1) {
            drawPage(canvas, currentPage + 1, swipeOffset + width)
        }

        drawingItem(canvas)
    }

    private fun drawPage(canvas: Canvas, page: Int, offsetX: Float) {
        items.filter { it.page == page && it.packageName.isNotEmpty() }.forEach { slot ->
            if (draggingItem?.packageName == slot.packageName && draggingItem?.page == slot.page) {
                return@forEach
            }
            val cell = cells[Pair(slot.row, slot.col)] ?: return@forEach
            drawIconAt(canvas, slot, cell, offsetX)
        }
    }

    private fun drawIconAt(canvas: Canvas, slot: HomeSlot, cell: Rect, offsetX: Float) {
        val app = AppCache.get(slot.packageName) ?: return
        val bitmap = getIconBitmap(slot.packageName) ?: return

        val isCustom = PrefsSync.customIcons(context).containsKey(slot.packageName)
        val drawSize = if (isCustom) (iconSize * 0.85f).toInt() else iconSize
        val drawOffset = (iconSize - drawSize) / 2f

        val iconLeft = cell.left.toFloat() + (cell.width() - iconSize) / 2f + offsetX + drawOffset
        val iconTop = cell.top.toFloat() + iconMargin.toFloat() + drawOffset
        canvas.drawBitmap(bitmap, iconLeft, iconTop, null)

        if (app.label.isNotEmpty()) {
            val textWidth = cell.width() - 2 * labelSideMargin
            val layout = StaticLayout.Builder
                .obtain(app.label, 0, app.label.length, textPaint, textWidth)
                .setMaxLines(1)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .build()

            val labelTop = iconTop + drawSize + (iconMargin / 2)
            canvas.save()
            canvas.translate(cell.left.toFloat() + labelSideMargin + offsetX, labelTop)
            layout.draw(canvas)
            canvas.restore()
        }
    }

    private fun drawingItem(canvas: Canvas) {
        val dragged = draggingItem ?: return
        if (draggingCoords.first < 0f) return

        val cell = getCellCoords(draggingCoords.first, draggingCoords.second)
        if (cell != null) {
            val rect = cells[Pair(cell.first, cell.second)]
            if (rect != null) {
                canvas.drawCircle(
                    rect.exactCenterX(),
                    rect.exactCenterY(),
                    iconSize / 2f,
                    dragShadowPaint
                )
            }
        }

        val bitmap = getIconBitmap(dragged.packageName) ?: return
        val cx = draggingCoords.first - iconSize / 2f
        val cy = draggingCoords.second - iconSize / 1.2f
        canvas.drawBitmap(bitmap, cx, cy, null)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeAnimator?.cancel()
                isAnimatingSwipe = false
                touchDownX = event.x
                touchDownY = event.y
                touchDownTime = System.currentTimeMillis()
                longPressTriggered = false
                isSwiping = false
                swipeOffset = 0f
                targetSwipeOffset = 0f
                onSwipeOffsetChanged?.invoke(0f)
                postDelayed(longPressRunnable, longPressTimeout)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - touchDownX
                val dy = event.y - touchDownY
                if (!longPressTriggered && (abs(dx) > tapThreshold || abs(dy) > tapThreshold)) {
                    removeCallbacks(longPressRunnable)
                }

                if (draggingItem != null) {
                    setDraggingCoords(event.x, event.y)
                    return true
                }

                if (abs(dx) > abs(dy) && abs(dx) > 1f) {
                    val canSwipeLeft = dx < 0 && currentPage < pageCount - 1
                    val canSwipeRight = dx > 0 && currentPage > 0
                    if (canSwipeLeft || canSwipeRight) {
                        isSwiping = true
                        val damped = dx * swipeDamping
                        targetSwipeOffset = damped.coerceIn(-width.toFloat(), width.toFloat())
                        startSmoothing()
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)

                if (draggingItem != null) {
                    return true
                }

                if (isSwiping) {
                    val absDx = abs(targetSwipeOffset / swipeDamping)

                    if (absDx < tapThreshold) {
                        animateSwipeTo(0f) {
                            if (!longPressTriggered) {
                                val slot = findSlotAt(event.x, event.y)
                                if (slot != null && slot.packageName.isNotEmpty()) {
                                    itemClickListener?.invoke(slot)
                                }
                            }
                        }
                        isSwiping = false
                        return true
                    }

                    val completeThreshold = width * swipeCompleteThresholdRatio

                    when {
                        targetSwipeOffset < -completeThreshold && currentPage < pageCount - 1 -> {
                            animateSwipeTo(-width.toFloat()) {
                                currentPage++
                                swipeOffset = 0f
                                targetSwipeOffset = 0f
                                onSwipeOffsetChanged?.invoke(0f)
                                pageChangeListener?.invoke(currentPage)
                                invalidateDrawing()
                            }
                        }
                        targetSwipeOffset > completeThreshold && currentPage > 0 -> {
                            animateSwipeTo(width.toFloat()) {
                                currentPage--
                                swipeOffset = 0f
                                targetSwipeOffset = 0f
                                onSwipeOffsetChanged?.invoke(0f)
                                pageChangeListener?.invoke(currentPage)
                                invalidateDrawing()
                            }
                        }
                        else -> {
                            animateSwipeTo(0f) {
                                targetSwipeOffset = 0f
                            }
                        }
                    }

                    isSwiping = false
                    return true
                }

                if (!longPressTriggered && System.currentTimeMillis() - touchDownTime < longPressTimeout) {
                    val slot = findSlotAt(event.x, event.y)
                    if (slot != null && slot.packageName.isNotEmpty()) {
                        itemClickListener?.invoke(slot)
                    }
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun animateSwipeTo(target: Float, onEnd: () -> Unit) {
        swipeAnimator?.cancel()
        drawingArea.removeCallbacks(smoothingRunnable)
        isAnimatingSwipe = true

        val animator = ValueAnimator.ofFloat(swipeOffset, target)
        animator.duration = swipeAnimDuration
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener {
            swipeOffset = it.animatedValue as Float
            onSwipeOffsetChanged?.invoke(swipeOffset)
            invalidateDrawing()
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                isAnimatingSwipe = false
                onEnd()
            }
        })
        animator.start()
        swipeAnimator = animator
    }

    fun isPointInside(x: Float, y: Float): Boolean {
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        return x >= loc[0] && x <= loc[0] + width && y >= loc[1] && y <= loc[1] + height
    }
}
