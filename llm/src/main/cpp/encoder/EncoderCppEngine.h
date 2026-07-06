#pragma once

#include <llama.h>
#include <string>
#include <vector>

namespace lunacattus::encoder {

    class EncoderCppEngine {
    public:
        EncoderCppEngine() = default;

        ~EncoderCppEngine();

        EncoderCppEngine(const EncoderCppEngine &) = delete;

        EncoderCppEngine &operator=(const EncoderCppEngine &) = delete;

        static void InitBackend(const char *native_lib_path);

        int LoadModel(const char *model_path);

        int Prepare();

        void Unload();

        static void ShutDown();

        [[nodiscard]] bool IsReady() const;

        /// 将文本 tokenize，自动添加 [CLS] 和 [SEP] 特殊 token
        std::vector<int> Tokenize(const std::string &text);

        /// 编码器前向传播，所有 token 均标记为输出（供后续提取 embeddings）
        int Encode(const std::vector<int> &tokens);

        /// CLS embedding → pooler → 分类头，返回 argmax 类别索引；失败返回 -1
        int GetTopClassIndex() const;

    private:
        static constexpr int kMaxSeqLen        = 512;
        static constexpr int kBatchSize        = 512;
        static constexpr int kNThreadsMin      = 2;
        static constexpr int kNThreadsMax      = 4;
        static constexpr int kNThreadsHeadroom = 2;

        llama_model   *model_   = nullptr;
        llama_context *context_ = nullptr;
        llama_batch    batch_   = {};

        // 分类头权重（模型加载时从 GGUF 读入）
        int n_embd_ = 0;  // BERT 隐藏层维度（768）
        int n_cls_  = 0;  // 分类数（9）
        std::vector<float> pooler_w_;  // [n_embd × n_embd]，来自 GGUF KV
        std::vector<float> pooler_b_;  // [n_embd]，来自 GGUF KV
        std::vector<float> cls_w_;     // [n_cls × n_embd]，来自 GGUF tensor
        std::vector<float> cls_b_;     // [n_cls]，来自 GGUF tensor

        int LoadClassificationWeights(const char *model_path);
    };

}
