# LunaModules

Android 多模块基础库，发布至 `com.lunacattus.android`。各模块独立可发布，模块间**无相互依赖**。

---

## 模块一览

| 模块 | 版本 | 说明                                                           |
|------|------|--------------------------------------------------------------|
| [common](common/README.md) | 1.0.2 | 核心工具库：协程安全包装、扩展函数（时间/文件/View/String）、Asset 操作                |
| [logger](logger/README.md) | 1.1.0 | 独立日志库：Logcat + 文件持久化 + 自动轮转 + Box 打印 + TAG 自动推断              |
| [network](network/README.md) | 1.0.0 | 网络库：基于 Result 的 HTTP 客户端 + 带指数退避重连的 WebSocket 客户端 + ULID 生成器 |
| [statemachine](statemachine/README.md) | 1.0.0 | 状态机：协程版平级状态机 & 经典 Handler 版层级状态机                             |
| [screen-adaptation-plugin](screen-adaptation-plugin/README.md) | 1.0.0 | Gradle 插件：根据设计图参数自动生成多屏幕适配 `dimens.xml`                      |
| [llm](llm/README.md) | 1.0.1 | 端侧 LLM 推理 SDK：文本分类（ONNX/llama.cpp）+ 对话生成（llama.cpp Chat）            |
| [framework-jar](framework-jar/README.md) | 1.0.0 | Gradle 插件：下载不同版本的 AOSP framework JAR 用于 ksp/compileOnly      |

---

## 快速添加

### 基础库（common / logger / network / statemachine / llm）

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.lunacattus.android:common:1.0.2")
    implementation("com.lunacattus.android:logger:1.1.0")
    implementation("com.lunacattus.android:network:1.0.0")
    implementation("com.lunacattus.android:statemachine:1.0.0")
    implementation("com.lunacattus.android:llm:1.0.1")
}
```

### Gradle 插件（screen-adaptation-plugin / framework-jar）

```toml
# gradle/libs.versions.toml
[plugins]
screen-adaptation = { id = "com.lunacattus.android.screen-adaptation", version = "1.0.0" }
framework-jar = { id = "com.lunacattus.android.framework.jar", version = "1.0.0" }
```

```kotlin
// build.gradle.kts (app 或 library 模块)
plugins {
    alias(libs.plugins.screen.adaptation)
    alias(libs.plugins.framework.jar)
}

screenAdaptation {
    designWidthPx = 1080
    designHeightPx = 1920
    designDpi = 320
    target(1280, 720, 240)
}

frameworkJar {
    version = "13"
    custom = false
}
```

---

## 模块详解

📖 **[common — 核心工具库](common/README.md)**

- `SafeCoroutine`：`launchSafe` / `asyncSafe` / `cancelSafe`，阻止未捕获异常导致作用域崩溃
- 扩展函数：时间格式化、文件大小、View 防抖、字符串掩码、dp-px 转换、设备 ID、系统签名检测
- `AssetUtils`：asset 文件夹复制到 files 目录
- `CommonLog.setLogger`：可插拔日志回调

📖 **[logger — 日志库](logger/README.md)**

- 多级别日志：`d` / `i` / `w` / `e`，空 TAG 自动推断调用类名
- `initFileLogger`：文件日志 + 按文件大小轮转 + 按目录总大小清理
- `box`：边框格式化打印，适合多行内容
- 线程安全，单条超 2000 字符自动分块

📖 **[network — 网络库](network/README.md)**

- **HTTP**：`IHttpClient` 接口 + `HttpManager`（OkHttp 实现），超时可配置
- **WebSocket**：`IWebSocketClient` + `WebSocketManager`，指数退避自动重连，`StateFlow`/`SharedFlow` 双向可观察
- **请求 ID 生成**：`RequestIdGenerator`，基于 ULID，线程安全，设备代码前缀

📖 **[statemachine — 状态机](statemachine/README.md)**

- **SampleStateMachine**（推荐）：Kotlin Coroutines Channel 驱动，平级状态，`StateFlow` 可观察，类型安全
- **StateMachine**（经典）：Handler/Message 驱动，层级状态（子→父冒泡），自有线程，内置日志记录系统

📖 **[screen-adaptation-plugin — 屏幕适配插件](screen-adaptation-plugin/README.md)**

- 根据设计图宽高/DPI 生成多屏幕 `dimens.xml`
- 支持多目标屏幕、ADB 自动探测、CSV 配置文件批量导入
- 自定义 dp/sp 命名格式

📖 **[llm — 端侧 LLM 推理 SDK](llm/README.md)**

- **分类引擎**：ONNX Runtime（CPU / NNAPI / 高通 QNN NPU）或 llama.cpp 后端，支持 NLU 意图识别
- **生成引擎**：llama.cpp Chat，GGUF 模型，Flow 流式 token 输出
- **自定义日志**：`LlmLog.setLogger` 可插拔日志回调
- **异常体系**：`LlmException` 统一异常，覆盖模型加载、推理、NPU 不可用等场景
- ⚠️ 宿主 App 需配置 `packaging.jniLibs.useLegacyPackaging = true`

📖 **[framework-jar — Framework JAR 插件](framework-jar/README.md)**

- Gradle 插件：通过 DSL 拉取指定版本 AOSP framework JAR，自动配置 compileOnly/ksp/bootstrapClasspath
- JAR 发布：文件放入 `frameworkLibs/` → 自动发布单个 artifact + bundle POM，无需代码改动
