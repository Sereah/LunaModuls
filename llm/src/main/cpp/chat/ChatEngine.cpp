#include "ChatEngine.h"

#include <bits/sysconf.h>
#include "common.h"

#include "tool/logger.h"
#include "sampling.h"

namespace lunacattus::chat {

    // ==================== 生命周期 ====================

    void ChatEngine::InitBackend(const char *native_lib_path) {
        // 加载 GPU/NPU 后端驱动，初始化 llama.cpp 运行时
        ggml_backend_load_all_from_path(native_lib_path);
        llama_backend_init();
    }

    ChatEngine::~ChatEngine() {
        // 按依赖关系的逆序释放资源：
        // sampler → templates → batch → context → model
        if (templates_ != nullptr) {
            templates_ = nullptr;
        }
        if (sampler_ != nullptr) {
            common_sampler_free(sampler_);
            sampler_ = nullptr;
        }
        llama_batch_free(batch_);
        if (context_ != nullptr) {
            llama_free(context_);
            context_ = nullptr;
        }
        if (model_ != nullptr) {
            llama_model_free(model_);
            model_ = nullptr;
        }
    }

    int ChatEngine::LoadModel(const char *model_path) {
        // 如果已加载过模型，先释放
        if (model_ != nullptr) {
            LOGW("%s: model already loaded, freeing existing model", __func__);
            llama_model_free(model_);
            model_ = nullptr;
        }

        const auto model_params = llama_model_default_params();
        model_ = llama_model_load_from_file(model_path, model_params);
        if (model_ == nullptr) {
            LOGE("%s: llama_model_load_from_file() failed", __func__);
            return 1;
        }
        return 0;
    }

    int ChatEngine::Prepare(const int context_size) {
        if (model_ == nullptr) {
            LOGE("%s: model must be loaded before calling prepare", __func__);
            return 1;
        }

        // 1. 创建推理上下文
        if (InitContext(context_size) != 0) {
            LOGE("%s: InitContext() failed", __func__);
            return 1;
        }
        // 2. 初始化批次缓冲区
        batch_ = llama_batch_init(kBatchSize, 0, 1);

        // 3. 初始化 chat template（用于格式化对话）
        templates_ = common_chat_templates_init(model_, "");
        if (templates_ == nullptr) {
            LOGE("%s: common_chat_templates_init() failed", __func__);
            return 1;
        }

        // 4. 初始化采样器
        common_params_sampling params_sampling;
        params_sampling.temp = kDefaultSamplerTemp;
        sampler_ = common_sampler_init(model_, params_sampling);
        if (sampler_ == nullptr) {
            LOGE("%s: common_sampler_init() failed", __func__);
            return 1;
        }

        return 0;
    }

    void ChatEngine::Unload() {
        // 重置会话状态
        ResetSystemTerm();
        ResetUserTerm();

        // 释放推理资源（判空 + 释放后置 null，防止与析构函数重复释放）
        if (sampler_ != nullptr) {
            common_sampler_free(sampler_);
            sampler_ = nullptr;
        }
        if (templates_ != nullptr) {
            templates_ = nullptr;
        }
        llama_batch_free(batch_);
        batch_ = {};

        if (context_ != nullptr) {
            llama_free(context_);
            context_ = nullptr;
        }
        if (model_ != nullptr) {
            llama_model_free(model_);
            model_ = nullptr;
        }
    }

    void ChatEngine::ShutDown() {
        llama_backend_free();
    }

    // ==================== 状态查询 ====================

    bool ChatEngine::IsReady() const {
        LOGI("%s: model_=%p, context_=%p, templates_=%p, sampler_=%p",
             __func__, model_, context_, templates_.get(), sampler_);
        return model_ != nullptr && context_ != nullptr && templates_ != nullptr &&
               sampler_ != nullptr;
    }

    bool ChatEngine::IsContextFull() const {
        return chat_term_.current_position >= kDefaultContextSize - kOverflowHeadroom;
    }

    // ==================== 会话状态管理 ====================

    void ChatEngine::ResetSystemTerm(bool clear_kv_cache) {
        // 清除消息记录和位置追踪
        chat_term_.chat_messages.clear();
        chat_term_.system_prompt_position = 0;
        chat_term_.current_position = 0;
        chat_term_.previous_prompt_tokens = 0;
        // 可选：清除 KV 缓存（重新从系统提示词开始推理时使用）
        if (clear_kv_cache) {
            llama_memory_clear(llama_get_memory(context_), false);
        }
    }

    void ChatEngine::ResetUserTerm() {
        chat_term_.stop_generation_position = 0;
        chat_term_.cached_token_chars.clear();
        chat_term_.assistant_ss.str("");          // 清空 stringstream
    }

    ChatTerm &ChatEngine::chat_term() {
        return chat_term_;
    }

    // ==================== 提示词处理 ====================

    std::string ChatEngine::FormatPrompt(bool is_system_role, const char *prompt,
                                           bool enable_thinking) {
        // 先保留原始提示词
        std::string format_prompt(prompt);
        // 如果有 chat template，用 template 格式化；否则返回原始文本
        const bool has_chat_template = common_chat_templates_was_explicit(templates_.get());
        if (has_chat_template) {
            const char *role = is_system_role ? kRoleSystem : kRoleUser;
            format_prompt = AddChat(role, prompt, enable_thinking);
        }
        return format_prompt;
    }

    std::vector<int>
    ChatEngine::TokenizePrompt(const std::string &prompt) {
        // 将格式化后的文本转为 token ID 列表
        const bool has_chat_template = common_chat_templates_was_explicit(templates_.get());
        std::vector<int> tokens = common_tokenize(context_, prompt, has_chat_template,
                                                  has_chat_template);
        for (int id: tokens) {
            LOGD("token: `%s`\t -> `%d`", common_token_to_piece(context_, id).c_str(), id);
        }
        return tokens;
    }

    int ChatEngine::CheckTokensLength(bool is_system_role, std::vector<int> &tokens) {
        const int max_batch_size = kDefaultContextSize - kOverflowHeadroom;
        const int token_size = (int) tokens.size();
        if (token_size > max_batch_size) {
            if (is_system_role) {
                // 系统提示词超限 → 拒绝，因为截断会破坏系统指令
                LOGE("%s: System prompt too long for context! %d tokens, max: %d",
                     __func__, token_size, max_batch_size);
                return 1;
            } else {
                // 用户提示词超限 → 截断，丢弃超出部分
                int skip_tokens = token_size - max_batch_size;
                tokens.resize(max_batch_size);
                LOGW("%s: User prompt too long for context! Skipping %d tokens", __func__,
                     skip_tokens);
            }
        }
        return 0;
    }

    int
    ChatEngine::DecodeTokensInBatches(const std::vector<int> &tokens, bool compute_last_logit,
                                        llama_pos start_pos_override) {
        llama_pos start_pos = start_pos_override >= 0 ? start_pos_override
                                                       : chat_term_.current_position;
        LOGI("%s: Decode %d tokens starting at position %d", __func__, (int) tokens.size(),
             start_pos);

        for (int i = 0; i < (int) tokens.size(); i += kBatchSize) {
            const int current_batch_size = std::min(kBatchSize, (int) tokens.size() - i);
            common_batch_clear(batch_);
            LOGD("%s: Preparing a batch size of %d starting at: %d", __func__, current_batch_size,
                 i);

            // 如果当前批次放不进上下文窗口，先执行一次上下文平移
            if (start_pos + i + current_batch_size >= kDefaultContextSize - kOverflowHeadroom) {
                LOGW("%s: Current batch won't fit into context! Shifting...", __func__);
                ShiftContext();
            }

            // 将 token 加入批次，指定位置和日志输出标记
            for (int j = 0; j < current_batch_size; j++) {
                const llama_token token_id = tokens[i + j];
                const llama_pos position = start_pos + i + j;
                const bool want_logit = compute_last_logit && (i + j == tokens.size() - 1);
                common_batch_add(batch_, token_id, position, {0}, want_logit);
            }

            // 执行批量解码
            const int decode_result = llama_decode(context_, batch_);
            if (decode_result) {
                LOGE("%s: llama_decode failed w/ %d", __func__, decode_result);
                return 1;
            }
        }

        return 0;
    }

    void ChatEngine::ShiftContext() {
        // 从系统提示词之后到当前位置之间丢弃一半 token，
        // 将留下的后半段"滑动"到系统提示词之后，保持上下文窗口可用
        const int n_discard = (chat_term_.current_position - chat_term_.system_prompt_position) / 2;
        LOGD("%s: discarding %d tokens", __func__, n_discard);
        llama_memory_seq_rm(llama_get_memory(context_), 0, chat_term_.system_prompt_position,
                            chat_term_.system_prompt_position + n_discard);
        llama_memory_seq_add(llama_get_memory(context_), 0,
                             chat_term_.system_prompt_position + n_discard,
                             chat_term_.current_position, -n_discard);
        chat_term_.current_position -= n_discard;
        LOGD("%s: Context shifting done! Current position: %d", __func__,
             chat_term_.current_position);
    }

    // ==================== 逐 Token 生成循环 ====================

    int ChatEngine::SampleNextToken() {
        // 从采样器采样下一个 token，并告知采样器「该 token 已被接受」
        const int new_token_id = common_sampler_sample(sampler_, context_, -1);
        common_sampler_accept(sampler_, new_token_id, true);
        return new_token_id;
    }

    int ChatEngine::PopulateBatchAndDecode(int new_token_id) {
        // 清空 batch，放入单个新 token，执行解码
        common_batch_clear(batch_);
        common_batch_add(batch_, new_token_id, chat_term_.current_position, {0}, true);
        return llama_decode(context_, batch_);
    }

    bool ChatEngine::CheckTokenEndOfGenerate(int new_token_id) {
        // 判断是否为 EOS/EOT 结束标记
        bool is_eog = llama_vocab_is_eog(llama_model_get_vocab(model_), new_token_id);
        if (is_eog) {
            // 生成结束时，将助手回复加入对话消息列表，供后续多轮对话使用
            AddChat(kRoleAssistant, chat_term_.assistant_ss.str());
        }
        return is_eog;
    }

    std::string ChatEngine::ConvertTokenToText(int new_token_id) {
        // token ID → UTF-8 文本，累积到缓存中等待拼成合法 UTF-8 字符后再输出
        std::string new_token_chars = common_token_to_piece(context_, new_token_id);
        chat_term_.cached_token_chars += new_token_chars;
        return new_token_chars;
    }

    // ==================== 私有方法 ====================

    int ChatEngine::InitContext(int context_size) {
        // 如果已有 context，先释放
        if (context_ != nullptr) {
            LOGW("%s: context already created, freeing existing context", __func__);
            llama_free(context_);
            context_ = nullptr;
        }

        // 计算合适的线程数：CPU 核心数 - 预留，限制在 [kNThreadsMin, kNThreadsMax] 范围内
        const int n_thread = std::max(kNThreadsMin,
                                      std::min(kNThreadsMax,
                                               static_cast<int>(sysconf(_SC_NPROCESSORS_ONLN)) -
                                               kNThreadsHeadroom));
        LOGI("%s: n_thread = %d", __func__, n_thread);

        llama_context_params context_params = llama_context_default_params();
        const int trained_context_size = llama_model_n_ctx_train(model_);
        if (context_size > trained_context_size) {
            LOGW("%s: context_size=%d > trained_context_size=%d, using context_size",
                 __func__, context_size, trained_context_size);
        }

        context_params.n_ctx = context_size;
        context_params.n_batch = kBatchSize;
        context_params.n_ubatch = kBatchSize;
        context_params.n_threads = n_thread;
        context_params.n_threads_batch = n_thread;

        // 从已加载的模型创建推理上下文
        context_ = llama_init_from_model(model_, context_params);
        if (context_ == nullptr) {
            LOGE("%s: llama_init_from_model() failed", __func__);
            return 1;
        }
        return 0;
    }

    std::string ChatEngine::AddChat(const std::string &role, const std::string &content,
                                      bool enable_thinking) {
        common_chat_msg new_msg;
        new_msg.role = role;
        new_msg.content = content;

        // 先将消息加入对话历史，再用 Jinja 模板统一格式化
        chat_term_.chat_messages.push_back(new_msg);

        common_chat_templates_inputs inputs;
        inputs.messages = chat_term_.chat_messages;
        inputs.use_jinja = true;
        inputs.enable_thinking = enable_thinking;
        auto params = common_chat_templates_apply(templates_.get(), inputs);

        LOGI("%s: Formatted and added %s message: %s", __func__,
             params.prompt.c_str(), role.c_str());
        return params.prompt;
    }

} // namespace lunacattus::chat
