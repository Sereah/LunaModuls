# Luna Network

HTTP + WebSocket 网络库。提供基于 `Result<String>` 的 HTTP 客户端，以及带指数退避自动重连的 WebSocket 客户端。

## 添加依赖

```kotlin
implementation("com.lunacattus.android:network:1.0.0")
```

---

## NetworkLog — 日志回调配置

可注入自定义日志处理：

```kotlin
NetworkLog.setLogger(
    debug = { tag, msg -> MyLogger.d(tag, msg) },
    error = { tag, msg, tr -> MyLogger.e(tag, msg, tr) }
)
```

---

## HTTP 客户端

### 接口

```kotlin
interface IHttpClient {
    suspend fun post(url: String, body: String, headers: Map<String, String> = emptyMap()): Result<String>
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): Result<String>
}
```

### 使用

```kotlin
val client = HttpManager()

// GET
val result = client.get("https://api.example.com/users")
result.onSuccess { json -> println("成功: $json") }
     .onFailure { e -> println("失败: ${e.message}") }

// POST
val postResult = client.post(
    url = "https://api.example.com/login",
    body = """{"username":"admin","password":"***"}""",
    headers = mapOf("Authorization" to "Bearer xxx")
)
```

### 配置超时

```kotlin
client.updateConfig(HttpConfig(
    connectTimeoutMs = 5_000L,
    readTimeoutMs = 15_000L,
    writeTimeoutMs = 10_000L,
))
```

### API

| 类型 | 说明 |
|------|------|
| `IHttpClient` | HTTP 客户端接口（`post` / `get` 返回 `Result<String>`） |
| `HttpManager()` | OkHttp 实现，创建于 `Dispatchers.IO` |
| `HttpConfig` | 超时配置数据类（connect / read / write） |
| `HttpException(code, message)` | HTTP 状态码异常 |

---

## WebSocket 客户端

### 基本使用

```kotlin
val ws = WebSocketManager()

scope.launch {
    // 收集连接状态
    ws.state.collect { state ->
        when (state) {
            is WebSocketState.Connected -> println("已连接")
            is WebSocketState.Reconnecting -> println("重连中 #${state.attempt}")
            is WebSocketState.Failed -> println("连接失败: ${state.throwable}")
            else -> {}
        }
    }
}

scope.launch {
    // 收集事件
    ws.events.collect { event ->
        when (event) {
            is WebSocketEvent.MessageReceived -> {
                when (val msg = event.message) {
                    is WebSocketEvent.Message.Text -> println("收到: ${msg.data}")
                    is WebSocketEvent.Message.Binary -> println("收到二进制: ${msg.data.size} bytes")
                }
            }
            is WebSocketEvent.Error -> println("错误: ${event.throwable}")
            else -> {}
        }
    }
}

// 连接
ws.connect("wss://example.com/ws")

// 发送消息
ws.send("ping")
ws.send(byteArrayOf(0x01, 0x02))

// 断开
ws.disconnect()
```

### 重连策略

```kotlin
ws.connect("wss://example.com/ws", config = WebSocketConfig(
    reconnectEnabled = true,
    maxReconnectAttempts = 10,
    reconnectBaseDelayMs = 1_000L,      // 初始 1s
    reconnectMaxDelayMs = 30_000L,      // 最长 30s
    reconnectDelayMultiplier = 2.0f,    // 指数退避
    pingIntervalMs = 15_000L,           // 心跳间隔
))
// 延迟序列：1s → 2s → 4s → 8s → 16s → 30s → 30s → ...
```

### API

| 类型 | 说明 |
|------|------|
| `IWebSocketClient` | WebSocket 接口：`connect` / `disconnect` / `send` |
| `WebSocketManager()` | OkHttp WebSocket 实现，支持自动重连 |
| `WebSocketConfig` | 重连策略 + 超时配置数据类 |
| `WebSocketState` | 连接状态 sealed interface（Idle / Connecting / Connected / Reconnecting / Disconnected / Failed） |
| `WebSocketEvent` | 事件 sealed interface（Connected / Disconnected / MessageReceived / Error）|

---

## RequestIdGenerator — ULID 请求 ID 生成器

基于 ULID 标准（Crockford Base32），线程安全，容忍最多 10 秒时钟漂移。

```kotlin
val gen = RequestIdGenerator(deviceCode = "AX01")

gen.generate()          // "AX01_01JN6Z3XZY0000000000000000"
gen.generateRaw()       // "01JN6Z3XZY0000000000000000"
gen.fromTimestamp(now)  // 从指定时间戳生成
```

---

## Maven 坐标

| group | artifact | version |
|-------|----------|---------|
| `com.lunacattus.android` | `network` | `1.0.0` |
