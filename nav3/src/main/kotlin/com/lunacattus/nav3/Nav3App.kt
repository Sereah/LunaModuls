package com.lunacattus.nav3

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay

/**
 * A full-screen single-stack navigation host.
 *
 * Use this when the app only needs simple push/pop between full-screen pages
 * (no bottom navigation bar, no separate tab stacks).
 *
 * ```kotlin
 * Nav3App(
 *     startRoute = HomeRoute,
 *     entries = {
 *         entryWithNavAndVm<HomeRoute, HomeViewModel> { route, nav, vm ->
 *             HomeScreen(vm, onNext = { nav.navigate(DetailRoute(it)) })
 *         }
 *         entryWithNavAndVm<DetailRoute, DetailViewModel> { route, nav, vm ->
 *             DetailScreen(route.id, vm, onBack = { nav.goBack() })
 *         }
 *     }
 * )
 * ```
 */
@Composable
fun Nav3App(
    startRoute: NavKey,
    modifier: Modifier = Modifier,
    transitionSpec: AnimatedContentTransitionScope<*>.() -> ContentTransform = { forwardHorizontal },
    popTransitionSpec: AnimatedContentTransitionScope<*>.() -> ContentTransform = { backwardHorizontal },
    predictivePopTransitionSpec: AnimatedContentTransitionScope<*>.(Int) -> ContentTransform = { popTransitionSpec() },
    entries: EntryProviderScope<NavKey>.() -> Unit,
) {
    val backStack = rememberNavBackStack(startRoute)
    val navigator = remember(backStack) { SimpleNavigator(backStack) }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        NavDisplay(
            entries = rememberDecoratedNavEntries(
                backStack = backStack,
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                entryProvider = entryProvider { entries() },
            ),
            onBack = { navigator.goBack() },
            transitionSpec = transitionSpec,
            popTransitionSpec = popTransitionSpec,
            predictivePopTransitionSpec = predictivePopTransitionSpec,
            modifier = modifier.fillMaxSize(),
        )
    }
}
