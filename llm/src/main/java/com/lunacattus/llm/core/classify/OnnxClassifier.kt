package com.lunacattus.llm.core.classify

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import com.lunacattus.llm.api.LlmLog
import com.lunacattus.llm.api.classify.ClassifierConfig
import com.lunacattus.llm.api.classify.ClassifierResult
import com.lunacattus.llm.api.classify.IClassifierEngine
import com.lunacattus.llm.api.classify.OnnxBackend
import com.lunacattus.llm.api.classify.QnnConfig
import com.lunacattus.llm.api.exception.LlmException
import com.lunacattus.llm.jni.LlmNative
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.util.concurrent.Executors

/**
 * ONNX Runtime BERT 分类器实现。
 *
 * 支持三种推理后端（通过 [ClassifierConfig.Onnx.backend] 选择）：
 * - [OnnxBackend.CPU] — 纯 CPU，兼容性最好
 * - [OnnxBackend.NNAPI] — Android NNAPI（Android ≤14 可用，≥15 已废弃）
 * - [OnnxBackend.QNN] — 高通 AI Engine Direct，直连 Hexagon NPU
 */
internal class OnnxClassifier(
    private val context: Context,
    private val config: ClassifierConfig.Onnx,
) : IClassifierEngine {

    companion object {
        private const val TAG = "OnnxClassifier"

        // 使用 Java 线程池避免 limitedParallelism 的版本兼容问题
        private val Dispatcher = Executors
            .newSingleThreadExecutor { Thread(it, "OnnxClassifier") }
            .asCoroutineDispatcher()
    }

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null

    @Volatile
    private var modelLoaded = false

    override fun load(): Flow<Result<Unit>> = flow {
        if (modelLoaded) {
            LlmLog.d(TAG, "model already loaded, skip.")
            emit(Result.success(Unit))
            return@flow
        }

        LlmLog.d(TAG, "load model: ${config.modelPath}, backend: ${config.backend}")
        try {
            if (!File(config.modelPath).exists()) {
                emit(Result.failure(LlmException.ModelNotFound(config.modelPath)))
                return@flow
            }

            ortEnv = OrtEnvironment.getEnvironment()
            LlmLog.d(TAG, "ONNX Runtime environment created")

            // 配置 Session
            val options = OrtSession.SessionOptions()

            // 注册 onnxruntime-extensions 自定义算子（BertTokenizer 等）
            registerExtensions(options)

            // 根据后端类型配置
            when (config.backend) {
                OnnxBackend.CPU -> configureCpu()
                OnnxBackend.NNAPI -> configureNnapi(options)
                OnnxBackend.QNN -> configureQnn(options, config.qnnConfig)
            }

            // 创建 session
            ortSession = ortEnv?.createSession(config.modelPath, options)
            LlmLog.d(TAG, "model loaded: ${config.modelPath}")
            modelLoaded = true
            emit(Result.success(Unit))
        } catch (e: Throwable) {
            LlmLog.e(TAG, "load fail", e)
            releaseSession()
            emit(Result.failure(LlmException.ModelLoadFailed(e.message ?: "unknown")))
        }
    }.flowOn(Dispatcher)

    override suspend fun classify(text: String): ClassifierResult {
        if (!modelLoaded) throw LlmException.ModelNotLoaded()
        val env = ortEnv ?: throw LlmException.ModelNotLoaded()
        val session = ortSession ?: throw LlmException.ModelNotLoaded()

        LlmLog.d(TAG, "classify: $text")
        val startTime = System.currentTimeMillis()
        try {
            // 输入：2D 字符串张量 [batch=1, num_sentences=1]
            val inputTensor = OnnxTensor.createTensor(env, arrayOf(arrayOf(text)))
            inputTensor.use { input ->
                val outputs = session.run(mapOf("input_text" to input))
                outputs.use { result ->
                    val predId = (result[0].value as LongArray)[0].toInt()
                    val elapsed = System.currentTimeMillis() - startTime
                    LlmLog.d(TAG, "classify result: index=$predId, time consuming=${elapsed}ms")
                    return ClassifierResult(labelIndex = predId, timeMs = elapsed)
                }
            }
        } catch (e: Throwable) {
            LlmLog.e(TAG, "classify fail", e)
            throw LlmException.ClassifyFailed(e.message ?: "classify fail", e)
        }
    }

    override val isReady: Boolean
        get() = ortSession != null && ortEnv != null

    override fun close() {
        LlmLog.d(TAG, "close...")
        releaseSession()
        ortEnv?.close()
        ortEnv = null
        modelLoaded = false
    }

    private fun releaseSession() {
        ortSession?.close()
        ortSession = null
    }

    /** 注册 onnxruntime-extensions 自定义算子库 */
    private fun registerExtensions(options: OrtSession.SessionOptions) {
        val libDir = context.applicationInfo.nativeLibraryDir
        val extLib = "$libDir/libortextensions.so"
        if (File(extLib).exists()) {
            options.registerCustomOpLibrary(extLib)
            LlmLog.d(TAG, "registered extensions: $extLib")
        } else {
            LlmLog.d(TAG, "extensions not exist: $extLib")
        }
    }

    /** 纯 CPU：不做任何额外配置 */
    private fun configureCpu() {
        LlmLog.d(TAG, "Use CPU backend")
    }

    /**
     * Android NNAPI。
     *
     * 注意：Android 15（API 35）已废弃 NNAPI，运行时可能抛异常。
     * 捕获后自动回退 CPU。
     */
    private fun configureNnapi(options: OrtSession.SessionOptions) {
        LlmLog.d(TAG, "try start NNAPI...")
        try {
            options.addNnapi()
            LlmLog.d(TAG, "NNAPI started")
        } catch (e: Throwable) {
            LlmLog.e(TAG, "NNAPI can't use，back to CPU", e)
        }
    }

    /**
     * 高通 QNN（AI Engine Direct）。
     *
     * 步骤：
     * 1. 通过 JNI 设置 ADSP_LIBRARY_PATH / LD_LIBRARY_PATH
     * 2. 配置 QNN EP provider options
     * 3. AppendExecutionProvider("QNN", ...)
     *
     * 若 QNN 库不存在或设备不支持 HTP，自动回退 CPU。
     */
    private fun configureQnn(options: OrtSession.SessionOptions, qnn: QnnConfig?) {
        LlmLog.d(TAG, "try start QNN NPU...")
        try {
            val nativeLibDir = context.applicationInfo.nativeLibraryDir

            // 1. JNI: 设置 ADSP_LIBRARY_PATH，使 FastRPC 能找到 Stub/Skel
            LlmNative.nativeSetQnnEnv(nativeLibDir, skelDir = nativeLibDir)

            // 2. backend_path
            val backendPath = qnn?.backendPath ?: "$nativeLibDir/libQnnHtp.so"
            LlmLog.d(TAG, "QNN backend_path: $backendPath")

            // 3. QNN EP 参数
            val qnnOpts = linkedMapOf(
                "backend_path" to backendPath,
                "htp_performance_mode" to (qnn?.performanceMode ?: "burst"),
            )
            // soc_model：优先使用用户指定值，不传则依赖 QNN 自动检测
            if (!qnn?.socModel.isNullOrEmpty()) {
                qnnOpts["soc_model"] = qnn.socModel
            }
            if (qnn?.enableFp16Precision != false) {
                qnnOpts["enable_htp_fp16_precision"] = "1"
            }
            if ((qnn?.vtcmMb ?: 0) > 0) {
                qnnOpts["vtcm_mb"] = qnn!!.vtcmMb.toString()
            }

            options.addQnn(qnnOpts)

            // 4. 开启 profiling，输出每个 op 的 EP 分配
            val profilePath = "${context.filesDir.absolutePath}/ort_profile"
            options.enableProfiling(profilePath)
            LlmLog.d(TAG, "Profiling 已开启，输出前缀: $profilePath")

            LlmLog.d(TAG, "QNN NPU started (config=${qnn})")
        } catch (e: Throwable) {
            LlmLog.e(TAG, "QNN NPU can't use，back to CPU", e)
        }
    }
}
