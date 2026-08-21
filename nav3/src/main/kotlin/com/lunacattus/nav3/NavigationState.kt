package com.lunacattus.nav3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.serialization.NavKeySerializer
import androidx.savedstate.compose.serialization.serializers.MutableStateSerializer

@Composable
fun rememberNavigationState(
    startRoute: NavKey,
    topLevelRoutesKey: Set<NavKey>,
): NavigationState {
    val topLevelRouteState = rememberSerializable(
        startRoute, topLevelRoutesKey,
        serializer = MutableStateSerializer(NavKeySerializer()),
    ) {
        mutableStateOf(startRoute)
    }

    val backStacks = topLevelRoutesKey.associateWith { key -> rememberNavBackStack(key) }

    return remember(startRoute, topLevelRoutesKey) {
        NavigationState(
            startRoute = startRoute,
            topLevelRouteState = topLevelRouteState,
            backStacks = backStacks,
        )
    }
}

class NavigationState(
    val startRoute: NavKey,
    topLevelRouteState: MutableState<NavKey>,
    val backStacks: Map<NavKey, NavBackStack<NavKey>>,
) {
    var topLevelRoute: NavKey by topLevelRouteState

    val stackInUse: List<NavKey>
        get() = if (topLevelRoute == startRoute) {
            listOf(startRoute)
        } else {
            listOf(startRoute, topLevelRoute)
        }

    var lastRoute by mutableStateOf<NavKey?>(null)
    var lastBackStack by mutableStateOf<NavBackStack<NavKey>?>(null)

    val currentRoute: NavKey by derivedStateOf {
        val activeTopRoute = stackInUse.last()
        backStacks[activeTopRoute]?.lastOrNull()
            ?: error("CurrentRoute is null!")
    }

    val currentBackStack: NavBackStack<NavKey> by derivedStateOf {
        backStacks[topLevelRoute] ?: error("CurrentBackStack is null!")
    }

    fun performNavigation(action: () -> Unit) {
        lastRoute = currentRoute
        lastBackStack = currentBackStack
        action()
    }
}

@Composable
fun NavigationState.toEntries(
    entryProvider: (NavKey) -> NavEntry<NavKey>,
): SnapshotStateList<NavEntry<NavKey>> {
    val decorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
        rememberViewModelStoreNavEntryDecorator(),
    )

    val topRouteToEntries = backStacks.mapValues { (_, stack) ->
        rememberDecoratedNavEntries(
            backStack = stack,
            entryDecorators = decorators,
            entryProvider = entryProvider,
        )
    }

    return stackInUse
        .flatMap { topRoute -> topRouteToEntries[topRoute] ?: emptyList() }
        .toMutableStateList()
}
