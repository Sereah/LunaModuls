package com.lunacattus.nav3

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * Single-stack navigator used in simple mode.
 */
open class SimpleNavigator(
    private val backStack: NavBackStack<NavKey>,
) : Navigator {
    override fun navigate(route: NavKey) {
        Nav3Log.d(TAG, "navigate -> $route | stack=${backStack.toList()}")
        backStack.add(route)
    }

    override fun goBack() {
        Nav3Log.d(TAG, "goBack | stack=${backStack.toList()}")
        if (backStack.size > 1) backStack.removeLastOrNull()
    }

    companion object {
        const val TAG = "Nav3"
    }
}