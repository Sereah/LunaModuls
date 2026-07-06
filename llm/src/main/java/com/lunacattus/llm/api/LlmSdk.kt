package com.lunacattus.llm.api

import android.content.Context
import com.lunacattus.llm.api.classify.ClassifierConfig
import com.lunacattus.llm.api.classify.IClassifierEngine
import com.lunacattus.llm.api.generate.GenerateConfig
import com.lunacattus.llm.api.generate.IGenerateEngine
import com.lunacattus.llm.core.classify.LlamaClassifier
import com.lunacattus.llm.core.classify.OnnxClassifier
import com.lunacattus.llm.core.generate.LlamaChatEngine
import com.lunacattus.llm.jni.LlmNative
import java.util.concurrent.atomic.AtomicBoolean


object LlmSdk {

    private const val TAG = "LlmSdk"
    private val lock = Any()

    private val isInitialized = AtomicBoolean(false)

    fun initialize(context: Context) {
        LlmLog.d(TAG, "initialize: $context, isInitialized=${isInitialized.get()}")
        if (isInitialized.get()) return
        synchronized(lock) {
            if (isInitialized.get()) return
            try {
                LlmNative.init(context.applicationInfo.nativeLibraryDir)
            } catch (e: Exception) {
                LlmLog.e(TAG, "Failed to initialize LLM SDK", e)
            }
            isInitialized.set(true)
        }
    }

    fun shutDown() {
        LlmLog.d(TAG, "shutDown")
        LlmNative.shutDown()
    }

    /**
     * 创建分类器。
     *
     * @param context Android Context，ONNX 后端需要（加载 custom op / 设置 QNN 路径）
     * @param config  分类器配置，见 [ClassifierConfig.Onnx] 和 [ClassifierConfig.Llama]
     */
    fun createClassifier(context: Context, config: ClassifierConfig): IClassifierEngine {
        LlmLog.d(TAG, "createClassifier: $config")
        return when (config) {
            is ClassifierConfig.Onnx -> OnnxClassifier(context, config)
            is ClassifierConfig.Llama -> LlamaClassifier(config)
        }
    }

    /**
     * 创建对话引擎（llama.cpp Chat）。
     */
    fun createGenerateEngine(config: GenerateConfig): IGenerateEngine {
        LlmLog.d(TAG, "createGenerateEngine: $config")
        return LlamaChatEngine(config)
    }
}