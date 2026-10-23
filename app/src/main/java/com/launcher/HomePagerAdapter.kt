package com.simplylauncher

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

class HomePagerAdapter(
    private val viewPager: ViewPager2,
    private val cols: Int,
    private val rows: Int,
    private val onSlotClick: (HomeSlot) -> Unit,
    private val onSlotLongPress: (HomeSlot, View) -> Unit,
    private val onEmptyCellLongPress: (View) -> Unit,
    private val onPageCreated: (page: Int, recycler: RecyclerView) -> Unit
) : RecyclerView.Adapter<HomePagerAdapter.PageViewHolder>() {

    private val pages = mutableListOf<MutableList<HomeSlot>>()
    private val recyclers = mutableListOf<RecyclerView>()
    private val widgetContainers = mutableListOf<FrameLayout>()
    private var cellHeight: Int = 0

    fun setPages(newPages: List<List<HomeSlot>>) {
        pages.clear()
        pages.addAll(newPages.map { it.toMutableList() })
        notifyDataSetChanged()
    }

    fun getPageItems(page: Int): List<HomeSlot> = pages.getOrNull(page) ?: emptyList()

    fun setCellHeight(height: Int) {
        if (cellHeight != height) {
            cellHeight = height
            recyclers.forEach { rv ->
                (rv.adapter as? HomeGridAdapter)?.setCellHeight(height)
            }
        }
    }

    fun getRecycler(page: Int): RecyclerView? = recyclers.getOrNull(page)

    fun getWidgetContainer(page: Int): FrameLayout? = widgetContainers.getOrNull(page)

    override fun getItemCount(): Int = pages.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val container = FrameLayout(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val rv = RecyclerView(parent.context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            layoutManager = GridLayoutManager(parent.context, cols)
            clipToPadding = false
            setPadding(0, (16 * resources.displayMetrics.density).toInt(), 0, 0)
        }

        val widgetContainer = FrameLayout(parent.context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        container.addView(rv)
        container.addView(widgetContainer)

        return PageViewHolder(container, rv, widgetContainer)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val items = pages[position]

        if (holder.recycler.adapter == null) {
            val adapter = HomeGridAdapter(
                onClick = { slot -> onSlotClick(slot) },
                onLongPress = { slot, view -> onSlotLongPress(slot, view) },
                onEmptyCellLongPress = { view -> onEmptyCellLongPress(view) }
            )
            holder.recycler.adapter = adapter
            if (cellHeight > 0) adapter.setCellHeight(cellHeight)
        }

        (holder.recycler.adapter as HomeGridAdapter).submitList(items)

        while (recyclers.size <= position) recyclers.add(holder.recycler)
        while (widgetContainers.size <= position) widgetContainers.add(holder.widgetContainer)
        if (position < recyclers.size) {
            recyclers[position] = holder.recycler
        }
        if (position < widgetContainers.size) {
            widgetContainers[position] = holder.widgetContainer
        }

        onPageCreated(position, holder.recycler)
    }

    override fun onViewRecycled(holder: PageViewHolder) {
        super.onViewRecycled(holder)
        // non fare nulla: le liste vengono aggiornate in onBindViewHolder
    }

    inner class PageViewHolder(
        val container: FrameLayout,
        val recycler: RecyclerView,
        val widgetContainer: FrameLayout
    ) : RecyclerView.ViewHolder(container)
}
