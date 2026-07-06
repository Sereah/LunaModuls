package com.lunacattus.llm.api.classify

/**
 * 高通 QNN NPU（HTP / DSP / GPU）加速配置。
 *
 * 所有配置项均为可选，默认值适配大多数场景。仅当 [OnnxBackend.QNN] 时生效，
 * 通过 `toProviderOptions()` 转换为 ONNX Runtime QNN EP 所需的 `Map<String, String>`。
 *
 * ## 关键配置
 * - [backendPath] / [backendType] — 二选一，指定 QNN 后端库。不传时自动检测
 * - [performanceMode] — HTP 性能模式，影响功耗与延迟
 * - [enableFp16Precision] — FP16 精度，INT8/W8A8 量化模型应关闭
 * - [vtcmMb] — VTCM 高速片上缓存大小（MB），0 = 自动
 *
 * ## 相关文档
 * - [ONNX Runtime QNN Execution Provider](https://onnxruntime.ai/docs/execution-providers/QNN-ExecutionProvider.html)
 * - [Qualcomm AI Engine Direct SDK](https://www.qualcomm.com/developer/software/qualcomm-ai-engine-direct-sdk)
 */
data class QnnConfig(
    // ════════════════════════════════════════════════════════════════
    // 后端选择（互斥：只传其中一个，不传则自动检测）
    // ════════════════════════════════════════════════════════════════

    /**
     * QNN 后端库文件的绝对路径。
     *
     * 例如：`"/vendor/lib64/libQnnHtp.so"` 或 `"$nativeLibDir/libQnnHtp.so"`。
     * 与 [backendType] **互斥**，二选一。默认 `null` = 由 ONNX Runtime 自动检测
     * （在 `libonnxruntime.so` 同级目录下查找对应后端库）。
     *
     * 适用场景：QNN 库位于非标准路径、或需要精确控制加载哪个库。
     */
    val backendPath: String? = null,

    /**
     * QNN 内部后端类型，与 [backendPath] **互斥**，二选一。
     *
     * 可选值：
     * - `"cpu"` — Kryo CPU（通过 QNN 运行时，非 ORT CPU EP）
     * - `"gpu"` — Adreno GPU
     * - `"htp"` — Hexagon Tensor Processor（**NPU，默认推荐**）
     *
     * 默认 `null` = 自动检测（NPU 可用 → HTP，否则 GPU → CPU）。
     * 注意：这是 QNN 生态内的后端，非 ONNX Runtime 层面的 `OnnxBackend`。
     */
    val backendType: String? = null,

    // ════════════════════════════════════════════════════════════════
    // 性能与精度
    // ════════════════════════════════════════════════════════════════

    /**
     * HTP 性能模式。
     *
     * | 模式 | 说明 | 适用场景 |
     * |------|------|---------|
     * | `"burst"` | **爆发性能（默认）** | 交互式单次推理，低延迟优先 |
     * | `"balanced"` | 均衡 | 通用场景 |
     * | `"default"` | 系统默认 | 交给 QNN 驱动决定 |
     * | `"high_performance"` | 持续高性能 | 长时推理，如视频处理 |
     * | `"high_power_saver"` | 高能效 | 对功耗敏感的长任务 |
     * | `"low_balanced"` | 低功耗均衡 | 轻量模型、后台任务 |
     * | `"extreme_power_saver"` | 极致省电 | 对延迟不敏感的离线任务 |
     * | `"low_power_saver"` | 低功耗省电 | 电池优先 |
     * | `"power_saver"` | 省电 | 通用省电 |
     * | `"sustained_high_performance"` | 持续高性能 | 需要稳定帧率的场景 |
     */
    val performanceMode: String = "burst",

    /**
     * FP32 算子自动转为 FP16 执行，可减少内存带宽、降低延迟。
     *
     * - `true` → 传给 ORT 的值 `"1"`（默认）
     * - `false` → 传给 ORT 的值 `"0"`，保留 FP32 精度
     *
     * **重要**：INT8/W8A8 等量化模型应设为 `false`，
     * 否则 FP16 转换会引入额外量化误差，降低精度。
     */
    val enableFp16Precision: Boolean = true,

    /**
     * SoC 型号，例如 `"SM8550"`（骁龙 8 Gen 2）、`"SM8650"`（骁龙 8 Gen 3）。
     *
     * 默认 `null` = 自动从设备属性中检测。仅调试或特殊适配场景需要手动指定。
     */
    val socModel: String? = null,

    /**
     * HTP 架构版本。
     *
     * | 值 | 对应架构 | 代表 SoC |
     * |----|---------|---------|
     * | `"0"` | 自动检测（默认行为，当值为 `null` 时不传 key） | — |
     * | `"68"` | V68 | 骁龙 8 Gen 1 |
     * | `"69"` | V69 | 骁龙 8+ Gen 1 / 8 Gen 2 |
     * | `"73"` | V73 | 骁龙 8 Gen 3 |
     * | `"75"` | V75 | 骁龙 8 Elite |
     * | `"81"` | V81 | 下一代 |
     *
     * 默认 `null` = 不设置，由 ONNX Runtime 根据 [socModel] 自动推断。
     */
    val htpArch: String? = null,

    // ════════════════════════════════════════════════════════════════
    // 内存
    // ════════════════════════════════════════════════════════════════

    /**
     * VTCM（Vector Tightly Coupled Memory）大小，单位 MB。
     *
     * VTCM 是 Hexagon HTP 内部的**高速片上 SRAM**（带宽远超 DDR），
     * QNN 将权重/中间结果置于 VTCM 中以减少内存延迟。
     *
     * - `0`（默认）— 由 QNN 驱动自行分配，适合大多数模型
     * - `> 0` — 强制预留指定大小，大模型可能受益，但挤占其他用途的内存
     *
     * 典型值：4、8、16。设置过大会导致初始化失败。
     */
    val vtcmMb: Int = 0,

    /**
     * 启用 HTP Spill/Fill Buffer，允许超大 tensor 从 VTCM 溢出到 DDR。
     *
     * - `true` → `"1"`，大模型不会因 VTCM 不足而 OOM
     * - `false` → `"0"`（默认），仅使用 VTCM，超限则失败
     *
     * 当模型中间激活值超过 VTCM 容量时开启。
     */
    val enableHtpSpillFillBuffer: Boolean = false,

    // ════════════════════════════════════════════════════════════════
    // 设备
    // ════════════════════════════════════════════════════════════════

    /**
     * 设备 ID，多 NPU/GPU 设备时用于选择特定硬件。
     *
     * 默认 `0`（第一个设备）。大多数手机只有一个 HTP，无需修改。
     */
    val deviceId: Int = 0,

    // ════════════════════════════════════════════════════════════════
    // 图优化
    // ════════════════════════════════════════════════════════════════

    /**
     * 图输入/输出量化 offload。
     *
     * - `true` → `"1"`（默认），量化/反量化在 HTP 上执行，减少 CPU↔HTP 数据搬运
     * - `false` → `"0"`，量化/反量化在 CPU 上执行
     */
    val offloadGraphIOQuantization: Boolean = true,

    /**
     * QNN Context 优先级。
     *
     * 影响 HTP 调度策略：
     * - `"low"` — 低优先级
     * - `"normal"` — **默认**
     * - `"normal_high"` — 中高优先级
     * - `"high"` — 高优先级（可能影响其他 HTP 任务）
     */
    val qnnContextPriority: String = "normal",

    // ════════════════════════════════════════════════════════════════
    // 调试 & Profiling
    // ════════════════════════════════════════════════════════════════

    /**
     * QNN Profiling 级别。
     *
     * - `"off"` — 关闭（**默认**，零开销）
     * - `"basic"` — 基本性能指标（各 op 耗时）
     * - `"detailed"` — 详细逐层性能数据（有性能开销）
     */
    val profilingLevel: String = "off",

    /**
     * Profiling 结果输出文件路径。
     *
     * 仅 [profilingLevel] 不为 `"off"` 时生效。默认 `null` = 不输出文件。
     */
    val profilingFilePath: String? = null,

    /**
     * QNN RPC 控制延迟（微秒）。
     *
     * 默认 `null` = 由 QNN 驱动自动选择。仅高级调试场景使用。
     */
    val rpcControlLatency: Int? = null,

    /**
     * 导出 QNN IR 为 DLC 文件（仅调试用）。
     *
     * - `true` → `"1"`，导出 DLC 文件用于 QAIRT 工具链分析
     * - `false` → `"0"`（**默认**）
     */
    val dumpQnnIrDlc: Boolean = false,

    // ════════════════════════════════════════════════════════════════
    // 扩展
    // ════════════════════════════════════════════════════════════════

    /**
     * QNN UDO（User Defined Operations）包配置。
     *
     * 格式：`"packageName1:path1,packageName2:path2"`。
     * 默认 `null` = 不加载任何 UDO。
     */
    val opPackages: String? = null,

    /**
     * 额外原始 key-value 配置项，直接透传给 ONNX Runtime QNN EP。
     *
     * 用于 [QnnConfig] 标准字段未覆盖的罕见参数，例如：
     * ```kotlin
     * QnnConfig(
     *     extraOptions = mapOf(
     *         "enable_vtcm_backup_buffer_sharing" to "1",
     *         "disable_file_mapped_weights" to "1",
     *     )
     * )
     * ```
     *
     * **注意**：extraOptions 中的 key 会覆盖同名标准字段的值。
     */
    val extraOptions: Map<String, String> = emptyMap(),
) {
    /**
     * 转换为 ONNX Runtime QNN EP 所需的 `Map<String, String>`，
     * 可直接传入 `OrtSession.SessionOptions.addQnn()`。
     *
     * **转换规则**：
     * - `null` 字段不输出（让 ORT 使用默认值）
     * - `Boolean` 字段映射为 `"1"` / `"0"`
     * - [extraOptions] 最后合并，可覆盖任何标准字段
     */
    fun toProviderOptions(): Map<String, String> = buildMap {
        // ── 后端选择（互斥，只传一个） ──
        if (backendPath != null) {
            put("backend_path", backendPath)
        } else if (backendType != null) {
            put("backend_type", backendType)
        }
        // 都不传 → ORT 自动检测

        // ── 性能与精度 ──
        put("htp_performance_mode", performanceMode)
        put("enable_htp_fp16_precision", if (enableFp16Precision) "1" else "0")

        socModel?.let { put("soc_model", it) }
        htpArch?.let { put("htp_arch", it) }

        // ── 内存 ──
        if (vtcmMb > 0) {
            put("vtcm_mb", vtcmMb.toString())
        }
        // 默认 0 时不传 key，避免 ORT "Skip invalid vtcm_mb: 0" warning
        if (enableHtpSpillFillBuffer) {
            put("enable_htp_spill_fill_buffer", "1")
        }
        // 默认 false 时不传，保持 ORT 默认行为（关闭）

        // ── 设备 ──
        if (deviceId != 0) {
            put("device_id", deviceId.toString())
        }

        // ── 图优化 ──
        if (!offloadGraphIOQuantization) {
            put("offload_graph_io_quantization", "0")
        }
        // 默认 true = ORT 默认开启，不传 key

        if (qnnContextPriority != "normal") {
            put("qnn_context_priority", qnnContextPriority)
        }
        // 默认 normal = ORT 默认值，不传 key

        // ── 调试 ──
        if (profilingLevel != "off") {
            put("profiling_level", profilingLevel)
            profilingFilePath?.let { put("profiling_file_path", it) }
        }

        rpcControlLatency?.let { put("rpc_control_latency", it.toString()) }

        if (dumpQnnIrDlc) {
            put("dump_qnn_ir_dlc", "1")
        }

        // ── 扩展 ──
        opPackages?.let { put("op_packages", it) }

        // ── 用户自定义（最后放入，可覆盖以上所有 key） ──
        putAll(extraOptions)
    }
}
