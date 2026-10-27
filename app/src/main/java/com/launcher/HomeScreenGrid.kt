package com.simplylauncher

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
    private var widgetView: FrameLayout? = null

    private var cellWidth = 0
    private var cellHeight = 0
    private var iconSize = 0
    private var iconMargin = 0
    private var labelSideMargin = 0

    private val cells = mutableMapOf<Pair<Int, Int>, Rect>()
    private val gridCenters = mutableListOf<Pair<Int, Int>>()

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

    private var swipeStartX = 0f
    private var swipeOffset = 0f
    private var isSwiping = false
    private val swipeThreshold = 20f * resources.displayMetrics.density

    init {
        setWillNotDraw(false)
        drawingArea.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        addView(drawingArea)
    }

    fun attachWidgetContainer(container: FrameLayout) {
        widgetView = container
    }

    fun setGridSize(cols: Int, rows: Int) {
        if (columnCount != cols || rows != rowCount) {
            columnCount = cols
            rowCount = rows
            cells.clear()
            gridCenters.clear()
            requestLayout()
            invalidate()
        }
    }

    fun setPageCount(count: Int) {
        pageCount = count.coerceAtLeast(1)
        if (currentPage >= pageCount) currentPage = pageCount - 1
        invalidate()
    }

    fun getPageCount(): Int = pageCount
    fun getCurrentPage(): Int = currentPage

    fun setItems(newItems: List<HomeSlot>) {
        items = newItems
        invalidate()
    }

    fun skipToPage(page: Int) {
        if (page in 0 until pageCount && page != currentPage) {
            currentPage = page
            swipeOffset = 0f
            pageChangeListener?.invoke(page)
            invalidate()
        }
    }

    fun nextPage(): Boolean {
        if (currentPage < pageCount - 1) {
            currentPage++
            pageChangeListener?.invoke(currentPage)
            invalidate()
            return true
        }
        return false
    }

    fun prevPage(): Boolean {
        if (currentPage > 0) {
            currentPage--
            pageChangeListener?.invoke(currentPage)
            invalidate()
            return true
        }
        return false
    }

    fun setDraggingItem(slot: HomeSlot?) {
        draggingItem = slot
        invalidate()
    }

    fun setDraggingCoords(x: Float, y: Float) {
        draggingCoords = Pair(x, y)
        invalidate()
    }

    fun clearDragging() {
        draggingItem = null
        draggingCoords = Pair(-1f, -1f)
        invalidate()
    }

    fun getItemAt(x: Float, y: Float): HomeSlot? = findSlotAt(x, y)

    fun getCellCoords(x: Float, y: Float): Pair<Int, Int>? {
        ensureCells()
        val pageX = x + currentPage * width.toFloat() + swipeOffset
        val col = (pageX / cellWidth).toInt()
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
        iconSize = min(cellWidth, cellHeight) - 2 * iconMargin
        labelSideMargin = (cellWidth * 0.04f).toInt()
        cells.clear()
        gridCenters.clear()
        for (r in 0 until rowCount) {
            for (c in 0 until columnCount) {
                val rect = Rect(
                    c * cellWidth,
                    r * cellHeight,
                    (c + 1) * cellWidth,
                    (r + 1) * cellHeight
                )
                cells[Pair(r, c)] = rect
                gridCenters.add(Pair(rect.centerX(), rect.centerY()))
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

    // --- Drawing ---

    fun drawInto(canvas: Canvas) {
        if (cells.isEmpty()) {
            ensureCells()
        }
        if (cells.isEmpty()) return

        val pageOffset = -(currentPage * width) - swipeOffset.toInt()

        canvas.save()
        canvas.translate(pageOffset.toFloat(), 0f)
        drawPage(canvas, currentPage)

        if (swipeOffset > 0 && currentPage > 0) {
            canvas.save()
            canvas.translate(width.toFloat(), 0f)
            drawPage(canvas, currentPage - 1)
            canvas.restore()
        } else if (swipeOffset < 0 && currentPage < pageCount - 1) {
            canvas.save()
            canvas.translate(-width.toFloat(), 0f)
            drawPage(canvas, currentPage + 1)
            canvas.restore()
        }
        canvas.restore()

        drawingItem(canvas)
    }

    private fun drawPage(canvas: Canvas, page: Int) {
        items.filter { it.page == page && it.packageName.isNotEmpty() }.forEach { slot ->
            if (draggingItem?.packageName == slot.packageName && draggingItem?.page == slot.page) {
                return@forEach
            }
            val cell = cells[Pair(slot.row, slot.col)] ?: return@forEach
            drawIconAt(canvas, slot, cell)
        }
    }

    private fun drawIconAt(canvas: Canvas, slot: HomeSlot, cell: Rect) {
        val app = AppCache.get(slot.packageName) ?: return
        val bitmap = getIconBitmap(slot.packageName) ?: return

        val iconLeft = cell.left + (cell.width() - iconSize) / 2
        val iconTop = cell.top + iconMargin
        canvas.drawBitmap(bitmap, iconLeft.toFloat(), iconTop.toFloat(), null)

        if (app.label.isNotEmpty()) {
            val textWidth = cell.width() - 2 * labelSideMargin
            val layout = StaticLayout.Builder
                .obtain(app.label, 0, app.label.length, textPaint, textWidth)
                .setMaxLines(1)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .build()

            val labelTop = iconTop + iconSize + (iconMargin / 2)
            canvas.save()
            canvas.translate(cell.left.toFloat() + labelSideMargin, labelTop.toFloat())
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

    // --- Touch ---

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x
                touchDownY = event.y
                touchDownTime = System.currentTimeMillis()
                longPressTriggered = false
                isSwiping = false
                swipeStartX = event.x
                swipeOffset = 0f
                postDelayed(longPressRunnable, longPressTimeout)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - touchDownX
                val dy = event.y - touchDownY
                if (!longPressTriggered && (abs(dx) > swipeThreshold || abs(dy) > swipeThreshold)) {
                    removeCallbacks(longPressRunnable)
                }

                if (draggingItem != null) {
                    setDraggingCoords(event.x, event.y)
                    return true
                }

                if (abs(dx) > abs(dy) && abs(dx) > swipeThreshold) {
                    isSwiping = true
                    swipeOffset = dx
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)

                if (draggingItem != null) {
                    return true
                }

                if (isSwiping) {
                    if (swipeOffset < -swipeThreshold * 3 && currentPage < pageCount - 1) {
                        currentPage++
                        pageChangeListener?.invoke(currentPage)
                    } else if (swipeOffset > swipeThreshold * 3 && currentPage > 0) {
                        currentPage--
                        pageChangeListener?.invoke(currentPage)
                    }
                    swipeOffset = 0f
                    isSwiping = false
                    invalidate()
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

    fun isPointInside(x: Float, y: Float): Boolean {
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        return x >= loc[0] && x <= loc[0] + width && y >= loc[1] && y <= loc[1] + height
    }
}
