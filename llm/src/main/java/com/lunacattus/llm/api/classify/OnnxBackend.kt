package com.lunacattus.llm.api.classify

/**
 * ONNX Runtime 推理后端选择。
 *
 * | 后端  | 说明 | 适用条件 |
 * |-------|------|---------|
 * | [CPU]  | 纯 CPU 推理，兼容性最好 | 所有设备 |
 * | [NNAPI] | Android Neural Networks API，已废弃 | Android ≤14 且芯片支持 DSP |
 * | [QNN]   | 高通 AI Engine Direct，直连 Hexagon NPU | 骁龙 8 Gen 1+，需 QNN SDK 库 |
 */
enum class OnnxBackend {
    /** 纯 CPU 推理（默认，兼容所有设备） */
    CPU,

    /**
     * Android NNAPI（Neural Networks API）。
     *
     * **已废弃警告**：Android 15（API 35）已标记 NNAPI 为 deprecated。
     * 建议新设备迁移到 [QNN]。
     */
    NNAPI,

    /**
     * 高通 QNN（Qualcomm Neural Network / AI Engine Direct）。
     *
     * 直连 Hexagon Tensor Processor (HTP)，不经过 NNAPI 中间层。
     * 需要：QAIRT SDK 运行时库（libQnnHtp.so 等）在设备固件或 APK 中。
     */
    QNN,
}
