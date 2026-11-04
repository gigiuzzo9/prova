package com.simplylauncher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy

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
            // Carica il bitmap alla risoluzione originale (o più alta) e lascia che Glide lo ridimensioni
            val highResBitmap = AppCache.getIconBitmap(app.packageName, iconSizePx * 3)
            
            if (highResBitmap != null) {
                Glide.with(itemView.context)
                    .load(highResBitmap)
                    .override(iconSizePx, iconSizePx)
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                    .into(icon)
            } else {
                icon.setImageDrawable(null)
            }

            val isCustom = PrefsSync.customIcons(itemView.context).containsKey(app.packageName)
            if (isCustom) {
                val pad = (iconSizePx * 0.05f).toInt()
                icon.setPadding(pad, pad, pad, pad)
            } else {
                icon.setPadding(0, 0, 0, 0)
            }

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
                   oldItem.packageName == newItem.packageName &&
                   oldItem.icon === newItem.icon
        }
    }
}
