package com.simplylauncher

import android.content.Context
import androidx.recyclerview.widget.GridLayoutManager

class MyGridLayoutManager(context: Context, spanCount: Int) : GridLayoutManager(context, spanCount) {
    override fun supportsPredictiveItemAnimations() = false
}
