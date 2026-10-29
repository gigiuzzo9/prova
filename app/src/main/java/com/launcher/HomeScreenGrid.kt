private fun drawIconAt(canvas: Canvas, slot: HomeSlot, cell: Rect, offsetX: Float) {
    val app = AppCache.get(slot.packageName) ?: return
    val bitmap = getIconBitmap(slot.packageName) ?: return

    // Riduci l'icona custom per allinearla visivamente a quelle di sistema
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
