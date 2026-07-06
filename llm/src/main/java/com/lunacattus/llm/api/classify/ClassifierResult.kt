package com.lunacattus.llm.api.classify

/**
 * 文本分类结果。
 *
 * @property labelIndex 预测类别索引（0 起始），由训练时的 label_mapping 定义。
 *                      例如 5 → "直接车控"。
 * @property confidence 置信度 0.0～1.0。ONNX 后端可提供 softmax 分数，
 *                      llama.cpp 后端暂仅返回 null。
 */
data class ClassifierResult(
    val labelIndex: Int,
    /** 推理耗时（毫秒），包含 JNI 调用 / ONNX session.run 的完整耗时 */
    val timeMs: Long,
    val confidence: Float? = null,
)
