package com.simplylauncher

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.drawable.toBitmap
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(
    private val onClick: (AppInfo) -> Unit,
    private val onLongPress: (AppInfo, View) -> Unit
) : ListAdapter<AppInfo, AppAdapter.AppViewHolder>(DiffCallback()) {

    private var draggingPackage: String? = null

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long =
        getItem(position).packageName.hashCode().toLong()

    fun setDraggingPackage(pkg: String?) {
        if (draggingPackage == pkg) return
        draggingPackage = pkg
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.appIcon)
        private val label: TextView = itemView.findViewById(R.id.appLabel)
        private val iconSizePx = (57 * itemView.context.resources.displayMetrics.density).toInt()

        fun bind(app: AppInfo) {
            // Scala il Drawable UNA volta alla dimensione finale dell'ImageView.
            // Evita che la GPU ridimensioni il bitmap ad ogni frame di scroll.
            val bitmap: Bitmap = app.icon.toBitmap(iconSizePx, iconSizePx, Bitmap.Config.ARGB_8888)
            icon.setImageBitmap(bitmap)
            label.text = app.label

            itemView.alpha = if (app.packageName == draggingPackage) 0f else 1f

            itemView.setOnClickListener { onClick(app) }
            itemView.setOnLongClickListener { view ->
                onLongPress(app, view)
                true
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<AppInfo>() {
        override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem.label == newItem.label &&
                   oldItem.packageName == newItem.packageName
        }
    }
}
