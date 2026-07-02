# Luna Common

核心工具库，提供协程安全包装、常用扩展函数、Asset 操作等基础能力。

## 添加依赖

```kotlin
implementation("com.lunacattus.android:common:1.0.2")
```

---

## CommonLog — 日志回调配置

通过 `setLogger` 注入自定义日志处理，默认回退到 `android.util.Log`。

```kotlin
CommonLog.setLogger(
    debug = { tag, msg -> MyLogger.d(tag, msg) },
    error = { tag, msg, tr -> MyLogger.e(tag, msg, tr) }
)
```

---

## SafeCoroutine — 安全的协程启动

防止未捕获异常导致协程作用域崩溃，异常自动记录到 CommonLog。

```kotlin
val scope = CoroutineScope(Dispatchers.Default)

// 安全启动，异常被捕获并记录，不会崩溃
scope.launchSafe("fetchData") {
    // 业务逻辑
}

// 安全 async，异常仍会传播但被记录
scope.asyncSafe<String>("loadConfig") {
    // 返回结果
}

// 安全取消（仅作用域活跃时才取消）
scope.cancelSafe()
```

### API

| 方法 | 说明 |
|------|------|
| `CoroutineScope.launchSafe(name, context, block)` | 启动协程，内部捕获异常防止作用域崩溃；返回 `Job` |
| `CoroutineScope.asyncSafe(name, context, block)` | 启动 async 协程，异常被记录但仍会传播；返回 `Deferred<T>` |
| `CoroutineScope.cancelSafe()` | 仅当作用域活跃时取消，已取消则不操作 |

---

## 扩展函数

### Context 扩展

| 方法 | 说明 |
|------|------|
| `Context.isSystemSignature()` | 判断应用是否使用系统签名（与 `"android"` 的签名比较） |
| `Context.getAndroidId()` | 从 `Settings.Secure` 获取 Android ID |

### Long 扩展（时间戳 & 文件大小）

```kotlin
val timeStr = 1698765432000L.toDateTimeString()                    // "2023-11-01 12:34:56"
val smartStr = 1698765432000L.toSmartDateString("今天", "昨天", "前天") // 智能日期
val sizeStr = 1048576L.toFileSizeString()                           // "1.00 MB"
val durationStr = 3661000L.toDuration()                             // "01:01:01.000"
val shortDur = 2500L.toDurationStringShort()                        // "2.50s"
```

| 方法 | 说明 |
|------|------|
| `Long.toDateTimeString(pattern, timeZone, locale)` | 毫秒时间戳 → 自定义日期字符串 |
| `Long.toSmartDateString(today, yesterday, dayBefore, ...)` | 毫秒时间戳 → "今天/昨天/前天/日期" |
| `Long.toFileSizeString()` | 字节数 → "1.23 MB" |
| `Long.toDuration()` | 毫秒 → "HH:mm:ss.SSS" |
| `Long.toDurationStringShort()` | 毫秒 → "2.50s" / "3m 45s" |

### String 扩展

| 方法 | 说明 |
|------|------|
| `String.maskCenter(headLen, tailLen)` | 保留头尾，中间替换为 `"..."`，如 `"138****5678"` |

### View 扩展

| 方法 | 说明 |
|------|------|
| `Float.dpToPx(context)` | dp → px 转换 |
| `View.setOnClickListenerWithDebounce(debounceTime, action)` | 带防抖的点击监听（默认 500ms） |

---

## AssetUtils

将 assets 中的文件夹递归复制到应用 files 目录。

```kotlin
val destPath = AssetUtils.copyToFiles(context, "models")
// → "/data/data/.../files/models/"
```

---

## Maven 坐标

| group | artifact | version |
|-------|----------|---------|
| `com.lunacattus.android` | `common` | `1.0.2` |


