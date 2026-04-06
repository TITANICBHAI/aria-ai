/*
 * aria_engine.cpp — ARIA inference engine, C++17 implementation
 *
 * Implements aria_c_api.h — all llama.cpp types are hidden behind the
 * opaque aria_engine_t handle. Callers on any platform (Android via JNI,
 * Windows via direct include, Python via ctypes) see only plain C types.
 *
 * Mirrors the logic previously in llama_jni.cpp, now platform-agnostic.
 */

#include "aria_c_api.h"

#include "llama.h"
#include "common.h"
#include "ggml-backend.h"
#include "mtmd.h"
#include "mtmd-helper.h"

#include <atomic>
#include <chrono>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>
#include <sys/stat.h>

#ifdef __ANDROID__
#  include <android/log.h>
#  define ARIA_LOGI(...) __android_log_print(ANDROID_LOG_INFO,  "AriaEngine", __VA_ARGS__)
#  define ARIA_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "AriaEngine", __VA_ARGS__)
#else
#  include <cstdio>
#  define ARIA_LOGI(...) (fprintf(stdout, "[ARIA] " __VA_ARGS__), fputc('\n', stdout), fflush(stdout))
#  define ARIA_LOGE(...) (fprintf(stderr, "[ARIA-ERR] " __VA_ARGS__), fputc('\n', stderr))
#endif

/* ── pImpl: all llama.cpp types live here, never in the public header ─────── */
struct aria_engine {
    llama_model*        model        = nullptr;
    llama_context*      ctx          = nullptr;
    llama_adapter_lora* lora_adapter = nullptr;
    mtmd_context*       vision_ctx   = nullptr;

    std::atomic<bool>   stop_flag{false};
    std::atomic<double> toks_per_sec{0.0};
    std::atomic<double> memory_mb{0.0};

    /* Backend device list built at load time; kept alive for the model lifetime */
    std::vector<ggml_backend_dev_t> selected_devices;
};

/* ─── helpers ────────────────────────────────────────────────────────────── */

static void select_backend_devices(aria_engine_t* e,
                                   const char*    gpu_backend,
                                   int            n_gpu_layers,
                                   llama_model_params& mparams)
{
    e->selected_devices.clear();
    if (n_gpu_layers <= 0) return;

    bool want_vulkan = (strcmp(gpu_backend, "vulkan") == 0);
    bool want_opencl = (strcmp(gpu_backend, "opencl") == 0);
    bool want_auto   = (strcmp(gpu_backend, "auto")   == 0);

    ggml_backend_dev_t cpu_dev = nullptr;

    for (size_t i = 0; i < ggml_backend_dev_count(); i++) {
        ggml_backend_dev_t dev = ggml_backend_dev_get(i);
        auto t = ggml_backend_dev_type(dev);
        const char* name = ggml_backend_dev_name(dev);

        if (t == GGML_BACKEND_DEVICE_TYPE_CPU) {
            cpu_dev = dev;
        } else if (want_auto || want_vulkan) {
            if (strstr(name, "Vulkan") != nullptr || strstr(name, "CUDA") != nullptr) {
                e->selected_devices.push_back(dev);
                ARIA_LOGI("Backend selected: %s", name);
            }
        } else if (want_opencl) {
            if (strstr(name, "OpenCL") != nullptr) {
                e->selected_devices.push_back(dev);
                ARIA_LOGI("Backend selected: OpenCL (%s)", name);
            }
        }
    }

    if (!e->selected_devices.empty() && cpu_dev) {
        e->selected_devices.push_back(cpu_dev);
        e->selected_devices.push_back(nullptr);   /* null-terminate */
        mparams.devices = e->selected_devices.data();
    } else if (e->selected_devices.empty()) {
        ARIA_LOGI("Requested backend '%s' not found — using GGML default priority", gpu_backend);
    }
}

/* ─── Engine lifecycle ───────────────────────────────────────────────────── */

extern "C" {

aria_engine_t* aria_engine_create(void)
{
    llama_backend_init();
    return new aria_engine();
}

void aria_engine_destroy(aria_engine_t* e)
{
    if (!e) return;
    aria_engine_free_context(e);
    aria_engine_unload_model(e);
    aria_engine_free_vision(e);
    delete e;
}

/* ─── Model + context ────────────────────────────────────────────────────── */

int64_t aria_engine_load_model(aria_engine_t* e, const aria_model_params_t* p)
{
    if (!e || !p || !p->model_path) return 0;

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = p->n_gpu_layers;

    /* Memory mapping policy */
    const char* mm = p->memory_mode ? p->memory_mode : "auto";
    if (strcmp(mm, "heap") == 0) {
        mparams.use_mmap  = false;
        mparams.use_mlock = false;
        ARIA_LOGI("Memory: heap (user override)");
    } else if (strcmp(mm, "mmap") == 0) {
        mparams.use_mmap  = true;
        mparams.use_mlock = true;
        ARIA_LOGI("Memory: mmap + mlock attempt");
    } else {
        /* "auto": mmap for large models (> 2 GB), heap for small ones */
        struct stat st{};
        bool large = (stat(p->model_path, &st) == 0) &&
                     (st.st_size > 2LL * 1024 * 1024 * 1024);
        mparams.use_mmap  = large;
        mparams.use_mlock = large;
        ARIA_LOGI("Memory: auto → %s", large ? "mmap+mlock" : "heap (≤2 GB)");
    }

    /* GPU backend selection */
    const char* backend = p->gpu_backend ? p->gpu_backend : "auto";
    if (strcmp(backend, "cpu") != 0) {
        select_backend_devices(e, backend, p->n_gpu_layers, mparams);
    }

    e->model = llama_model_load_from_file(p->model_path, mparams);
    if (!e->model) {
        ARIA_LOGE("Failed to load model from %s", p->model_path);
        return 0;
    }

    e->memory_mb.store(1700.0);   /* RSS estimate; updated after ctx alloc */
    ARIA_LOGI("Model loaded — gpu_layers=%d backend=%s", p->n_gpu_layers, backend);
    return reinterpret_cast<int64_t>(e->model);
}

int64_t aria_engine_create_context(aria_engine_t* e, const aria_ctx_params_t* p)
{
    if (!e || !e->model) return 0;

    int32_t n_ctx = (p && p->ctx_size > 0) ? p->ctx_size : 2048;
    if (n_ctx < 512)  n_ctx = 512;
    if (n_ctx > 4096) n_ctx = 4096;

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx           = (uint32_t)n_ctx;
    cparams.n_batch         = (uint32_t)n_ctx;
    cparams.n_threads       = 6;
    cparams.n_threads_batch = 8;

    if (p) {
        /* micro-batch: clamped [64, n_ctx] */
        uint32_t ubatch = (p->gpu_ubatch > 0) ? (uint32_t)p->gpu_ubatch : 512u;
        if (ubatch < 64u)               ubatch = 64u;
        if (ubatch > (uint32_t)n_ctx)   ubatch = (uint32_t)n_ctx;
        cparams.n_ubatch = ubatch;

        cparams.flash_attn_type = p->flash_attn
            ? LLAMA_FLASH_ATTN_TYPE_AUTO
            : LLAMA_FLASH_ATTN_TYPE_DISABLED;

        if (p->kv_quant) {
            cparams.type_k = GGML_TYPE_Q8_0;
            cparams.type_v = GGML_TYPE_Q8_0;
        }
    }

    e->ctx = llama_init_from_model(e->model, cparams);
    if (!e->ctx) {
        ARIA_LOGE("Failed to create context (n_ctx=%d)", (int)n_ctx);
        return 0;
    }

    ARIA_LOGI("Context created — n_ctx=%d", (int)n_ctx);
    return reinterpret_cast<int64_t>(e->ctx);
}

void aria_engine_free_context(aria_engine_t* e)
{
    if (!e || !e->ctx) return;
    llama_free(e->ctx);
    e->ctx = nullptr;
    ARIA_LOGI("Context freed");
}

void aria_engine_unload_model(aria_engine_t* e)
{
    if (!e) return;
    /* LoRA adapter must be freed before the model */
    if (e->lora_adapter && e->ctx) {
        llama_set_adapters_lora(e->ctx, nullptr, 0, nullptr);
        llama_adapter_lora_free(e->lora_adapter);
        e->lora_adapter = nullptr;
    }
    aria_engine_free_context(e);
    if (e->model) {
        llama_model_free(e->model);
        e->model = nullptr;
        e->memory_mb.store(0.0);
        e->toks_per_sec.store(0.0);
        ARIA_LOGI("Model freed");
    }
}

/* ─── Inference ──────────────────────────────────────────────────────────── */

int aria_engine_generate(aria_engine_t*           e,
                         const aria_gen_params_t*  p,
                         aria_token_cb             on_token,
                         aria_status_cb            on_status,
                         void*                     userdata)
{
    if (!e || !e->ctx || !e->model || !p || !p->prompt) return -1;

    const auto* model = llama_get_model(e->ctx);
    const llama_vocab* vocab = llama_model_get_vocab(model);

    /* Tokenize */
    std::string prompt_str(p->prompt);
    int32_t n_tokens = -llama_tokenize(
        vocab, prompt_str.c_str(), (int32_t)prompt_str.size(),
        nullptr, 0, true, true);
    std::vector<llama_token> tokens(n_tokens);
    int32_t filled = llama_tokenize(
        vocab, prompt_str.c_str(), (int32_t)prompt_str.size(),
        tokens.data(), n_tokens, true, true);
    if (filled < 0) {
        ARIA_LOGE("Tokenization failed");
        return -2;
    }
    tokens.resize(filled);

    const int32_t n_ctx = (int32_t)llama_n_ctx(e->ctx);
    if ((int32_t)tokens.size() >= n_ctx) {
        ARIA_LOGE("Prompt too long: %zu tokens (n_ctx=%d)", tokens.size(), n_ctx);
        return -3;
    }

    llama_memory_clear(llama_get_memory(e->ctx), true);
    llama_decode(e->ctx, llama_batch_get_one(tokens.data(), (int)tokens.size()));

    /* Sampler */
    float temp   = (p->temperature > 0.0f) ? p->temperature : 0.7f;
    float top_pp = (p->top_p > 0.0f && p->top_p <= 1.0f) ? p->top_p : 0.9f;

    llama_sampler* sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(top_pp, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temp));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    e->stop_flag.store(false);
    auto t_start = std::chrono::high_resolution_clock::now();
    int n_gen = 0;
    int max_tok = (p->max_tokens > 0) ? p->max_tokens : 512;

    for (int i = 0; i < max_tok; i++) {
        if (e->stop_flag.load()) break;

        llama_token tok = llama_sampler_sample(sampler, e->ctx, -1);
        if (llama_vocab_is_eog(vocab, tok)) break;

        char buf[256];
        int len = llama_token_to_piece(vocab, tok, buf, sizeof(buf), 0, true);
        if (len < 0) continue;

        buf[len] = '\0';
        if (on_token) on_token(buf, userdata);

        n_gen++;
        llama_decode(e->ctx, llama_batch_get_one(&tok, 1));
    }

    llama_sampler_free(sampler);

    auto t_end = std::chrono::high_resolution_clock::now();
    double elapsed = std::chrono::duration<double>(t_end - t_start).count();
    double tps = (elapsed > 0.0) ? (n_gen / elapsed) : 0.0;
    e->toks_per_sec.store(tps);

    if (on_status) on_status(tps, e->memory_mb.load(), userdata);
    ARIA_LOGI("Generate done — %d tokens in %.2fs (%.1f tok/s)", n_gen, elapsed, tps);
    return 0;
}

void aria_engine_stop(aria_engine_t* e)
{
    if (e) e->stop_flag.store(true);
}

/* ─── Vision ─────────────────────────────────────────────────────────────── */

int64_t aria_engine_init_vision(aria_engine_t* e,
                                const char*    mmproj_path,
                                int            flash_attn)
{
    if (!e || !e->model || !mmproj_path) return 0;

    mtmd_context_params vp = mtmd_context_params_default();
    vp.use_gpu          = true;
    vp.n_threads        = 6;
    vp.print_timings    = false;
    vp.flash_attn_type  = flash_attn
        ? LLAMA_FLASH_ATTN_TYPE_AUTO
        : LLAMA_FLASH_ATTN_TYPE_DISABLED;
    vp.warmup = false;

    e->vision_ctx = mtmd_init_from_file(mmproj_path, e->model, vp);
    if (!e->vision_ctx) {
        ARIA_LOGE("mtmd_init_from_file failed");
        return 0;
    }
    if (!mtmd_support_vision(e->vision_ctx)) {
        ARIA_LOGE("mmproj does not support vision");
        mtmd_free(e->vision_ctx);
        e->vision_ctx = nullptr;
        return 0;
    }

    ARIA_LOGI("Vision context initialized");
    return reinterpret_cast<int64_t>(e->vision_ctx);
}

void aria_engine_free_vision(aria_engine_t* e)
{
    if (!e || !e->vision_ctx) return;
    mtmd_free(e->vision_ctx);
    e->vision_ctx = nullptr;
    ARIA_LOGI("Vision context freed");
}

int aria_engine_generate_vision(aria_engine_t*                  e,
                                const aria_vision_gen_params_t*  p,
                                aria_token_cb                    on_token,
                                aria_status_cb                   on_status,
                                void*                            userdata)
{
    if (!e || !e->ctx || !e->vision_ctx || !p) return -1;

    const auto* model = llama_get_model(e->ctx);
    const llama_vocab* vocab = llama_model_get_vocab(model);

    /* Decode image bytes */
    mtmd_bitmap* bitmap = mtmd_helper_bitmap_init_from_buf(
        e->vision_ctx,
        p->image_bytes, p->image_len);
    if (!bitmap) {
        ARIA_LOGE("Failed to decode image bytes");
        return -2;
    }

    std::string full_prompt = std::string(mtmd_default_marker()) + "\n" +
                              (p->prompt ? p->prompt : "");

    mtmd_input_text input_text;
    input_text.text          = full_prompt.c_str();
    input_text.add_special   = true;
    input_text.parse_special = true;

    mtmd_input_chunks* chunks = mtmd_input_chunks_init();
    const mtmd_bitmap* bitmaps_arr[1] = { bitmap };
    int32_t tok_rc = mtmd_tokenize(e->vision_ctx, chunks, &input_text, bitmaps_arr, 1);
    mtmd_bitmap_free(bitmap);

    if (tok_rc != 0) {
        ARIA_LOGE("mtmd_tokenize failed rc=%d", tok_rc);
        mtmd_input_chunks_free(chunks);
        return -3;
    }

    llama_memory_clear(llama_get_memory(e->ctx), true);
    llama_pos new_n_past = 0;
    int32_t eval_rc = mtmd_helper_eval_chunks(
        e->vision_ctx, e->ctx, chunks,
        0, 0, (int32_t)llama_n_batch(e->ctx),
        true, &new_n_past);
    mtmd_input_chunks_free(chunks);

    if (eval_rc != 0) {
        ARIA_LOGE("mtmd_helper_eval_chunks failed rc=%d", eval_rc);
        return -4;
    }

    /* Sampling loop */
    llama_sampler* sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.1f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    e->stop_flag.store(false);
    auto t_start = std::chrono::high_resolution_clock::now();
    int n_gen = 0;
    int max_tok = (p->max_tokens > 0) ? p->max_tokens : 512;

    for (int i = 0; i < max_tok; i++) {
        if (e->stop_flag.load()) break;

        llama_token tok = llama_sampler_sample(sampler, e->ctx, -1);
        if (llama_vocab_is_eog(vocab, tok)) break;

        char buf[256];
        int len = llama_token_to_piece(vocab, tok, buf, sizeof(buf), 0, true);
        if (len < 0) continue;

        buf[len] = '\0';
        if (on_token) on_token(buf, userdata);

        n_gen++;
        if (llama_decode(e->ctx, llama_batch_get_one(&tok, 1)) != 0) break;
    }

    llama_sampler_free(sampler);

    auto t_end = std::chrono::high_resolution_clock::now();
    double elapsed = std::chrono::duration<double>(t_end - t_start).count();
    double tps = (elapsed > 0.0) ? (n_gen / elapsed) : 0.0;
    e->toks_per_sec.store(tps);

    if (on_status) on_status(tps, e->memory_mb.load(), userdata);
    ARIA_LOGI("Vision generate done — %d tokens in %.2fs (%.1f tok/s)", n_gen, elapsed, tps);
    return 0;
}

/* ─── LoRA ───────────────────────────────────────────────────────────────── */

int aria_engine_load_lora(aria_engine_t* e, const char* path, float scale)
{
    if (!e || !e->ctx || !path) return 0;

    if (e->lora_adapter) {
        llama_set_adapters_lora(e->ctx, nullptr, 0, nullptr);
        llama_adapter_lora_free(e->lora_adapter);
        e->lora_adapter = nullptr;
    }

    auto* model = const_cast<llama_model*>(llama_get_model(e->ctx));
    e->lora_adapter = llama_adapter_lora_init(model, path);
    if (!e->lora_adapter) {
        ARIA_LOGE("Failed to load LoRA adapter from %s", path);
        return 0;
    }

    int32_t rc = llama_set_adapters_lora(e->ctx, &e->lora_adapter, 1, &scale);
    if (rc != 0) {
        ARIA_LOGE("llama_set_adapters_lora failed rc=%d", rc);
        llama_adapter_lora_free(e->lora_adapter);
        e->lora_adapter = nullptr;
        return 0;
    }

    ARIA_LOGI("LoRA adapter loaded (scale=%.2f)", (double)scale);
    return 1;
}

/* ─── Training ───────────────────────────────────────────────────────────── */

int aria_engine_train(aria_engine_t*             e,
                      const aria_train_params_t* p,
                      aria_train_progress_cb      on_progress,
                      void*                       userdata)
{
#if defined(LLAMA_HAS_TRAINING)
    if (!e || !p) return 0;

    (void)p->rank;   /* full fine-tune: rank unused */

    llama_model_params mparams = llama_model_default_params();
    mparams.use_mmap     = false;
    mparams.use_mlock    = false;
    mparams.n_gpu_layers = 0;

    llama_model* train_model = llama_model_load_from_file(p->model_path, mparams);
    if (!train_model) {
        ARIA_LOGE("Training: failed to load model from %s", p->model_path);
        return 0;
    }

    /* Read JSONL dataset */
    struct TrainPair { std::string input, output; };
    std::vector<TrainPair> pairs;
    {
        FILE* f = fopen(p->dataset_path, "r");
        if (!f) {
            ARIA_LOGE("Training: cannot open dataset %s", p->dataset_path);
            llama_model_free(train_model);
            return 0;
        }
        auto extract_str = [](const char* json, const char* key) -> std::string {
            std::string needle = std::string("\"") + key + "\":\"";
            const char* start = strstr(json, needle.c_str());
            if (!start) return {};
            start += needle.size();
            std::string val;
            bool escaped = false;
            for (const char* q = start; *q; ++q) {
                if (escaped) { val += *q; escaped = false; continue; }
                if (*q == '\\') { escaped = true; continue; }
                if (*q == '"')  break;
                val += *q;
            }
            return val;
        };
        char line[8192];
        while (fgets(line, sizeof(line), f)) {
            std::string in  = extract_str(line, "input");
            std::string out = extract_str(line, "output");
            if (!in.empty() && !out.empty()) pairs.push_back({in, out});
        }
        fclose(f);
    }
    if (pairs.empty()) {
        ARIA_LOGE("Training: empty dataset");
        llama_model_free(train_model);
        return 0;
    }

    const uint32_t TRAIN_CTX = 512;
    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = TRAIN_CTX; cparams.n_batch = TRAIN_CTX; cparams.n_ubatch = TRAIN_CTX;
    cparams.n_threads = 4; cparams.n_threads_batch = 4;

    llama_context* train_ctx = llama_init_from_model(train_model, cparams);
    if (!train_ctx) {
        llama_model_free(train_model);
        return 0;
    }

    const llama_vocab* vocab = llama_model_get_vocab(train_model);
    auto tokenize_text = [&](const std::string& text, int max_len) {
        std::vector<llama_token> toks(max_len);
        int n = llama_tokenize(vocab, text.c_str(), (int)text.size(),
                               toks.data(), max_len, true, false);
        if (n < 0) n = 0;
        toks.resize(n);
        return toks;
    };

    std::vector<int32_t> data_buf, lbl_buf;
    for (const auto& pr : pairs) {
        auto inp = tokenize_text(pr.input,  (int)(TRAIN_CTX * 3 / 4));
        auto out = tokenize_text(pr.output, (int)(TRAIN_CTX / 4));
        if (inp.empty() || out.empty()) continue;
        std::vector<llama_token> seq;
        seq.insert(seq.end(), inp.begin(), inp.end());
        seq.insert(seq.end(), out.begin(), out.end());
        if (seq.size() > TRAIN_CTX) seq.resize(TRAIN_CTX);
        size_t slen = seq.size(), ilen = std::min(inp.size(), slen);
        for (size_t i = 0; i < TRAIN_CTX; ++i) {
            data_buf.push_back(i < slen ? (int32_t)seq[i] : 0);
            lbl_buf.push_back((i < ilen || i + 1 >= slen) ? -1 : (int32_t)seq[i+1]);
        }
    }

    int64_t ndata = (int64_t)(data_buf.size() / TRAIN_CTX);
    if (ndata == 0) { llama_free(train_ctx); llama_model_free(train_model); return 0; }

    ggml_opt_dataset_t opt_dataset = ggml_opt_dataset_init(
        GGML_TYPE_I32, GGML_TYPE_I32, TRAIN_CTX, TRAIN_CTX, ndata, 1);
    struct ggml_tensor* dt = ggml_opt_dataset_data(opt_dataset);
    struct ggml_tensor* lt = ggml_opt_dataset_labels(opt_dataset);
    memcpy(dt->data, data_buf.data(), data_buf.size() * sizeof(int32_t));
    memcpy(lt->data, lbl_buf.data(),  lbl_buf.size()  * sizeof(int32_t));

    static ggml_opt_optimizer_params s_opt_pars = {
        { 1e-4f, 0.9f, 0.999f, 1e-8f, 0.0f },
        { 1e-4f, 0.0f },
    };
    struct llama_opt_params lopt_params;
    memset(&lopt_params, 0, sizeof(lopt_params));
    lopt_params.n_ctx_train     = TRAIN_CTX;
    lopt_params.param_filter    = llama_opt_param_filter_all;
    lopt_params.param_filter_ud = nullptr;
    lopt_params.optimizer_type  = GGML_OPT_OPTIMIZER_TYPE_ADAMW;
    lopt_params.get_opt_pars    = ggml_opt_get_constant_optimizer_params;
    lopt_params.get_opt_pars_ud = &s_opt_pars;
    llama_opt_init(train_ctx, train_model, lopt_params);

    ggml_opt_result_t result_train = ggml_opt_result_init();
    bool saved = false;

    for (int ep = 0; ep < p->epochs; ++ep) {
        ggml_opt_result_reset(result_train);
        llama_opt_epoch(train_ctx, opt_dataset, result_train, nullptr, ndata, nullptr, nullptr);
        double loss = 0.0;
        ggml_opt_result_loss(result_train, &loss, nullptr);
        ARIA_LOGI("Training: epoch %d/%d loss=%.4f", ep + 1, p->epochs, loss);
        if (on_progress) on_progress(ep + 1, p->epochs, loss, userdata);
    }

    ggml_opt_result_free(result_train);
    ggml_opt_dataset_free(opt_dataset);

    try {
        llama_model_save_to_file(train_model, p->output_path);
        saved = true;
        ARIA_LOGI("Training: checkpoint saved → %s", p->output_path);
    } catch (...) {
        ARIA_LOGE("Training: failed to save checkpoint");
    }

    llama_free(train_ctx);
    llama_model_free(train_model);
    return saved ? 1 : 0;
#else
    (void)e; (void)p; (void)on_progress; (void)userdata;
    ARIA_LOGE("Training: LLAMA_HAS_TRAINING not defined");
    return 0;
#endif
}

/* ─── State persistence ──────────────────────────────────────────────────── */

int aria_engine_save_state(aria_engine_t* e, const char* dir_path)
{
    if (!e || !e->ctx || !dir_path) return 0;

#if defined(_WIN32)
    _mkdir(dir_path);
#else
    mkdir(dir_path, 0755);
#endif

    /* Build path: dir_path/kv_cache.bin */
    std::string kv_path = std::string(dir_path) + "/kv_cache.bin";
    size_t sz = 0;
    bool ok = llama_state_save_file(e->ctx, kv_path.c_str(),
                                    nullptr, 0, &sz);
    if (!ok) {
        ARIA_LOGE("aria_engine_save_state: llama_state_save_file failed");
        return 0;
    }
    ARIA_LOGI("State saved to %s (%zu bytes)", kv_path.c_str(), sz);
    return 1;
}

int aria_engine_load_state(aria_engine_t* e, const char* dir_path)
{
    if (!e || !e->ctx || !dir_path) return 0;

    std::string kv_path = std::string(dir_path) + "/kv_cache.bin";
    size_t n_token_count_out = 0;
    bool ok = llama_state_load_file(e->ctx, kv_path.c_str(),
                                    nullptr, 0, &n_token_count_out);
    if (!ok) {
        ARIA_LOGE("aria_engine_load_state: llama_state_load_file failed");
        return 0;
    }
    ARIA_LOGI("State loaded from %s", kv_path.c_str());
    return 1;
}

/* ─── Status ─────────────────────────────────────────────────────────────── */

int aria_engine_is_loaded(const aria_engine_t* e)
{
    return (e && e->model) ? 1 : 0;
}

double aria_engine_toks_per_sec(const aria_engine_t* e)
{
    return e ? e->toks_per_sec.load() : 0.0;
}

double aria_engine_memory_mb(const aria_engine_t* e)
{
    return e ? e->memory_mb.load() : 0.0;
}

const char* aria_version(void)
{
    return "1.0.0";
}

} /* extern "C" */
