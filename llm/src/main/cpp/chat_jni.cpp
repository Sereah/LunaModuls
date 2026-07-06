#include <jni.h>
#include <memory>

#include "chat/ChatEngine.h"
#include "tool/logger.h"
#include "tool/utf8.h"

using lunacattus::chat::ChatEngine;

// g_engine 定义在 llm_jni.cpp 中
extern std::unique_ptr<ChatEngine> g_engine;

/**
 * 加载 GGUF 对话模型。
 *
 * @return 0 成功，非 0 失败
 */
extern "C"
JNIEXPORT jint JNICALL
Java_com_lunacattus_llm_jni_LlmNative_nativeLoadLlmModel(JNIEnv *env, jclass,
                                                                  jstring model_path) {
    if (!g_engine) {
        LOGE("%s: chat 引擎未初始化", __func__);
        return 1;
    }
    const auto *path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("%s: modelPath=%s", __func__, path);
    const int result = g_engine->LoadModel(path);
    env->ReleaseStringUTFChars(model_path, path);
    return result;
}

/**
 * 准备 Chat 推理上下文（context / batch / sampler / chat template）。
 *
 * @return 0 成功，非 0 失败
 */
extern "C"
JNIEXPORT jint JNICALL
Java_com_lunacattus_llm_jni_LlmNative_nativePrepareLlm(JNIEnv * /*env*/, jclass) {
    if (!g_engine) {
        LOGE("%s: chat 引擎未初始化", __func__);
        return 1;
    }
    return g_engine->Prepare();
}

/**
 * 处理系统提示词：格式化 → tokenize → 批量解码 → 记录位置。
 *
 * @return 0 成功，非 0 失败
 */
extern "C"
JNIEXPORT jint JNICALL
Java_com_lunacattus_llm_jni_LlmNative_nativeProcessSystemPrompt(JNIEnv *env, jclass,
                                                                         jstring sys_prompt,
                                                                         jboolean enable_thinking) {
    if (!g_engine) {
        LOGE("%s: chat 引擎未初始化", __func__);
        return 1;
    }

    g_engine->ResetSystemTerm();
    g_engine->ResetUserTerm();

    const auto system_prompt = env->GetStringUTFChars(sys_prompt, nullptr);
    LOGI("%s: prompt=%s, enable_thinking=%d", __func__, system_prompt, enable_thinking);

    std::string formatted = g_engine->FormatPrompt(true, system_prompt, enable_thinking);
    env->ReleaseStringUTFChars(sys_prompt, system_prompt);

    auto tokens = g_engine->TokenizePrompt(formatted);
    if (ChatEngine::CheckTokensLength(true, tokens) != 0) {
        LOGE("%s: token 长度检查失败", __func__);
        return 1;
    }

    if (g_engine->DecodeTokensInBatches(tokens, false) != 0) {
        LOGE("%s: 批量解码失败", __func__);
        return 1;
    }

    // 记录位置信息，供后续增量解码使用
    auto &chat = g_engine->chat_term();
    chat.system_prompt_position  = (int) tokens.size();
    chat.current_position        = (int) tokens.size();
    chat.previous_prompt_tokens  = (int) tokens.size();

    return 0;
}

/**
 * 处理用户输入：增量解码，仅处理本轮新增的 token。
 *
 * @return 0 成功，非 0 失败
 */
extern "C"
JNIEXPORT jint JNICALL
Java_com_lunacattus_llm_jni_LlmNative_nativeProcessUserPrompt(JNIEnv *env, jclass,
                                                                       jstring user_prompt,
                                                                       jint predict_length,
                                                                       jboolean enable_thinking) {
    if (!g_engine) {
        LOGE("%s: chat 引擎未初始化", __func__);
        return 1;
    }

    g_engine->ResetUserTerm();

    const char *const u_prompt = env->GetStringUTFChars(user_prompt, nullptr);
    LOGI("%s: prompt=%s, predictLength=%d, enable_thinking=%d",
         __func__, u_prompt, predict_length, enable_thinking);

    std::string formatted = g_engine->FormatPrompt(false, u_prompt, enable_thinking);
    env->ReleaseStringUTFChars(user_prompt, u_prompt);

    std::vector<int> tokens = g_engine->TokenizePrompt(formatted);
    if (ChatEngine::CheckTokensLength(false, tokens) != 0) {
        LOGE("%s: token 长度检查失败", __func__);
        return 1;
    }

    // 增量解码：跳过已在 KV cache 中的历史 token
    auto &chat = g_engine->chat_term();
    const int prev_count  = chat.previous_prompt_tokens;
    const int total_count = (int) tokens.size();
    const int new_count   = total_count - prev_count;

    if (new_count > 0) {
        std::vector<int> new_tokens(tokens.begin() + prev_count, tokens.end());
        LOGI("%s: 增量解码 %d 个新 token (total=%d, prev=%d)",
             __func__, new_count, total_count, prev_count);
        if (g_engine->DecodeTokensInBatches(new_tokens, true, chat.current_position) != 0) {
            LOGE("%s: 增量解码失败", __func__);
            return 1;
        }
    } else {
        LOGI("%s: 无新增 token，跳过解码", __func__);
    }
    chat.previous_prompt_tokens = total_count;

    // 更新位置
    chat.current_position += new_count;
    chat.stop_generation_position = chat.current_position + predict_length;

    return 0;
}

/**
 * 采样并解码下一个 token。
 *
 * @return 解码后的文本片段（jstring），nullptr 表示生成结束
 */
extern "C"
JNIEXPORT jstring JNICALL
Java_com_lunacattus_llm_jni_LlmNative_nativeGenerateNextToken(JNIEnv *env, jclass) {
    if (!g_engine) {
        LOGE("%s: chat 引擎未初始化", __func__);
        return nullptr;
    }

    // 上下文窗口满 → 执行滑动平移
    if (g_engine->IsContextFull()) {
        g_engine->ShiftContext();
    }

    // 到达预设停止位置
    auto &chat = g_engine->chat_term();
    if (chat.current_position >= chat.stop_generation_position) {
        LOGW("%s: 到达停止位置: %d", __func__, chat.stop_generation_position);
        return nullptr;
    }

    const int new_token_id = g_engine->SampleNextToken();

    if (g_engine->PopulateBatchAndDecode(new_token_id) != 0) {
        LOGE("%s: 解码失败", __func__);
        return nullptr;
    }

    chat.current_position++;

    // 检查结束标记
    if (g_engine->CheckTokenEndOfGenerate(new_token_id)) {
        LOGD("%s: EOS token=%d, 生成结束", __func__, new_token_id);
        return nullptr;
    }

    auto new_token_chars = g_engine->ConvertTokenToText(new_token_id);

    // UTF-8 完整性检查
    auto cached_chars = chat.cached_token_chars.c_str();
    jstring result = nullptr;
    if (lunacattus::chat::IsValidUtf8(cached_chars)) {
        result = env->NewStringUTF(cached_chars);
        LOGD("%s: token=%d, output='%s'", __func__, new_token_id, cached_chars);
        chat.assistant_ss << chat.cached_token_chars;
        chat.cached_token_chars.clear();
    } else {
        LOGD("%s: token=%d, 缓存中等待更多字节", __func__, new_token_id);
        result = env->NewStringUTF("");
    }

    return result;
}

/**
 * 卸载对话模型（保留后端）。
 *
 */
extern "C"
JNIEXPORT void JNICALL
Java_com_lunacattus_llm_jni_LlmNative_nativeUnloadLlm(JNIEnv * /*env*/, jclass) {
    if (!g_engine) return;
    g_engine->Unload();
    LOGI("%s: chat 模型已卸载", __func__);
}
