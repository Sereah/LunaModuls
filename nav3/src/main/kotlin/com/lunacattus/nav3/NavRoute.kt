package com.lunacattus.nav3

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Marks a route placed on the root/overlay stack (full-screen coverage above the main UI). */
interface RootRoute : NavKey

/** Marks a route placed inside the current top-level tab stack. */
interface MainRoute : NavKey {
    /** String resource ID for the top bar title; 0 means no title. */
    val titleResId: Int get() = 0
}

/**
 * Placeholder route placed on the root stack that hosts the inner NavDisplay
 * (the multi-tab main content). Only used in suite mode.
 */
@Serializable
data object Main : NavKey

data class TopLevelItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
)

inline fun <reified R : NavKey, reified VM : ViewModel>
        EntryProviderScope<NavKey>.entryWithVm(
    metadata: Map<String, Any> = emptyMap(),
    crossinline content: @Composable (R, VM) -> Unit,
) {
    entry<R>(metadata = metadata) { content(it, hiltViewModel()) }
}

inline fun <reified R : NavKey>
        EntryProviderScope<NavKey>.entryWithNav(
    metadata: Map<String, Any> = emptyMap(),
    crossinline content: @Composable (R, Navigator) -> Unit,
) {
    entry<R>(metadata = metadata) { content(it, LocalNavigator.current) }
}

inline fun <reified R : NavKey, reified VM : ViewModel>
        EntryProviderScope<NavKey>.entryWithNavAndVm(
    metadata: Map<String, Any> = emptyMap(),
    crossinline content: @Composable (R, Navigator, VM) -> Unit,
) {
    entry<R>(metadata = metadata) {
        content(it, LocalNavigator.current, hiltViewModel())
    }
}

inline fun <reified R : NavKey, reified VM : ViewModel>
        EntryProviderScope<NavKey>.entryWithNavAndVm(
    metadata: Map<String, Any> = emptyMap(),
    crossinline viewModelProvider: @Composable (R) -> VM,
    crossinline content: @Composable (R, Navigator, VM) -> Unit,
) {
    entry<R>(metadata = metadata) { key ->
        content(key, LocalNavigator.current, viewModelProvider(key))
    }
}
