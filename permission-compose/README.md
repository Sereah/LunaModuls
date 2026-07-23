# Luna Permission Compose

Jetpack Compose 权限请求库，统一处理标准权限弹窗、特殊权限跳转、理由弹窗和设置引导的完整生命周期。

## 添加依赖

```kotlin
dependencies {
    implementation("com.lunacattus.android:permission-compose:1.0.0")
}
```

---

## 快速开始

```kotlin
@Composable
fun MyScreen() {
    val recordPermission = rememberPermissionState(
        Manifest.permission.RECORD_AUDIO,
        rationaleConfig = RationaleDialogConfig(
            title = "需要麦克风权限",
            message = "录音功能需要使用麦克风，请在下一步授权。"
        ),
        settingsConfig = SettingsDialogConfig(
            title = "需要麦克风权限",
            message = "录音功能需要使用麦克风，请前往系统设置中开启。"
        )
    )

    Button(onClick = {
        recordPermission.request { allGranted, denied ->
            if (allGranted) startRecording()
        }
    }) {
        Text("开始录音")
    }
}
```

**关键点：**
- `rememberPermissionState` 内部已集成弹窗宿主（`PermissionDialogHost`），**无需手动渲染弹窗**
- 标准权限 → 系统弹窗；特殊权限（悬浮窗等）→ 自动跳转设置弹窗
- 用户拒绝一次 → 展示理由弹窗；永久拒绝 → 展示设置弹窗

---

## 配置弹窗文案

### 理由弹窗（用户拒绝过一次）

```kotlin
RationaleDialogConfig(
    title = "需要相机权限",
    message = "扫描二维码需要使用相机。",
    confirmText = "继续授权",
    dismissText = "取消",
    cancelable = false        // false: 不显示取消按钮，不允许外部点击关闭
)
```

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `title` | `String` | `"Permission Required"` | 弹窗标题 |
| `message` | `String` | `"This permission is needed..."` | 弹窗正文 |
| `confirmText` | `String` | `"Continue"` | 确认按钮文案 |
| `dismissText` | `String` | `"Cancel"` | 取消按钮文案 |
| `cancelable` | `Boolean` | `false` | 是否可取消（外部点击 / 返回键） |

### 设置弹窗（权限被永久拒绝）

```kotlin
SettingsDialogConfig(
    title = "需要相机权限",
    message = "相机权限已被永久拒绝，请前往系统设置中开启。",
    confirmText = "前往设置",
    dismissText = "取消",
    cancelable = true
)
```

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `title` | `String` | `"Permission Required"` | 弹窗标题 |
| `message` | `String` | `"This permission has been permanently denied..."` | 弹窗正文 |
| `confirmText` | `String` | `"Go to Settings"` | 确认按钮文案 |
| `dismissText` | `String` | `"Cancel"` | 取消按钮文案 |
| `cancelable` | `Boolean` | `true` | 是否可取消（外部点击 / 返回键） |

---

## 自定义弹窗

传入 `rationaleDialog` 或 `settingsDialog` 参数替换默认弹窗：

```kotlin
val permission = rememberPermissionState(
    Manifest.permission.CAMERA,
    rationaleDialog = { state, dismiss, confirm ->
        // 自定义理由弹窗
        MyCustomRationaleDialog(
            state = state,
            onDismiss = dismiss,
            onConfirm = confirm
        )
    },
    settingsDialog = { state, dismiss, confirm ->
        // 自定义设置弹窗
        MyCustomSettingsDialog(
            state = state,
            onDismiss = dismiss,
            onConfirm = confirm
        )
    }
)
```

自定义弹窗签名（`CustomPermissionDialog`）：

```kotlin
typealias CustomPermissionDialog = @Composable (PermissionState, () -> Unit, () -> Unit) -> Unit
```

三个参数依次为：当前权限状态、关闭回调、确认回调。

---

## 权限名称本地化

通过 `nameProvider` 参数注入本地化名称：

```kotlin
val permission = rememberPermissionState(
    Manifest.permission.CAMERA,
    nameProvider = { perm ->
        when (perm) {
            Manifest.permission.CAMERA -> stringResource(R.string.perm_camera)
            Manifest.permission.RECORD_AUDIO -> stringResource(R.string.perm_mic)
            else -> permissionDisplayName(perm) // 回退到英文默认
        }
    }
)
```

| 类型 | 说明 |
|------|------|
| `PermissionNameProvider` | `@Composable (String) -> String`，可在 Composable 上下文中调用 `stringResource` |
| `permissionDisplayName()` | 默认英文名称映射，覆盖所有常见 Runtime 权限 |

---

## 权限图标

默认弹窗中会根据权限显示对应 Material Icons 图标：

```kotlin
val PermissionIconMap: Map<String, ImageVector>
```

覆盖所有常见权限（相机 → `CameraAlt`、麦克风 → `Mic`、位置 → `LocationOn` 等），可直接作为参考或扩展。

---

## 日志回调配置

通过 `PermissionLog.setLogger` 注入自定义日志处理：

```kotlin
PermissionLog.setLogger(
    debug = { tag, msg -> MyLogger.d(tag, msg) },
    error = { tag, msg, tr -> MyLogger.e(tag, msg, tr) }
)
```

不调用 `setLogger` 时，默认通过 `android.util.Log` 输出。建议在 `Application.onCreate` 中完成配置。

---

## PermissionState API

`rememberPermissionState` 返回的 `PermissionState` 提供以下公开 API：

| 属性 / 方法 | 类型 | 说明 |
|------------|------|------|
| `allGranted` | `Boolean`（Compose 可观察） | 当前是否全部权限已授权 |
| `permissions` | `List<String>` | 请求的权限列表 |
| `rationaleConfig` | `RationaleDialogConfig` | 理由弹窗配置 |
| `settingsConfig` | `SettingsDialogConfig` | 设置弹窗配置 |
| `request(onResult)` | 方法 | 发起权限请求，结果通过回调返回 |

### request 回调

```kotlin
permissionState.request { allGranted, deniedList ->
    if (allGranted) {
        // 全部授权，执行业务逻辑
    } else {
        // 部分权限被拒绝，deniedList 列出被拒绝的权限
    }
}
```

| 回调参数 | 类型 | 说明 |
|---------|------|------|
| `allGranted` | `Boolean` | 是否全部权限已授权 |
| `deniedList` | `List<String>` | 被拒绝的权限列表（全部授权时为空） |

---

## 权限类型说明

| 类型 | 示例 | 处理方式 |
|------|------|---------|
| **标准权限** | CAMERA、RECORD_AUDIO、ACCESS_FINE_LOCATION 等 | 系统 `requestPermissions` 弹窗 |
| **特殊权限** | SYSTEM_ALERT_WINDOW、WRITE_SETTINGS、MANAGE_EXTERNAL_STORAGE | 系统弹窗无效，引导跳转系统设置页手动开启 |
| **同伴权限** | READ_MEDIA_VISUAL_USER_SELECTED + IMAGES + VIDEO | 组内任一授权即视为整组成功（Android 14+ "选择部分照片"） |

---

## Maven 坐标

| group | artifact | version |
|-------|----------|---------|
| `com.lunacattus.android` | `permission-compose` | `1.0.0` |
