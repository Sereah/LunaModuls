package com.lunacattus.llm.api.classify

import kotlinx.coroutines.flow.Flow

/**
 * 文本分类引擎接口（NLU 意图识别）。
 *
 * 生命周期：
 * ```
 *   load() ──→ classify() ──→ close()
 *   ↑ 异步加载     ↑ 可重复调用    ↑ 释放全部资源
 * ```
 *
 * 典型用法：
 * ```kotlin
 * val engine = LlmSdk.createClassifier(context, config)
 * engine.load().collect { result ->
 *     result.onSuccess { /* 加载成功，可调用 classify */ }
 *     result.onFailure { /* 加载失败 */ }
 * }
 * val r = engine.classify("打开空调")
 * engine.close()
 * ```
 */
interface IClassifierEngine {

    /**
     * 异步加载模型，完成后发射一次 [Result.success] 或 [Result.failure]。
     * 重复调用会先释放旧资源再重新加载。
     */
    fun load(): Flow<Result<Unit>>

    /**
     * 对单条文本执行分类推理。
     *
     * 必须在 [load] 成功之后调用，否则抛出 [com.lunacattus.llm.api.exception.LlmException.ModelNotLoaded]。
     *
     * @param text 待分类的原始文本（中文）
     * @return 分类结果
     * @throws com.lunacattus.llm.api.exception.LlmException.ModelNotLoaded 模型未初始化
     * @throws com.lunacattus.llm.api.exception.LlmException.ClassifyFailed 推理过程异常
     */
    suspend fun classify(text: String): ClassifierResult

    /** 模型是否已加载就绪，可安全调用 [classify] */
    val isReady: Boolean

    /** 卸载模型、释放全部资源。调用后可重新 [load]。 */
    fun close()
}
