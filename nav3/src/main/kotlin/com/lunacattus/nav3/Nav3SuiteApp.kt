package com.lunacattus.nav3

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItemColors
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay

val LocalInnerPadding = staticCompositionLocalOf<PaddingValues> {
    error("PaddingValues not provided")
}

/**
 * Navigation host with adaptive bottom bar / navigation rail / drawer.
 *
 * Each top-level route keeps its own back stack. Use [RootRoute] for full-screen
 * overlays (e.g. a player) that sit above the tabbed content.
 *
 * ```kotlin
 * Nav3SuiteApp(
 *     startRoute = HomeRoute,
 *     topLevelRoutes = mapOf(
 *         HomeRoute to TopLevelItem(Icons.Default.Home, "Home"),
 *         SettingsRoute to TopLevelItem(Icons.Default.Settings, "Settings"),
 *     ),
 *     mainEntries = {
 *         entryWithNavAndVm<HomeRoute, HomeViewModel> { _, nav, vm ->
 *             HomeScreen(vm) { nav.navigate(DetailRoute) }
 *         }
 *         entryWithNav<DetailRoute> { _, nav -> DetailScreen { nav.goBack() } }
 *         entryWithVm<SettingsRoute, SettingsViewModel> { _, vm -> SettingsScreen(vm) }
 *     },
 *     rootEntries = {
 *         entry<PlayerRoute> { PlayerScreen() }
 *     },
 * )
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Nav3SuiteApp(
    startRoute: NavKey,
    topLevelRoutes: Map<NavKey, TopLevelItem>,
    mainEntries: EntryProviderScope<NavKey>.() -> Unit,
    modifier: Modifier = Modifier,
    rootEntries: (EntryProviderScope<NavKey>.() -> Unit)? = null,
    topBar: @Composable (NavigationState, Navigator) -> Unit = { navState, navigator ->
        DefaultTopBar(navState, navigator)
    },
    navigationSuiteItemColors: NavigationSuiteItemColors? = null,
    mainTransitionSpec: Nav3SuiteTransitionScope.() -> ContentTransform = { defaultMainTransition() },
    mainPopTransitionSpec: Nav3SuiteTransitionScope.() -> ContentTransform = { defaultMainPopTransition() },
    rootTransitionSpec: AnimatedContentTransitionScope<*>.() -> ContentTransform = { scaleEnter },
    rootPopTransitionSpec: AnimatedContentTransitionScope<*>.() -> ContentTransform = { scaleExit },
    rootPredictivePopTransitionSpec: AnimatedContentTransitionScope<*>.(Int) -> ContentTransform = { rootPopTransitionSpec() },
) {
    val rootBackStack = rememberNavBackStack(Main)
    val navState = rememberNavigationState(startRoute, topLevelRoutes.keys)
    val navigator = remember(rootBackStack, navState) {
        SuiteNavigator(navState, rootBackStack)
    }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        NavDisplay(
            entries = rememberDecoratedNavEntries(
                backStack = rootBackStack,
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                entryProvider = entryProvider {
                    entry<Main> {
                        SuiteScaffold(
                            navState = navState,
                            navigator = navigator,
                            topLevelRoutes = topLevelRoutes,
                            topBar = topBar,
                            navigationSuiteItemColors = navigationSuiteItemColors,
                            mainEntries = mainEntries,
                            transitionSpec = mainTransitionSpec,
                            popTransitionSpec = mainPopTransitionSpec,
                            modifier = modifier,
                        )
                    }
                    rootEntries?.invoke(this)
                },
            ),
            onBack = { navigator.goBack() },
            transitionSpec = rootTransitionSpec,
            popTransitionSpec = rootPopTransitionSpec,
            predictivePopTransitionSpec = rootPredictivePopTransitionSpec,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuiteScaffold(
    navState: NavigationState,
    navigator: Navigator,
    topLevelRoutes: Map<NavKey, TopLevelItem>,
    topBar: @Composable (NavigationState, Navigator) -> Unit,
    navigationSuiteItemColors: NavigationSuiteItemColors?,
    mainEntries: EntryProviderScope<NavKey>.() -> Unit,
    transitionSpec: Nav3SuiteTransitionScope.() -> ContentTransform,
    popTransitionSpec: Nav3SuiteTransitionScope.() -> ContentTransform,
    modifier: Modifier,
) {
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val layoutType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(adaptiveInfo)
    val isBottomBar = layoutType == NavigationSuiteType.NavigationBar

    val scope = Nav3SuiteTransitionScope(navState, isBottomBar)

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            topLevelRoutes.forEach { (key, tab) ->
                item(
                    selected = navState.topLevelRoute == key,
                    onClick = { navigator.navigate(key) },
                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                    label = { Text(tab.label) },
                    colors = navigationSuiteItemColors,
                )
            }
        },
    ) {
        Scaffold(
            topBar = { topBar(navState, navigator) },
            modifier = modifier.fillMaxSize(),
        ) { padding ->
            CompositionLocalProvider(LocalInnerPadding provides padding) {
                NavDisplay(
                    entries = navState.toEntries(entryProvider { mainEntries() }),
                    onBack = { navigator.goBack() },
                    transitionSpec = { with(scope) { transitionSpec() } },
                    popTransitionSpec = { with(scope) { popTransitionSpec() } },
                    predictivePopTransitionSpec = { with(scope) { popTransitionSpec() } },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultTopBar(navState: NavigationState, navigator: Navigator) {
    val currentRoute = navState.currentRoute as? MainRoute
    val title = currentRoute?.titleResId?.takeIf { it != 0 }?.let {
        stringResource(it)
    }.orEmpty()
    TopAppBar(
        title = { Text(title) },
        colors = TopAppBarDefaults.topAppBarColors().copy(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
        navigationIcon = {
            if (currentRoute != null && navState.currentRoute !in navState.backStacks.keys) {
                IconButton(onClick = { navigator.goBack() }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
            }
        },
    )
}

class Nav3SuiteTransitionScope(
    val navState: NavigationState,
    val isBottomBar: Boolean,
) {
    val isTopLevelToTopLevel: Boolean
        get() = navState.lastRoute != null &&
            navState.lastRoute in navState.backStacks.keys &&
            navState.currentRoute in navState.backStacks.keys

    val isSameTopLevelStack: Boolean
        get() = navState.lastBackStack?.first() == navState.currentBackStack.first()

    val isMoveToRight: Boolean
        get() {
            val keys = navState.backStacks.keys
            val fromIndex = keys.indexOf(navState.lastBackStack?.first())
            val toIndex = keys.indexOf(navState.currentBackStack.first())
            return toIndex > fromIndex
        }

    fun defaultMainTransition(): ContentTransform = when {
        isTopLevelToTopLevel -> topLevelTransform(isBottomBar, isMoveToRight)
        isSameTopLevelStack -> forwardHorizontal
        else -> topLevelTransform(isBottomBar, isMoveToRight)
    }

    fun defaultMainPopTransition(): ContentTransform {
        val lastRoute = navState.lastRoute
        val useHorizontal = isBottomBar || lastRoute !in navState.backStacks.keys
        return if (useHorizontal) backwardHorizontal else backwardVertical
    }

    private fun topLevelTransform(isBottomBar: Boolean, forward: Boolean): ContentTransform = when {
        isBottomBar && forward -> forwardHorizontal
        isBottomBar && !forward -> backwardHorizontal
        !isBottomBar && forward -> forwardVertical
        else -> backwardVertical
    }
}
