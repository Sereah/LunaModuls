# Luna Navigation3

基于 AndroidX Navigation 3 的 Jetpack Compose 导航库，提供两种导航宿主：

- **`Nav3App`** — 单栈全屏导航，适用于简单的页面 push/pop
- **`Nav3SuiteApp`** — 自适应多层级导航，底部导航栏 / 导航轨道 / 抽屉随窗口尺寸自动切换，每个顶层路由维护独立返回栈，并支持全屏 Root 覆盖层

## 添加依赖

```kotlin
implementation("com.lunacattus.android:nav3:1.0.0")
```

### 前置依赖

nav3 的公共 API 暴露了 Navigation 3 与 Hilt 相关类型，宿主工程需自行声明以下依赖：

```kotlin
// build.gradle.kts (app 模块)
plugins {
    alias(libs.plugins.kotlin.serialization)   // 用于 @Serializable 路由
}

dependencies {
    implementation("androidx.navigation3:navigation3-runtime:1.0.1")
    implementation("androidx.navigation3:navigation3-ui:1.0.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-navigation3:2.10.0")
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")
    implementation("androidx.savedstate:savedstate-compose:1.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.3")
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite:1.5.0-alpha15")
}
```

> `entryWithVm` / `entryWithNavAndVm` 内部调用 `hiltViewModel()`，路由需使用 `@Serializable` 声明以支持状态保存与恢复。

---

## 快速开始

### 单栈导航 — `Nav3App`

```kotlin
Nav3App(
    startRoute = HomeRoute,
    entries = {
        entryWithNavAndVm<HomeRoute, HomeViewModel> { route, nav, vm ->
            HomeScreen(vm, onNext = { nav.navigate(DetailRoute(it)) })
        }
        entryWithNavAndVm<DetailRoute, DetailViewModel> { route, nav, vm ->
            DetailScreen(route.id, vm, onBack = { nav.goBack() })
        }
    },
)
```

### 多层级导航 — `Nav3SuiteApp`

```kotlin
Nav3SuiteApp(
    startRoute = HomeRoute,
    topLevelRoutes = mapOf(
        HomeRoute to TopLevelItem(Icons.Default.Home, "首页"),
        SettingsRoute to TopLevelItem(Icons.Default.Settings, "设置"),
    ),
    mainEntries = {
        entryWithNavAndVm<HomeRoute, HomeViewModel> { _, nav, vm ->
            HomeScreen(vm) { nav.navigate(DetailRoute) }
        }
        entryWithNav<DetailRoute> { _, nav -> DetailScreen { nav.goBack() } }
        entryWithVm<SettingsRoute, SettingsViewModel> { _, vm -> SettingsScreen(vm) }
    },
    rootEntries = {
        entry<PlayerRoute> { PlayerScreen() }   // 全屏覆盖层（如播放页）
    },
)
```

**关键点：**
- 顶部导航样式（底部导航栏 / 导航轨道 / 抽屉）根据窗口尺寸**自动切换**
- 每个顶层路由（`topLevelRoutes` 的 key）维护**独立返回栈**
- 实现 `RootRoute` 的路由会被压入根覆盖栈，全屏覆盖在 tab 内容之上
- 通过 `LocalInnerPadding.current` 获取 Scaffold 的内边距，用于内容布局

---

## 路由定义

```kotlin
// 顶层路由：实现 MainRoute，可用 titleResId 指定标题栏文案
@Serializable
data object HomeRoute : MainRoute

// 普通页面路由：实现 MainRoute（当前 tab 栈内 push）
@Serializable
data class DetailRoute(val id: Int) : MainRoute {
    override val titleResId: Int get() = R.string.detail
}

// 全屏覆盖路由：实现 RootRoute（压入根覆盖栈）
@Serializable
data object PlayerRoute : RootRoute
```

| 类型 | 说明 |
|------|------|
| `RootRoute : NavKey` | 根覆盖栈路由（全屏覆盖在 tab 内容之上） |
| `MainRoute : NavKey` | 主栈路由，提供 `titleResId`（标题栏文案资源 ID，0 表示无标题） |
| `Main : NavKey` | 内置占位路由，仅在 suite 模式内部使用 |
| `TopLevelItem(icon, label)` | 顶层导航项（图标 + 文案） |

---

## 入口扩展函数

在 `entries` / `mainEntries` / `rootEntries` 作用域内使用，简化路由 → 页面的注册：

| 扩展函数 | 参数 | 说明 |
|---------|------|------|
| `entry<R>` | `content: @Composable (R) -> Unit` | 基础入口 |
| `entryWithVm<R, VM>` | `content: @Composable (R, VM) -> Unit` | 附带 `hiltViewModel()` 注入的 ViewModel |
| `entryWithNav<R>` | `content: @Composable (R, Navigator) -> Unit` | 附带 `Navigator` |
| `entryWithNavAndVm<R, VM>` | `content: @Composable (R, Navigator, VM) -> Unit` | 附带 `Navigator` + ViewModel |
| `entryWithNavAndVm<R, VM>` | `viewModelProvider` + `content` | 自定义 ViewModel 提供方式 |

---

## Navigator

通过 `LocalNavigator.current` 或 `entryWithNav*` 获取，提供导航操作：

```kotlin
interface Navigator {
    fun navigate(route: NavKey)
    fun goBack()
}
```

| 成员 | 说明 |
|------|------|
| `navigate(route)` | 压栈导航；suite 模式下顶层路由切换 tab，普通路由压入当前 tab 栈 |
| `goBack()` | 出栈返回 |

---

## 转场动画

`Transitions.kt` 提供预置 `ContentTransform`：

| 转场 | 说明 |
|------|------|
| `forwardHorizontal` / `backwardHorizontal` | 水平 push / pop（默认） |
| `forwardVertical` / `backwardVertical` | 垂直 push / pop（suite 模式导航轨道/抽屉下 tab 切换） |
| `fadeThrough` | 淡入淡出（根覆盖页） |
| `scaleEnter` / `scaleExit` | 缩放 + 淡入淡出（根覆盖层默认） |

可通过 `Nav3App` / `Nav3SuiteApp` 的 `transitionSpec`、`popTransitionSpec`、`predictivePopTransitionSpec` 参数自定义转场。

---

## 日志回调配置

通过 `Nav3Log.setLogger` 注入自定义日志处理：

```kotlin
Nav3Log.setLogger(
    debug = { tag, msg -> MyLogger.d(tag, msg) },
    error = { tag, msg, tr -> MyLogger.e(tag, msg, tr) }
)
```

不调用 `setLogger` 时，默认通过 `android.util.Log` 输出。建议在 `Application.onCreate` 中完成配置。

---

## Maven 坐标

| group | artifact | version |
|-------|----------|---------|
| `com.lunacattus.android` | `nav3` | `1.0.0` |
