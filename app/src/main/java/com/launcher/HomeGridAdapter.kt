package com.simplylauncher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class HomeGridAdapter(
    private val onClick: (HomeSlot) -> Unit,
    private val onLongPress: (HomeSlot, View) -> Unit,
    private val onEmptyCellLongPress: (View) -> Unit
) : ListAdapter<HomeSlot, HomeGridAdapter.SlotViewHolder>(DiffCallback()) {

    private var cellHeight: Int = 0

    // Package dell'app che è attualmente in drag: la cella corrispondente
    // viene nascosta (alpha 0) così l'overlay è l'unica cosa visibile.
    private var draggingPackage: String? = null

    fun setCellHeight(height: Int) {
        if (cellHeight != height) {
            cellHeight = height
            if (itemCount > 0) notifyItemRangeChanged(0, itemCount)
        }
    }

    fun getCellHeightSafe(): Int = cellHeight

    fun setDraggingPackage(pkg: String?) {
        if (draggingPackage == pkg) return
        val old = draggingPackage
        draggingPackage = pkg
        // Aggiorna solo le due celle coinvolte
        currentList.forEachIndexed { index, slot ->
            if (slot.packageName == old || slot.packageName == pkg) {
                notifyItemChanged(index)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlotViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return SlotViewHolder(view)
    }

    override fun onBindViewHolder(holder: SlotViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun swap(from: Int, to: Int): Boolean {
        val current = currentList.toMutableList()
        if (from !in current.indices || to !in current.indices) return false
        val a = current[from]
        val b = current[to]
        current[from] = a.copy(row = b.row, col = b.col, page = b.page)
        current[to] = b.copy(row = a.row, col = a.col, page = a.page)
        submitList(current)
        return true
    }

    inner class SlotViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.appIcon)
        private val label: TextView = itemView.findViewById(R.id.appLabel)

        fun bind(slot: HomeSlot) {
            val ctx = itemView.context
            val density = ctx.resources.displayMetrics.density

            if (cellHeight > 0) {
                val lp = itemView.layoutParams
                if (lp.height != cellHeight) {
                    lp.height = cellHeight
                    itemView.layoutParams = lp
                }
            }

            val iconSizePx = (57 * density).toInt()
            icon.layoutParams = icon.layoutParams.apply {
                width = iconSizePx
                height = iconSizePx
            }

            val cellHeightDp = if (cellHeight > 0) (cellHeight / density).toInt() else 80
            if (cellHeightDp < 70) {
                label.visibility = View.GONE
            } else {
                label.visibility = View.VISIBLE
                label.textSize = 11f
            }

            val app = AppCache.get(ctx, slot.packageName)
            if (app != null) {
                icon.setImageDrawable(app.icon)
                label.text = app.label
                itemView.visibility = View.VISIBLE

                // Ghost durante drag: nasconde la cella se è quella in drag
                itemView.alpha = if (slot.packageName == draggingPackage) 0f else 1f

                itemView.setOnClickListener { onClick(slot) }
                itemView.setOnLongClickListener { view ->
                    onLongPress(slot, view)
                    true
                }
            } else {
                icon.setImageDrawable(null)
                label.text = ""
                itemView.alpha = 1f
                itemView.visibility = View.VISIBLE
                itemView.setOnClickListener(null)
                itemView.setOnLongClickListener { view ->
                    onEmptyCellLongPress(view)
                    true
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<HomeSlot>() {
        override fun areItemsTheSame(oldItem: HomeSlot, newItem: HomeSlot): Boolean {
            return oldItem.row == newItem.row &&
                    oldItem.col == newItem.col &&
                    oldItem.page == newItem.page
        }

        override fun areContentsTheSame(oldItem: HomeSlot, newItem: HomeSlot): Boolean {
            return oldItem == newItem
        }
    }

    object AppCache {
        private val map = mutableMapOf<String, AppInfo>()
        fun putAll(apps: List<AppInfo>) {
            map.clear()
            apps.forEach { map[it.packageName] = it }
        }
        fun get(context: android.content.Context, pkg: String): AppInfo? = map[pkg]
    }
}
