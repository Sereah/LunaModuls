package com.lunacattus.nav3

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * Multi-tier navigator for suite mode:
 * - top-level tab keys switch the active tab (each keeps its own back stack)
 * - [RootRoute] is pushed onto the root overlay stack
 * - any other key is pushed onto the current tab stack
 */
class SuiteNavigator(
    private val navState: NavigationState,
    private val rootBackStack: NavBackStack<NavKey>,
) : Navigator {

    override fun navigate(route: NavKey) {
        Nav3Log.d(
            TAG,
            "navigate -> $route | current=${navState.currentRoute} " +
                "stack=${navState.currentBackStack.toList()}",
        )
        navState.performNavigation {
            when {
                route in navState.backStacks.keys -> navState.topLevelRoute = route
                route is RootRoute -> rootBackStack.add(route)
                else -> navState.backStacks[navState.topLevelRoute]?.add(route)
            }
        }
    }

    override fun goBack() {
        Nav3Log.d(
            TAG,
            "goBack | current=${navState.currentRoute} " +
                "stack=${navState.currentBackStack.toList()}",
        )
        navState.performNavigation {
            when {
                rootBackStack.size > 1 -> rootBackStack.removeLastOrNull()
                navState.currentRoute == navState.topLevelRoute ->
                    navState.topLevelRoute = navState.startRoute
                else -> navState.currentBackStack.removeLastOrNull()
            }
        }
    }

    companion object {
        const val TAG = "Nav3"
    }
}
