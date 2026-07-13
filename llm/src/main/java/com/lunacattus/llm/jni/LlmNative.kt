package com.lunacattus.llm.jni

/**
 * 统一 JNI 桥接层。
 *
 * C++ 侧分为四个源文件，全部编译进 `libllm.so`
 */
object LlmNative {

    @Volatile
    private var initialized = false

    /**
     * 加载 libllm.so 并初始化 llama.cpp 后端，幂等且线程安全。
     *
     * @param nativeLibPath APK 解压后的 native 库目录
     */
    @Synchronized
    fun init(nativeLibPath: String) {
        if (initialized) return
        System.loadLibrary("llm")
        nativeInit(nativeLibPath)
        initialized = true
    }

    fun shutDown() {
        if (!initialized) return
        nativeShutDown()
        initialized = false
    }

    /** 初始化 llama.cpp 后端（日志、动态后端路径等），进程级只调用一次。 */
    @JvmStatic
    private external fun nativeInit(nativeLibPath: String)

    /** 关闭 llama.cpp 后端全局资源。 */
    @JvmStatic
    private external fun nativeShutDown()

    /** 加载 BERT 模型，返回 0 成功。 */
    @JvmStatic
    external fun nativeLoadBertModel(modelPath: String): Int

    /** 准备 Encoder 推理上下文（创建 context / batch），返回 0 成功。 */
    @JvmStatic
    external fun nativePrepareBert(): Int

    /**
     * 对输入文本执行 BERT 编码 + 分类，返回类别索引。
     *
     * @param text 原始中文文本
     * @return 类别索引（0 起始），失败返回 -1
     */
    @JvmStatic
    external fun nativeClassifyText(text: String): Int

    /** 卸载 BERT 模型，释放 encoder 资源。 */
    @JvmStatic
    external fun nativeUnloadEncoder()

    /** 加载对话模型，返回 0 成功。 */
    @JvmStatic
    external fun nativeLoadLlmModel(modelPath: String): Int

    /** 准备 Chat 推理上下文（context / batch / sampler / chat template），返回 0 成功。 */
    @JvmStatic
    external fun nativePrepareLlm(): Int

    /**
     * 处理系统提示词（预处理 + 批量解码 + 记录位置）。
     *
     * @param prompt          系统提示词文本
     * @param enableThinking  是否开启思考模式
     * @return 0 成功，非 0 失败
     */
    @JvmStatic
    external fun nativeProcessSystemPrompt(prompt: String, enableThinking: Boolean): Int

    /**
     * 处理用户输入（增量解码）。
     *
     * @param prompt          用户消息文本
     * @param predictLength   最大生成 token 数
     * @param enableThinking  是否开启思考模式
     * @return 0 成功，非 0 失败
     */
    @JvmStatic
    external fun nativeProcessUserPrompt(
        prompt: String,
        predictLength: Int,
        enableThinking: Boolean,
    ): Int

    /**
     * 采样并解码下一个 token。
     *
     * @return 解码后的文本片段，null 表示生成结束（EOS/达到长度上限/出错）
     */
    @JvmStatic
    external fun nativeGenerateNextToken(): String?

    /** 卸载对话模型，释放 Chat 资源。 */
    @JvmStatic
    external fun nativeUnloadLlm()

    /**
     * 设置 QNN NPU 所需的环境变量。
     *
     * 必须在创建 ONNX Runtime Session 之前调用，确保 QNN EP 能找到 DSP 库。
     *
     * @param nativeLibDir  APK 解压后的 native 库目录（即 jniLibs/arm64-v8a 的解压路径）
     * @return 0 成功，非 0 失败
     */
    @JvmStatic
    external fun nativeSetQnnEnv(nativeLibDir: String, skelDir: String): Int
}
