# Luna LLM

端侧 LLM 推理 SDK，集成 llama.cpp 与 ONNX Runtime，提供文本分类（NLU 意图识别）与对话生成两类引擎。

## 添加依赖

```kotlin
implementation("com.lunacattus.android:llm:1.0.1")
```

传递依赖会自动引入：
- `com.microsoft.onnxruntime:onnxruntime-android-qnn:1.27.0`
- `com.microsoft.onnxruntime:onnxruntime-extensions-android:0.13.0`

---

## ⚠️ 重要：宿主 App 必须配置 `useLegacyPackaging`

ONNX Runtime 在初始化时会**扫描文件系统**加载自定义算子库（`libortextensions.so`），而现代 AGP 默认不提取 `.so` 文件到文件系统（`extractNativeLibs="false"`），导致模型加载失败：

```
Error: com.microsoft.extensions:BertTokenizer(-1) is not a registered function/op
```

**在宿主 App 的 `build.gradle.kts` 中加上以下配置：**

```kotlin
android {
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}
```

> **注意**：该配置必须写在 **application 模块**（`:app`）中。写在 library 模块的 AAR 里不会传递给最终 APK，无法生效。

---

## LlmSdk — 入口

所有引擎通过 `LlmSdk` 创建，使用前需初始化一次。

```kotlin
// Application.onCreate() 或首次使用前调用
LlmSdk.initialize(context)

// 创建分类器（NLU 意图识别）
val classifier: IClassifierEngine = LlmSdk.createClassifier(context, config)

// 创建对话引擎（llama.cpp Chat）
val generator: IGenerateEngine = LlmSdk.createGenerateEngine(config)

// 应用退出时释放全局资源
LlmSdk.shutDown()
```

---

## 分类引擎 — IClassifierEngine

文本分类（NLU 意图识别），支持 ONNX Runtime 和 llama.cpp 两种后端。

### 配置

```kotlin
// ── ONNX 后端（CPU）──
val cpuCfg = ClassifierConfig.Onnx(
    modelPath = "/data/models/nlu_mobile.onnx",
    numThreads = 4,
    backend = OnnxBackend.CPU,
)

// ── ONNX 后端（高通 QNN NPU）──
val qnnCfg = ClassifierConfig.Onnx(
    modelPath = "/data/models/nlu_mobile.onnx",
    backend = OnnxBackend.QNN,
    qnnConfig = QnnConfig(
        performanceMode = "burst",
        enableFp16Precision = true,
    ),
)

// ── llama.cpp 后端 ──
val llamaCfg = ClassifierConfig.Llama(
    modelPath = "/data/models/nlu.gguf",
    numThreads = 4,
)
```

### 生命周期

```kotlin
val engine = LlmSdk.createClassifier(context, cpuCfg)

engine.load().collect { result ->
    result.onSuccess { /* 加载成功，可调用 classify */ }
    result.onFailure { /* 加载失败（ModelNotFound / ModelLoadFailed） */ }
}

// 单条推理（必须在 load 成功后调用）
val result: ClassifierResult = engine.classify("打开空调")
// result.labelIndex  → 预测类别索引
// result.confidence  → 置信度 0.0~1.0（ONNX 后端提供，llama.cpp 后端为 null）
// result.timeMs      → 推理耗时（毫秒）

// 卸载模型，释放资源；之后可重新 load()
engine.close()
```

### ONNX 推理后端

| 后端 | 说明 | 适用条件 |
|------|------|---------|
| `OnnxBackend.CPU` | 纯 CPU 推理，兼容性最好 | 所有设备 |
| `OnnxBackend.NNAPI` | Android Neural Networks API（已废弃） | Android ≤14 且芯片支持 DSP |
| `OnnxBackend.QNN` | 高通 AI Engine Direct，直连 Hexagon NPU | 骁龙 8 Gen 1+，需 QNN SDK 库 |

### ClassifierResult

| 字段 | 类型 | 说明 |
|------|------|------|
| `labelIndex` | `Int` | 预测类别索引（0 起始），由训练时的 label_mapping 定义 |
| `timeMs` | `Long` | 推理耗时（毫秒），包含 JNI 调用 / ONNX session.run |
| `confidence` | `Float?` | 置信度 0.0～1.0，llama.cpp 后端为 null |

---

## 生成引擎 — IGenerateEngine

基于 llama.cpp 的对话生成，支持 GGUF 模型格式。

### 配置

```kotlin
val config = GenerateConfig(
    modelPath = "/data/models/chat.gguf",
    numThreads = 4,
)
```

### 用法

```kotlin
val engine = LlmSdk.createGenerateEngine(config)

// 异步加载
engine.load().collect { result ->
    result.onSuccess { /* 加载完成 */ }
    result.onFailure { /* 加载失败 */ }
}

// 设置系统提示词
engine.setSystemPrompt("你是一个有用的助手", enableThinking = false)

// 生成回复（Flow 流式输出 token）
engine.generate("你好，请介绍一下自己", maxTokens = 256, enableThinking = true)
    .collect { token -> print(token) }

// 检查就绪状态
if (engine.isReady) { /* ... */ }

// 释放资源
engine.close()
```

### API

| 方法 | 说明 |
|------|------|
| `load(): Flow<Result<Unit>>` | 异步加载模型，完成后发射一次 Result |
| `setSystemPrompt(prompt, enableThinking)` | 设置系统提示词 |
| `generate(prompt, maxTokens, enableThinking)` | 流式生成，逐 token 发射 |
| `isReady` | 模型是否加载就绪 |
| `close()` | 卸载模型、释放全部资源 |

---

## LlmLog — 日志回调

与 `CommonLog` 一致的日志注入模式，默认回退到 `android.util.Log`。

```kotlin
LlmLog.setLogger(
    debug = { tag, msg -> MyLogger.d(tag, msg) },
    error = { tag, msg, tr -> MyLogger.e(tag, msg, tr) },
)
```

---

## 异常体系

所有异常均为 `LlmException` 的子类：

| 异常 | 触发条件 |
|------|---------|
| `ModelNotFound` | 模型文件不存在 |
| `ModelLoadFailed` | 模型加载过程失败 |
| `ModelPrepareFailed` | 创建推理上下文 / 采样器失败 |
| `ModelNotLoaded` | `load()` 未成功就调用 `classify()` / `generate()` |
| `ClassifyFailed` | 分类推理过程异常 |
| `GenerateFailed` | 对话生成过程异常 |
| `NpuNotAvailable` | 指定的 NPU 后端在当前设备不可用 |

---

## Maven 坐标

| group | artifact | version |
|-------|----------|---------|
| `com.lunacattus.android` | `llm` | `1.0.1` |
