package com.lunacattus.common.utils

import android.content.Context
import android.util.TypedValue
import android.view.View

fun Float.dpToPx(context: Context): Float {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this,
        context.resources.displayMetrics
    )
}

inline fun View.setOnClickListenerWithDebounce(
    debounceTime: Long = 500,
    crossinline action: (View) -> Unit
) {
    var lastClickTime = 0L
    setOnClickListener {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime >= debounceTime) {
            lastClickTime = currentTime
            action(it)
        }
    }
}