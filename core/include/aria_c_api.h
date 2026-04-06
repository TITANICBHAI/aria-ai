/*
 * aria_c_api.h — ARIA inference engine public C interface
 *
 * Stable ABI. No C++ types. No JNI types. No platform-specific headers.
 * Implementation is in aria_engine.cpp (C++17 internally, hidden from callers).
 *
 * Called directly on all platforms:
 *   Android  → jni_bridge.cpp wraps these as thin JNI adapters
 *   Windows  → #include "aria_c_api.h" directly, no JNI
 *   Python   → ctypes.CDLL("libaria-core.so")
 *   Rust     → bindgen generates bindings automatically
 *   C#       → P/Invoke
 */
#pragma once

#ifdef __cplusplus
extern "C" {
#endif

#include <stdint.h>
#include <stddef.h>

/* ── Opaque engine handle — never expose internals ───────────────────────── */
typedef struct aria_engine aria_engine_t;

/* ── Model load parameters ───────────────────────────────────────────────── */
typedef struct {
    const char* model_path;     /* path to .gguf file                        */
    int         ctx_size;       /* context window (tokens); 0 → 2048         */
    int         n_gpu_layers;   /* layers offloaded to GPU; 99 = all         */
    const char* gpu_backend;    /* "vulkan" | "opencl" | "cpu" | "auto"     */
    const char* memory_mode;    /* "mmap" | "heap" | "auto"                  */
} aria_model_params_t;

/* ── Context create parameters ───────────────────────────────────────────── */
typedef struct {
    int ctx_size;       /* context length (tokens); 0 → 2048                */
    int flash_attn;     /* 1 = AUTO, 0 = DISABLED                            */
    int kv_quant;       /* 1 = Q8_0 KV cache, 0 = F16                       */
    int gpu_ubatch;     /* Vulkan/OpenCL micro-batch; 0 → 512                */
} aria_ctx_params_t;

/* ── Inference generation parameters ─────────────────────────────────────── */
typedef struct {
    const char* prompt;         /* UTF-8 prompt string                       */
    int         max_tokens;     /* generation token limit                    */
    float       temperature;    /* sampling temperature (0 = greedy)         */
    float       top_p;          /* top-p nucleus sampling (0.0 to 1.0)       */
} aria_gen_params_t;

/* ── Vision (multimodal) generation parameters ───────────────────────────── */
typedef struct {
    const unsigned char* image_bytes;   /* raw JPEG or PNG bytes             */
    size_t               image_len;     /* byte count                        */
    const char*          prompt;        /* text question about the image     */
    int                  max_tokens;    /* generation token limit            */
} aria_vision_gen_params_t;

/* ── Training parameters ─────────────────────────────────────────────────── */
typedef struct {
    const char* model_path;     /* base model path (.gguf)                   */
    const char* dataset_path;   /* JSONL dataset ({"input":"..","output":".."}*/
    const char* output_path;    /* output checkpoint path (.gguf)            */
    int         rank;           /* LoRA rank (unused in full fine-tune)      */
    int         epochs;         /* training epochs                           */
} aria_train_params_t;

/* ── Callbacks ───────────────────────────────────────────────────────────── */
/* Called from the inference thread (same thread as the caller in sync mode). */
typedef void (*aria_token_cb)(const char* token, void* userdata);
typedef void (*aria_status_cb)(double toks_per_sec, double mem_mb, void* userdata);
typedef void (*aria_train_progress_cb)(int epoch, int total_epochs, double loss, void* userdata);

/* ── Engine lifecycle ────────────────────────────────────────────────────── */
aria_engine_t* aria_engine_create(void);
void           aria_engine_destroy(aria_engine_t* e);

/* ── Model + context ─────────────────────────────────────────────────────── */
/*
 * aria_engine_load_model: load GGUF weights, select GPU backend.
 * Returns the model pointer cast to int64_t (non-zero = success), 0 on failure.
 */
int64_t aria_engine_load_model(aria_engine_t* e, const aria_model_params_t* p);

/*
 * aria_engine_create_context: allocate KV cache + compute graph.
 * Must be called after aria_engine_load_model.
 * Returns the context pointer cast to int64_t (non-zero = success), 0 on failure.
 */
int64_t aria_engine_create_context(aria_engine_t* e, const aria_ctx_params_t* p);

void    aria_engine_free_context(aria_engine_t* e);
void    aria_engine_unload_model(aria_engine_t* e);   /* also frees context  */

/* ── Inference ───────────────────────────────────────────────────────────── */
/*
 * Runs synchronously on the calling thread.
 * on_token is called once per generated token (may be NULL).
 * Returns 0 on success, negative on error.
 */
int  aria_engine_generate(aria_engine_t*           e,
                          const aria_gen_params_t*  p,
                          aria_token_cb             on_token,
                          aria_status_cb            on_status,
                          void*                     userdata);

void aria_engine_stop(aria_engine_t* e);   /* thread-safe: sets stop flag  */

/* ── Vision (multimodal) ─────────────────────────────────────────────────── */
/*
 * aria_engine_init_vision: load mmproj GGUF (CLIP encoder + projection layer).
 * model must already be loaded via aria_engine_load_model.
 * Returns non-zero handle on success, 0 on failure.
 */
int64_t aria_engine_init_vision(aria_engine_t* e,
                                const char*    mmproj_path,
                                int            flash_attn);

void aria_engine_free_vision(aria_engine_t* e);

int  aria_engine_generate_vision(aria_engine_t*                 e,
                                 const aria_vision_gen_params_t* p,
                                 aria_token_cb                   on_token,
                                 aria_status_cb                  on_status,
                                 void*                           userdata);

/* ── LoRA adapter ────────────────────────────────────────────────────────── */
/*
 * Loads a LoRA adapter and applies it to the active context.
 * Safe to call multiple times — replaces the previous adapter.
 * scale: 0.0 = off, 0.8 = typical, 1.0 = full influence.
 * Returns 1 on success, 0 on failure.
 */
int aria_engine_load_lora(aria_engine_t* e, const char* path, float scale);

/* ── On-device training ──────────────────────────────────────────────────── */
/*
 * Full-model fine-tune via AdamW optimizer (LLAMA_HAS_TRAINING required).
 * Uses its own model + context — does NOT interfere with the loaded model.
 * on_progress may be NULL. Returns 1 on success, 0 on failure.
 */
int aria_engine_train(aria_engine_t*               e,
                      const aria_train_params_t*   p,
                      aria_train_progress_cb        on_progress,
                      void*                         userdata);

/* ── State persistence (ADB bridge use) ──────────────────────────────────── */
/*
 * Serialize the current KV cache + conversation state to a directory.
 * Used by the ADB bridge: on abort, the PC saves state → adb push → Android.
 * Returns 1 on success, 0 on failure.
 */
int aria_engine_save_state(aria_engine_t* e, const char* dir_path);
int aria_engine_load_state(aria_engine_t* e, const char* dir_path);

/* ── Status / info ───────────────────────────────────────────────────────── */
int         aria_engine_is_loaded(const aria_engine_t* e);
double      aria_engine_toks_per_sec(const aria_engine_t* e);
double      aria_engine_memory_mb(const aria_engine_t* e);
const char* aria_version(void);   /* "1.0.0" — semver                        */

/* ── SIMD math (platform-abstracted) ─────────────────────────────────────── */
/* These are implemented in aria_math_arm.c / aria_math_x86.c / aria_math_scalar.c */
float aria_cosine_similarity(const float* a, const float* b, int n);
void  aria_l2_normalize(float* v, int n);
float aria_dot_product(const float* a, const float* b, int n);
void  aria_softmax(float* logits, int n);
void  aria_mat_vec_relu(const float* W, const float* x, float* out,
                        int rows, int cols);

#ifdef __cplusplus
}
#endif
