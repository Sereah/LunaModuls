#include <jni.h>
#include <memory>

#include "tool/logger.h"
#include "encoder/EncoderCppEngine.h"
#include "chat/ChatEngine.h"

using lunacattus::encoder::EncoderCppEngine;
using lunacattus::chat::ChatEngine;

std::unique_ptr<EncoderCppEngine> g_encoder;
std::unique_ptr<ChatEngine> g_engine;

/**
 * 初始化 llama.cpp 后端：设置日志回调、动态后端加载路径，
 * 并创建 Encoder 和 Chat 两个引擎实例。
 */
extern "C"
JNIEXPORT void JNICALL
Java_com_lunacattus_llm_jni_LlmNative_nativeInit(JNIEnv *env, jclass,
                                                 jstring native_lib_path) {
    llama_log_set(AndroidLogCallback, nullptr);

    const auto *path = env->GetStringUTFChars(native_lib_path, nullptr);
    LOGI("%s: nativeLibPath=%s", __func__, path);

    // 初始化 llama.cpp 后端（Encoder 和 Chat 共用）
    EncoderCppEngine::InitBackend(path);
    ChatEngine::InitBackend(path);

    // 创建引擎实例
    g_encoder = std::make_unique<EncoderCppEngine>();
    g_engine = std::make_unique<ChatEngine>();

    env->ReleaseStringUTFChars(native_lib_path, path);
    LOGI("%s: 后端初始化完成", __func__);
}

/**
 * 关闭 llama.cpp 后端全局资源（进程退出前调用）。
 */
extern "C"
JNIEXPORT void JNICALL
Java_com_lunacattus_llm_jni_LlmNative_nativeShutDown(JNIEnv * /*env*/, jclass) {
    EncoderCppEngine::ShutDown();
    ChatEngine::ShutDown();
    g_encoder.reset();
    g_engine.reset();
    LOGI("%s: 后端已关闭", __func__);
}