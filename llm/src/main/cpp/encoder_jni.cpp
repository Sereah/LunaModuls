#include <jni.h>
#include <memory>

#include "encoder/EncoderCppEngine.h"
#include "tool/logger.h"

using lunacattus::encoder::EncoderCppEngine;

// g_encoder 定义在 llm_jni.cpp 中
extern std::unique_ptr<EncoderCppEngine> g_encoder;

/**
 * 加载 BERT GGUF 模型。
 *
 * @return 0 成功，非 0 失败
 */
extern "C"
JNIEXPORT jint JNICALL
Java_com_lunacattus_llm_jni_LlmNative_nativeLoadBertModel(JNIEnv *env, jclass,
                                                                    jstring model_path) {
    if (!g_encoder) {
        LOGE("%s: encoder 引擎未初始化", __func__);
        return 1;
    }
    const auto *path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("%s: modelPath=%s", __func__, path);
    const int result = g_encoder->LoadModel(path);
    env->ReleaseStringUTFChars(model_path, path);
    return result;
}

/**
 * 准备 Encoder 推理上下文（创建 context / batch）。
 *
 * @return 0 成功，非 0 失败
 */
extern "C"
JNIEXPORT jint JNICALL
Java_com_lunacattus_llm_jni_LlmNative_nativePrepareBert(JNIEnv * /*env*/, jclass) {
    if (!g_encoder) {
        LOGE("%s: encoder 引擎未初始化", __func__);
        return 1;
    }
    return g_encoder->Prepare();
}

/**
 * BERT 编码 + pooler + 分类。
 *
 * @param input 原始中文文本
 * @return 类别索引（0 起始），-1 表示失败
 */
extern "C"
JNIEXPORT jint JNICALL
Java_com_lunacattus_llm_jni_LlmNative_nativeClassifyText(JNIEnv *env, jclass,
                                                                  jstring input) {
    if (!g_encoder) {
        LOGE("%s: encoder 引擎未初始化", __func__);
        return -1;
    }

    const char *text = env->GetStringUTFChars(input, nullptr);
    LOGI("%s: text=%s", __func__, text);
    std::string text_str(text);
    env->ReleaseStringUTFChars(input, text);

    // 1. 分词（自动添加 [CLS] 和 [SEP]）
    const auto tokens = g_encoder->Tokenize(text_str);
    LOGI("%s: token count=%d", __func__, (int) tokens.size());

    // 2. 编码
    if (g_encoder->Encode(tokens) != 0) {
        LOGE("%s: Encode 失败", __func__);
        return -1;
    }

    // 3. CLS → pooler → 分类头 → argmax
    return (jint) g_encoder->GetTopClassIndex();
}

/**
 * 卸载 BERT 模型（保留后端）。
 *
 */
extern "C"
JNIEXPORT void JNICALL
Java_com_lunacattus_llm_jni_LlmNative_nativeUnloadEncoder(JNIEnv * /*env*/, jclass) {
    if (!g_encoder) return;
    g_encoder->Unload();
    LOGI("%s: encoder 已卸载", __func__);
}
