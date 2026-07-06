package com.lunacattus.llm.core.generate

import com.lunacattus.llm.api.LlmLog
import com.lunacattus.llm.api.exception.LlmException
import com.lunacattus.llm.api.generate.GenerateConfig
import com.lunacattus.llm.api.generate.IGenerateEngine
import com.lunacattus.llm.jni.LlmNative
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

/**
 * llama.cpp JNI 对话引擎实现。
 */
internal class LlamaChatEngine(
    private val config: GenerateConfig,
) : IGenerateEngine {

    companion object {
        private const val TAG = "LlamaChatEngine"
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

            val loadRet = LlmNative.nativeLoadLlmModel(config.modelPath)
            if (loadRet != 0) {
                emit(
                    Result.failure(
                        LlmException.ModelLoadFailed("nativeLoadLlmModel return $loadRet")
                    )
                )
                return@flow
            }

            val prepRet = LlmNative.nativePrepareLlm()
            if (prepRet != 0) {
                emit(
                    Result.failure(
                        LlmException.ModelPrepareFailed("nativePrepareLlm return $prepRet")
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

    override suspend fun setSystemPrompt(prompt: String, enableThinking: Boolean) {
        if (!modelLoaded) throw LlmException.ModelNotLoaded()
        LlmLog.d(TAG, "setSystemPrompt (enableThinking=$enableThinking)")
        val ret = LlmNative.nativeProcessSystemPrompt(prompt, enableThinking)
        if (ret != 0) {
            LlmLog.e(TAG, "setSystemPrompt return $ret")
        }
    }

    override fun generate(
        prompt: String,
        maxTokens: Int,
        enableThinking: Boolean,
    ): Flow<String> = flow {
        if (!modelLoaded) throw LlmException.ModelNotLoaded()

        LlmLog.d(TAG, "generate: '$prompt' maxTokens=$maxTokens")

        val ret = LlmNative.nativeProcessUserPrompt(prompt, maxTokens, enableThinking)
        if (ret != 0) {
            throw LlmException.GenerateFailed("nativeProcessUserPrompt return $ret")
        }

        // 流式采样
        while (true) {
            val token = LlmNative.nativeGenerateNextToken()
            if (!token.isNullOrEmpty()) {
                emit(token)
            } else {
                break // EOS / 达到 maxTokens / 出错
            }
        }
        LlmLog.d(TAG, "generate completed")
    }.flowOn(Dispatcher)

    override val isReady: Boolean get() = modelLoaded

    override fun close() {
        LlmLog.d(TAG, "close...")
        LlmNative.nativeUnloadLlm()
        modelLoaded = false
    }
}
