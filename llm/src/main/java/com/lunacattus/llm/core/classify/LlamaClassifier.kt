package com.lunacattus.llm.core.classify

import com.lunacattus.llm.api.LlmLog
import com.lunacattus.llm.api.classify.ClassifierConfig
import com.lunacattus.llm.api.classify.ClassifierResult
import com.lunacattus.llm.api.classify.IClassifierEngine
import com.lunacattus.llm.api.exception.LlmException
import com.lunacattus.llm.jni.LlmNative
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

/**
 * llama.cpp JNI BERT 分类器实现。
 *
 * 通过 jni 调用 libllm.so 中的 Encoder 方法，
 * 底层使用 llama.cpp 的 BERT embedding + pooler + 分类头完成推理。
 */
internal class LlamaClassifier(
    private val config: ClassifierConfig.Llama,
) : IClassifierEngine {

    companion object {
        private const val TAG = "LlamaClassifier"
        private val Dispatcher = Dispatchers.IO.limitedParallelism(1)
    }

    @Volatile
    private var modelLoaded = false

    override fun load(): Flow<Result<Unit>> = flow {
        if (modelLoaded) {
            LlmLog.d(TAG, "model already loaded, skip.")
            emit(Result.success(Unit))
            return@flow
        }
        
        LlmLog.d(TAG, "load model: ${config.modelPath}")
        try {
            if (!File(config.modelPath).exists()) {
                emit(Result.failure(LlmException.ModelNotFound(config.modelPath)))
                return@flow
            }

            // 加载 BERT 模型
            val loadRet = LlmNative.nativeLoadBertModel(config.modelPath)
            if (loadRet != 0) {
                emit(
                    Result.failure(
                        LlmException.ModelLoadFailed("nativeLoadBertModel return $loadRet")
                    )
                )
                return@flow
            }

            // 准备推理上下文
            val prepRet = LlmNative.nativePrepareBert()
            if (prepRet != 0) {
                emit(
                    Result.failure(
                        LlmException.ModelPrepareFailed("nativePrepareBert return $prepRet")
                    )
                )
                return@flow
            }

            modelLoaded = true
            LlmLog.d(TAG, "model: ${config.modelPath} loaded!")
            emit(Result.success(Unit))
        } catch (e: Throwable) {
            LlmLog.e(TAG, "load fail", e)
            emit(Result.failure(LlmException.ModelLoadFailed(e.message ?: "unknown")))
        }
    }.flowOn(Dispatcher)

    override suspend fun classify(text: String): ClassifierResult {
        if (!modelLoaded) throw LlmException.ModelNotLoaded()

        LlmLog.d(TAG, "classify: $text")
        val startTime = System.currentTimeMillis()
        val idx = LlmNative.nativeClassifyText(text)
        val elapsed = System.currentTimeMillis() - startTime
        if (idx < 0) {
            throw LlmException.ClassifyFailed("nativeClassifyText return $idx")
        }
        LlmLog.d(TAG, "classify result: index=$idx, time consuming=${elapsed}ms")
        return ClassifierResult(labelIndex = idx, timeMs = elapsed)
    }

    override val isReady: Boolean get() = modelLoaded

    override fun close() {
        LlmLog.d(TAG, "close...")
        LlmNative.nativeUnloadEncoder()
        modelLoaded = false
    }
}
