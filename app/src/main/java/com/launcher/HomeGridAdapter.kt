package com.simplylauncher

import android.util.Log
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
    private var draggingPackage: String? = null

    fun setCellHeight(height: Int) {
        if (cellHeight != height) {
            cellHeight = height
            if (itemCount > 0) notifyItemRangeChanged(0, itemCount)
        }
    }

    fun getCellHeightSafe(): Int = cellHeight

    fun setDraggingPackage(pkg: String?) {
        Log.d("GHOST_HOME", "setDraggingPackage($pkg) old=$draggingPackage size=${currentList.size}")
        if (draggingPackage == pkg) {
            Log.d("GHOST_HOME", "  skip (same)")
            return
        }
        draggingPackage = pkg
        // notifyDataSetChanged bypassa AsyncListDiffer e garantisce il rebind
        notifyDataSetChanged()
        Log.d("GHOST_HOME", "  notified")
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

                val hide = slot.packageName == draggingPackage
                itemView.alpha = if (hide) 0f else 1f
                if (hide) Log.d("GHOST_HOME", "bind HIDE ${slot.packageName}")

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
