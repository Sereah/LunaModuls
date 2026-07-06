package com.lunacattus.llm.api.generate

/**
 * 生成式引擎配置。
 *
 * @property modelPath GGUF 格式模型文件绝对路径
 * @property numThreads CPU 推理线程数
 */
data class GenerateConfig(
    val modelPath: String,
    val numThreads: Int = 4,
)
