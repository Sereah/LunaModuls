# Luna Logger

独立日志库，支持 Logcat 输出、文件持久化（自动轮转 & 空间清理）、Box 边框格式化打印、自动 TAG 推断。

## 添加依赖

```kotlin
implementation("com.lunacattus.android:logger:1.1.0")
```

---

## 快速开始

```kotlin
// 初始化（建议在 Application.onCreate 中）
Logger.initBaseTag("MyApp", showThreadName = true, isDebug = BuildConfig.DEBUG)

// 使用 — tag 为空时自动从调用栈推断类名
Logger.d("MainActivity", "hello world")
Logger.i(message = "默认使用 baseTag")
Logger.w("onClick", "用户点击过快")
Logger.e("Network", "请求失败", IOException("timeout"))
```

### 文件日志

```kotlin
Logger.initFileLogger(
    dir = File(context.filesDir, "logs"),
    maxFileSizeMb = 10,   // 单个文件上限
    maxDirSizeMb = 50,    // 目录总大小上限
)
// 自动轮转：log_2026-07-02.txt → log_2026-07-02_1.txt → ...
// 超配额时自动删除最旧文件至 70%
```

### Box 打印

适用于多行信息、JSON、配置等需要视觉分隔的场景：

```kotlin
Logger.box("Login", """
    用户: admin
    角色: 管理员
    时间: 2026-07-02 12:00:00
""".trimIndent())
// 输出:
// ═══════════════════════════════
//  Login
// ═══════════════════════════════
//  用户: admin
//  角色: 管理员
//  时间: 2026-07-02 12:00:00
// ═══════════════════════════════
```

---

## API 总览

### 初始化

| 方法 | 说明 |
|------|------|
| `initBaseTag(tag, showThreadName, isDebug)` | 设置默认 TAG、线程名显示开关；`isDebug = false` 时禁用所有 Logcat 输出 |
| `initFileLogger(dir, maxFileSizeMb, maxDirSizeMb)` | 启用文件日志；自动轮转 + 空间清理 |

### 日志输出

| 方法 | 说明 |
|------|------|
| `d(tag, message)` | DEBUG 级别；tag 为空时自动推断调用类名 |
| `i(tag, message)` | INFO 级别 |
| `w(tag, message)` | WARN 级别 |
| `e(tag, message, throwable)` | ERROR 级别，可选异常堆栈 |
| `box(tag, message, borderChar)` | 边框格式化打印，适合多行内容 |
| `getArray(bytes, offset, limit)` | ByteArray → 十六进制字符串 (`"0A 1F FF"`) |

### 特性

- **日志分块**：单条日志超过 2000 字符自动拆分
- **线程安全**：内部通过单线程 executor 串行写入
- **文件命名**：`log_yyyy-MM-dd.txt`，超限追加 `_N` 索引
- **自动清理**：目录总大小超 `maxDirSizeMb` 时删除最旧文件直到降至 70%

---

## Maven 坐标

| group | artifact | version |
|-------|----------|---------|
| `com.lunacattus.android` | `logger` | `1.1.0` |
