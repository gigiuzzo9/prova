package com.simplylauncher

import android.graphics.drawable.Drawable

data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable
)

data class HomeItem(
    val packageName: String,
    val position: Int
)
