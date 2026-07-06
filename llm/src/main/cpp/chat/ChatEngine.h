#pragma once

#include <llama.h>
#include "chat.h"
#include "ChatTerm.h"

namespace lunacattus::chat {

    class ChatEngine {
    public:
        ChatEngine() = default;

        ~ChatEngine();

        ChatEngine(const ChatEngine &) = delete;

        ChatEngine &operator=(const ChatEngine &) = delete;

        /// 一次性全局后端初始化，必须在任何实例方法之前调用
        static void InitBackend(const char *native_lib_path);

        /// 从指定路径加载模型文件。返回 0 表示成功，非 0 表示失败
        int LoadModel(const char *model_path);

        /// 创建推理上下文、批次缓冲、chat template 和采样器。
        /// 返回 0 表示成功，非 0 表示失败
        int Prepare(int context_size = kDefaultContextSize);

        /// 释放引擎持有的所有资源（模型、上下文、采样器等）
        void Unload();

        /// 关闭 llama.cpp 后端，释放全局资源
        static void ShutDown();

        /// 模型、上下文、chat template、采样器全部就绪时返回 true
        [[nodiscard]] bool IsReady() const;

        /// 当前上下文窗口是否已接近容量上限
        [[nodiscard]] bool IsContextFull() const;

        /// 获取 ChatTerm 的可变引用，供 JNI 层更新位置信息
        ChatTerm &chat_term();

        /// 重置系统提示词相关的长期状态（消息记录、位置），可选清除 KV 缓存
        void ResetSystemTerm(bool clear_kv_cache = true);

        /// 重置用户轮次相关的短期状态（停止位置、缓存字符、助手输出）
        void ResetUserTerm();

        /// 将原始提示词格式化为 chat template 格式（如有 template）
        /// enable_thinking 默认为 false（关闭思考模式）
        std::string FormatPrompt(bool is_system_role, const char *prompt,
                                 bool enable_thinking = false);

        /// 将格式化后的提示词 token 化，返回 token ID 列表
        std::vector<int> TokenizePrompt(const std::string &prompt);

        /// 检查 token 数量是否超出上下文窗口上限；
        /// 系统提示词超限直接报错，用户提示词超限截断处理
        static int CheckTokensLength(bool is_system_role, std::vector<int> &tokens);

        /// 将 Token 列表按批次大小分组，分批送入 llama_decode 执行推理
        /// start_pos_override >= 0 时覆盖当前位置（用于增量解码）
        int DecodeTokensInBatches(const std::vector<int> &tokens,
                                  bool compute_last_logit = false,
                                  llama_pos start_pos_override = -1);

        /// 溢出时丢弃中间一半 token，将系统提示词和最新内容拼接
        void ShiftContext();

        /// 从采样器中采样下一个 token ID
        int SampleNextToken();

        /// 清理 batch 并将新 token 加入下一轮解码
        int PopulateBatchAndDecode(int new_token_id);

        /// 检查新生成的 token 是否为结束标记（EOS/EOT）
        bool CheckTokenEndOfGenerate(int new_token_id);

        /// 将 token ID 转换为可读文本，累积到缓存中
        std::string ConvertTokenToText(int new_token_id);

    private:
        static constexpr int kDefaultContextSize = 8192;
        static constexpr int kOverflowHeadroom = 4;
        static constexpr int kNThreadsMin = 2;
        static constexpr int kNThreadsMax = 4;
        static constexpr int kNThreadsHeadroom = 2;
        static constexpr int kBatchSize = 512;
        static constexpr float kDefaultSamplerTemp = 1.2f;
        static constexpr const char *kRoleUser = "user";
        static constexpr const char *kRoleSystem = "system";
        static constexpr const char *kRoleAssistant = "assistant";

        llama_model *model_ = nullptr;
        llama_context *context_ = nullptr;
        llama_batch batch_ = {};
        common_chat_templates_ptr templates_ = nullptr;
        common_sampler *sampler_ = nullptr;

        ChatTerm chat_term_;

        /// 创建 llama 推理上下文，配置线程数和上下文大小
        int InitContext(int context_size);

        /// 将角色和内容组装为 common_chat_msg 并添加到消息列表，返回格式化后的提示词字符串
        /// enable_thinking 控制 Jinja 模板是否开启思考模式
        std::string AddChat(const std::string &role, const std::string &content,
                            bool enable_thinking = false);
    };
}
