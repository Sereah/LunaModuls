package com.lunacattus.nav3

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

interface Navigator {
    fun navigate(route: NavKey)
    fun goBack()
}

val LocalNavigator = androidx.compose.runtime.staticCompositionLocalOf<Navigator> {
    error("Navigator not provided")
}
