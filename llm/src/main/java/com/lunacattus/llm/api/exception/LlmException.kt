package com.lunacattus.llm.api.exception

/**
 * LLM SDK 统一异常体系。
 */
sealed class LlmException(message: String? = null, cause: Throwable? = null) :
    Exception(message, cause) {

    /** 模型文件不存在 */
    class ModelNotFound(path: String) : LlmException("模型文件不存在: $path")

    /** 模型加载失败 */
    class ModelLoadFailed(msg: String = "模型加载失败") : LlmException(msg)

    /** 模型准备失败（创建上下文 / 采样器失败） */
    class ModelPrepareFailed(msg: String = "模型准备失败") : LlmException(msg)

    /** 模型未加载 */
    class ModelNotLoaded(msg: String = "模型未加载，请先调用 load()") : LlmException(msg)

    /** 分类推理失败 */
    class ClassifyFailed(msg: String = "分类推理失败", cause: Throwable? = null) :
        LlmException(msg, cause)

    /** 指定的 NPU 后端在当前设备不可用 */
    class NpuNotAvailable(msg: String = "当前设备不支持所选的 NPU 后端") :
        LlmException(msg)

    /** 聊天生成失败 */
    class GenerateFailed(msg: String = "聊天生成失败", cause: Throwable? = null) :
        LlmException(msg, cause)
}
