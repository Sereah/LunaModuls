package com.lunacattus.llm.api.classify

/**
 * 分类器配置，根据后端类型选择 [Onnx] 或 [Llama]。
 *
 * 用法：
 * ```kotlin
 * // ONNX + QNN NPU
 * val cfg = ClassifierConfig.Onnx(
 *     modelPath = "/data/models/nlu_mobile.onnx",
 *     backend = OnnxBackend.QNN,
 *     qnnConfig = QnnConfig(),
 * )
 *
 * // llama.cpp
 * val cfg = ClassifierConfig.Llama(
 *     modelPath = "/data/models/nlu.gguf",
 * )
 * ```
 */
sealed class ClassifierConfig {

    /** 模型文件绝对路径 */
    abstract val modelPath: String

    /** CPU 推理线程数 */
    abstract val numThreads: Int

    /**
     * ONNX Runtime 后端。
     *
     * @property backend   推理后端：CPU / NNAPI / QNN
     * @property qnnConfig QNN 详细配置，仅 [OnnxBackend.QNN] 时生效
     */
    data class Onnx(
        override val modelPath: String,
        override val numThreads: Int = 4,
        val backend: OnnxBackend = OnnxBackend.CPU,
        val qnnConfig: QnnConfig? = null,
    ) : ClassifierConfig()

    /**
     * llama.cpp JNI 后端。
     */
    data class Llama(
        override val modelPath: String,
        override val numThreads: Int = 4,
    ) : ClassifierConfig()
}
