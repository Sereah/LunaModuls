#include "EncoderCppEngine.h"

#include <algorithm>
#include <bits/sysconf.h>
#include <cmath>
#include <cstdio>

#include "common.h"
#include "gguf.h"
#include "tool/logger.h"

namespace lunacattus::encoder {

    void EncoderCppEngine::InitBackend(const char *native_lib_path) {
        ggml_backend_load_all_from_path(native_lib_path);
        llama_backend_init();
    }

    EncoderCppEngine::~EncoderCppEngine() {
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

    int EncoderCppEngine::LoadModel(const char *model_path) {
        if (model_ != nullptr) {
            LOGW("%s: model already loaded, freeing", __func__);
            llama_model_free(model_);
            model_ = nullptr;
        }
        const auto model_params = llama_model_default_params();
        model_ = llama_model_load_from_file(model_path, model_params);
        if (model_ == nullptr) {
            LOGE("%s: llama_model_load_from_file() failed", __func__);
            return 1;
        }
        if (LoadClassificationWeights(model_path) != 0) {
            llama_model_free(model_);
            model_ = nullptr;
            return 1;
        }
        return 0;
    }

    int EncoderCppEngine::LoadClassificationWeights(const char *model_path) {
        // 只读元数据，不加载张量数据到 ggml_context（避免重复加载 400MB 权重）
        const struct gguf_init_params params = {/*.no_alloc =*/ true, /*.ctx =*/ nullptr};
        struct gguf_context *gguf_ctx = gguf_init_from_file(model_path, params);
        if (!gguf_ctx) {
            LOGE("%s: gguf_init_from_file failed", __func__);
            return 1;
        }

        // --- pooler 从 KV metadata 读取 ---
        const int64_t pw_idx = gguf_find_key(gguf_ctx, "bert.classifier.pooler_weight");
        const int64_t pb_idx = gguf_find_key(gguf_ctx, "bert.classifier.pooler_bias");
        if (pw_idx < 0 || pb_idx < 0) {
            LOGE("%s: pooler KV not found in GGUF", __func__);
            gguf_free(gguf_ctx);
            return 1;
        }
        const size_t pb_n = gguf_get_arr_n(gguf_ctx, pb_idx);
        const size_t pw_n = gguf_get_arr_n(gguf_ctx, pw_idx);
        n_embd_ = (int) pb_n;
        if (pw_n != (size_t) (n_embd_ * n_embd_)) {
            LOGE("%s: unexpected pooler_weight size %zu", __func__, pw_n);
            gguf_free(gguf_ctx);
            return 1;
        }
        const auto *pw_data = (const float *) gguf_get_arr_data(gguf_ctx, pw_idx);
        const auto *pb_data = (const float *) gguf_get_arr_data(gguf_ctx, pb_idx);
        pooler_w_.assign(pw_data, pw_data + pw_n);
        pooler_b_.assign(pb_data, pb_data + pb_n);

        // --- 分类头从 KV metadata 读取（float32，不受量化影响）---
        const int64_t cw_idx = gguf_find_key(gguf_ctx, "bert.classifier.output_weight");
        const int64_t cb_idx = gguf_find_key(gguf_ctx, "bert.classifier.output_bias");
        if (cw_idx < 0 || cb_idx < 0) {
            LOGE("%s: cls.output KV not found in GGUF", __func__);
            gguf_free(gguf_ctx);
            return 1;
        }
        const size_t cb_n = gguf_get_arr_n(gguf_ctx, cb_idx);
        const size_t cw_n = gguf_get_arr_n(gguf_ctx, cw_idx);
        n_cls_ = (int) cb_n;
        if (cw_n != (size_t) (n_cls_ * n_embd_)) {
            LOGE("%s: unexpected output_weight size %zu", __func__, cw_n);
            gguf_free(gguf_ctx);
            return 1;
        }
        const auto *cw_data = (const float *) gguf_get_arr_data(gguf_ctx, cw_idx);
        const auto *cb_data = (const float *) gguf_get_arr_data(gguf_ctx, cb_idx);
        cls_w_.assign(cw_data, cw_data + cw_n);
        cls_b_.assign(cb_data, cb_data + cb_n);

        gguf_free(gguf_ctx);
        LOGI("%s: pooler [%d×%d], cls_head [%d×%d]", __func__, n_embd_, n_embd_, n_cls_, n_embd_);
        return 0;
    }

    int EncoderCppEngine::Prepare() {
        if (model_ == nullptr) {
            LOGE("%s: model must be loaded first", __func__);
            return 1;
        }

        if (context_ != nullptr) {
            llama_free(context_);
            context_ = nullptr;
        }

        const int n_thread = std::max(kNThreadsMin,
                                      std::min(kNThreadsMax,
                                               static_cast<int>(sysconf(_SC_NPROCESSORS_ONLN)) -
                                               kNThreadsHeadroom));
        LOGI("%s: n_thread=%d", __func__, n_thread);

        // 编码器模型的训练序列长度，通常 BERT 为 512
        const int ctx_size = std::min(kMaxSeqLen, (int) llama_model_n_ctx_train(model_));

        llama_context_params ctx_params = llama_context_default_params();
        ctx_params.n_ctx = ctx_size;
        ctx_params.n_batch = kBatchSize;
        ctx_params.n_ubatch = kBatchSize;
        ctx_params.n_threads = n_thread;
        ctx_params.n_threads_batch = n_thread;
        ctx_params.embeddings = true;
        ctx_params.pooling_type = LLAMA_POOLING_TYPE_CLS; // CLS pooling 返回 token[0] 的 768 维 embedding，供手动应用 pooler+分类头

        context_ = llama_init_from_model(model_, ctx_params);
        if (context_ == nullptr) {
            LOGE("%s: llama_init_from_model() failed", __func__);
            return 1;
        }

        batch_ = llama_batch_init(kBatchSize, 0, 1);
        LOGI("%s: ready (ctx_size=%d)", __func__, ctx_size);
        return 0;
    }

    void EncoderCppEngine::Unload() {
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

    void EncoderCppEngine::ShutDown() {
        llama_backend_free();
    }

    bool EncoderCppEngine::IsReady() const {
        return model_ != nullptr && context_ != nullptr;
    }

    std::vector<int> EncoderCppEngine::Tokenize(const std::string &text) {
        // add_special=true  → 添加 [CLS] 前缀和 [SEP] 后缀
        // parse_special=false → 用户文本按普通文本处理
        return common_tokenize(context_, text, /*add_special=*/true, /*parse_special=*/false);
    }

    int EncoderCppEngine::Encode(const std::vector<int> &tokens) {
        common_batch_clear(batch_);
        for (int i = 0; i < (int) tokens.size(); i++) {
            // want_logit=true：所有 token 均标记为输出，llama_encode 需要此设置
            common_batch_add(batch_, tokens[i], (llama_pos) i, {0}, true);
        }
        const int rc = llama_encode(context_, batch_);
        if (rc != 0) {
            LOGE("%s: llama_encode() failed rc=%d", __func__, rc);
        }
        return rc;
    }

    int EncoderCppEngine::GetTopClassIndex() const {
        if (n_cls_ == 0 || n_embd_ == 0) {
            LOGE("%s: classification weights not loaded", __func__);
            return -1;
        }

        // CLS pooling 返回 token[0] 的 n_embd 维 hidden state
        const float *cls_emb = llama_get_embeddings_seq(context_, 0);
        if (!cls_emb) {
            LOGE("%s: llama_get_embeddings_seq() returned null", __func__);
            return -1;
        }

        // pooler: pooled[i] = tanh(pooler_w[i,:] · cls_emb + pooler_b[i])
        std::vector<float> pooled(n_embd_);
        for (int i = 0; i < n_embd_; i++) {
            float v = pooler_b_[i];
            for (int j = 0; j < n_embd_; j++) {
                v += pooler_w_[i * n_embd_ + j] * cls_emb[j];
            }
            pooled[i] = std::tanh(v);
        }

        // 分类头: logits[i] = cls_w[i,:] · pooled + cls_b[i]
        std::vector<float> logits(n_cls_);
        for (int i = 0; i < n_cls_; i++) {
            float v = cls_b_[i];
            for (int j = 0; j < n_embd_; j++) {
                v += cls_w_[i * n_embd_ + j] * pooled[j];
            }
            logits[i] = v;
        }

        const int top = (int) (std::max_element(logits.begin(), logits.end()) - logits.begin());
        LOGI("%s: n_cls=%d, top=%d, logit=%.4f", __func__, n_cls_, top, logits[top]);
        return top;
    }

} // namespace lunacattus::encoder
