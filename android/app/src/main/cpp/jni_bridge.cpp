/*
 * jni_bridge.cpp — thin JNI adapter for ARIA on Android
 *
 * This file is the ENTIRE JNI layer. There is no inference logic here.
 * Every function is a 1-5 line wrapper that converts JNI types to C types
 * and calls the corresponding aria_c_api.h function.
 *
 * All inference, backend selection, mmap, LoRA, vision, and training logic
 * lives in core/src/aria_engine.cpp. JNI is only here because the JVM
 * cannot call C++ directly — and it does nothing else.
 *
 * Original llama_jni.cpp is kept intact as a reference until this bridge
 * is verified on-device (Galaxy M31 / Exynos 9611). See MULTIPLATFORM_PLAN.md.
 */

#include <jni.h>
#include <android/log.h>
#include "aria_c_api.h"

#define BRIDGE_TAG "AriaBridge"
#define BRIDGE_LOGI(...) __android_log_print(ANDROID_LOG_INFO,  BRIDGE_TAG, __VA_ARGS__)
#define BRIDGE_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, BRIDGE_TAG, __VA_ARGS__)

/* ─── Global engine instance ─────────────────────────────────────────────── */
/* One engine per JNI session. All handles returned to Kotlin are casts of
 * this pointer — callers can use them interchangeably (model handle and
 * context handle are the same engine pointer). */
static aria_engine_t* g_engine = nullptr;

/* ─── Token callback data packed into void* userdata ─────────────────────── */
struct JniTokenCallbackData {
    JNIEnv*   env;
    jobject   callback_obj;     /* Java object implementing TokenCallback     */
    jmethodID on_token_mid;     /* onToken(String) method                    */
};

static void jni_token_cb(const char* token, void* userdata)
{
    if (!userdata || !token) return;
    auto* d = static_cast<JniTokenCallbackData*>(userdata);
    jstring jtok = d->env->NewStringUTF(token);
    if (jtok) {
        d->env->CallVoidMethod(d->callback_obj, d->on_token_mid, jtok);
        d->env->DeleteLocalRef(jtok);
    }
}

/* ─── Training progress callback ─────────────────────────────────────────── */
/* Currently no Kotlin callback for training progress — called from idle bg  */
static void jni_train_progress_cb(int epoch, int total, double loss, void* /*ud*/)
{
    BRIDGE_LOGI("Training: epoch %d/%d loss=%.4f", epoch, total, loss);
}

extern "C" {

/* ═══════════════════════════════════════════════════════════════════════════
 * Model loading — matches nativeLoadModel in LlamaEngine.kt
 * ═════════════════════════════════════════════════════════════════════════ */
JNIEXPORT jlong JNICALL
Java_com_ariaagent_mobile_core_ai_LlamaEngine_nativeLoadModel(
    JNIEnv* env, jobject,
    jstring path_jstr,
    jint    ctx_size,
    jint    n_gpu_layers,
    jstring gpu_backend_jstr,
    jstring memory_mapping_jstr)
{
    if (!g_engine) g_engine = aria_engine_create();
    if (!g_engine) return 0L;

    aria_model_params_t p{};
    p.model_path   = env->GetStringUTFChars(path_jstr,           nullptr);
    p.ctx_size     = (int)ctx_size;
    p.n_gpu_layers = (int)n_gpu_layers;
    p.gpu_backend  = env->GetStringUTFChars(gpu_backend_jstr,    nullptr);
    p.memory_mode  = env->GetStringUTFChars(memory_mapping_jstr, nullptr);

    int64_t handle = aria_engine_load_model(g_engine, &p);

    env->ReleaseStringUTFChars(path_jstr,           p.model_path);
    env->ReleaseStringUTFChars(gpu_backend_jstr,    p.gpu_backend);
    env->ReleaseStringUTFChars(memory_mapping_jstr, p.memory_mode);

    /* Return engine ptr as handle (non-zero = success). */
    return handle ? reinterpret_cast<jlong>(g_engine) : 0L;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * Context creation — matches nativeCreateContext in LlamaEngine.kt
 * ═════════════════════════════════════════════════════════════════════════ */
JNIEXPORT jlong JNICALL
Java_com_ariaagent_mobile_core_ai_LlamaEngine_nativeCreateContext(
    JNIEnv*, jobject,
    jlong    model_handle,
    jint     ctx_size,
    jboolean flash_attn,
    jboolean kv_quant,
    jint     gpu_ubatch)
{
    (void)model_handle;   /* model is already in g_engine */
    if (!g_engine) return 0L;

    aria_ctx_params_t p{};
    p.ctx_size  = (int)ctx_size;
    p.flash_attn = (int)flash_attn;
    p.kv_quant   = (int)kv_quant;
    p.gpu_ubatch = (int)gpu_ubatch;

    int64_t handle = aria_engine_create_context(g_engine, &p);
    return handle ? reinterpret_cast<jlong>(g_engine) : 0L;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * Inference — matches nativeRunInference in LlamaEngine.kt
 * ═════════════════════════════════════════════════════════════════════════ */
JNIEXPORT jstring JNICALL
Java_com_ariaagent_mobile_core_ai_LlamaEngine_nativeRunInference(
    JNIEnv* env, jobject,
    jlong   ctx_handle,
    jstring prompt_jstr,
    jint    max_tokens,
    jfloat  temperature,
    jobject token_callback)
{
    (void)ctx_handle;
    if (!g_engine) return env->NewStringUTF("{\"error\":\"engine not initialized\"}");

    const char* prompt = env->GetStringUTFChars(prompt_jstr, nullptr);

    /* Build token callback glue */
    JniTokenCallbackData cb_data{};
    if (token_callback) {
        cb_data.env          = env;
        cb_data.callback_obj = token_callback;
        jclass cls = env->GetObjectClass(token_callback);
        cb_data.on_token_mid = env->GetMethodID(cls, "onToken", "(Ljava/lang/String;)V");
    }

    /* Accumulate full output for return value */
    std::string output;
    aria_token_cb token_cb_fn = nullptr;

    struct AccumData {
        JniTokenCallbackData* jni_cb;
        std::string*          output;
    };
    AccumData accum{ &cb_data, &output };

    auto accum_and_stream = [](const char* tok, void* ud) {
        auto* a = static_cast<AccumData*>(ud);
        *a->output += tok;
        if (a->jni_cb->callback_obj) jni_token_cb(tok, a->jni_cb);
    };

    aria_gen_params_t p{};
    p.prompt      = prompt;
    p.max_tokens  = (int)max_tokens;
    p.temperature = (float)temperature;
    p.top_p       = 0.9f;

    int rc = aria_engine_generate(g_engine, &p, accum_and_stream, nullptr, &accum);

    env->ReleaseStringUTFChars(prompt_jstr, prompt);

    if (rc != 0) return env->NewStringUTF("{\"error\":\"generate_failed\"}");
    return env->NewStringUTF(output.c_str());
}

/* ═══════════════════════════════════════════════════════════════════════════
 * LoRA adapter — matches nativeLoadLora in LlamaEngine.kt
 * ═════════════════════════════════════════════════════════════════════════ */
JNIEXPORT jboolean JNICALL
Java_com_ariaagent_mobile_core_ai_LlamaEngine_nativeLoadLora(
    JNIEnv* env, jobject,
    jlong   ctx_handle,
    jstring path_jstr,
    jfloat  scale)
{
    (void)ctx_handle;
    if (!g_engine) return JNI_FALSE;

    const char* path = env->GetStringUTFChars(path_jstr, nullptr);
    int ok = aria_engine_load_lora(g_engine, path, (float)scale);
    env->ReleaseStringUTFChars(path_jstr, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * Free model / context — matches nativeFreeModel, nativeFreeContext
 * ═════════════════════════════════════════════════════════════════════════ */
JNIEXPORT void JNICALL
Java_com_ariaagent_mobile_core_ai_LlamaEngine_nativeFreeModel(
    JNIEnv*, jobject, jlong /*model_handle*/)
{
    if (g_engine) aria_engine_unload_model(g_engine);
}

JNIEXPORT void JNICALL
Java_com_ariaagent_mobile_core_ai_LlamaEngine_nativeFreeContext(
    JNIEnv*, jobject, jlong /*ctx_handle*/)
{
    if (g_engine) aria_engine_free_context(g_engine);
}

/* ═══════════════════════════════════════════════════════════════════════════
 * Vision — matches nativeInitVision, nativeFreeVision, nativeRunVisionInference
 * ═════════════════════════════════════════════════════════════════════════ */
JNIEXPORT jlong JNICALL
Java_com_ariaagent_mobile_core_ai_LlamaEngine_nativeInitVision(
    JNIEnv* env, jobject,
    jstring  mmproj_path_jstr,
    jlong    model_handle,
    jboolean flash_attn)
{
    (void)model_handle;
    if (!g_engine) return 0L;

    const char* path = env->GetStringUTFChars(mmproj_path_jstr, nullptr);
    int64_t handle = aria_engine_init_vision(g_engine, path, (int)flash_attn);
    env->ReleaseStringUTFChars(mmproj_path_jstr, path);
    return handle ? reinterpret_cast<jlong>(g_engine) : 0L;
}

JNIEXPORT void JNICALL
Java_com_ariaagent_mobile_core_ai_LlamaEngine_nativeFreeVision(
    JNIEnv*, jobject, jlong /*vision_handle*/)
{
    if (g_engine) aria_engine_free_vision(g_engine);
}

JNIEXPORT jstring JNICALL
Java_com_ariaagent_mobile_core_ai_LlamaEngine_nativeRunVisionInference(
    JNIEnv* env, jobject,
    jlong       ctx_handle,
    jlong       vision_handle,
    jbyteArray  image_bytes,
    jstring     prompt_jstr,
    jint        max_tokens,
    jobject     token_callback)
{
    (void)ctx_handle; (void)vision_handle;
    if (!g_engine) return env->NewStringUTF("{\"error\":\"engine not initialized\"}");

    jsize img_len  = env->GetArrayLength(image_bytes);
    jbyte* img_buf = env->GetByteArrayElements(image_bytes, nullptr);
    const char* prompt = env->GetStringUTFChars(prompt_jstr, nullptr);

    JniTokenCallbackData cb_data{};
    if (token_callback) {
        cb_data.env          = env;
        cb_data.callback_obj = token_callback;
        jclass cls = env->GetObjectClass(token_callback);
        cb_data.on_token_mid = env->GetMethodID(cls, "onToken", "(Ljava/lang/String;)V");
    }

    std::string output;
    struct AccumData { JniTokenCallbackData* jni_cb; std::string* out; };
    AccumData accum{ &cb_data, &output };
    auto cb = [](const char* tok, void* ud) {
        auto* a = static_cast<AccumData*>(ud);
        *a->out += tok;
        if (a->jni_cb->callback_obj) jni_token_cb(tok, a->jni_cb);
    };

    aria_vision_gen_params_t p{};
    p.image_bytes = reinterpret_cast<const unsigned char*>(img_buf);
    p.image_len   = (size_t)img_len;
    p.prompt      = prompt;
    p.max_tokens  = (int)max_tokens;

    int rc = aria_engine_generate_vision(g_engine, &p, cb, nullptr, &accum);

    env->ReleaseByteArrayElements(image_bytes, img_buf, JNI_ABORT);
    env->ReleaseStringUTFChars(prompt_jstr, prompt);

    if (rc != 0) return env->NewStringUTF("{\"error\":\"vision_generate_failed\"}");
    return env->NewStringUTF(output.c_str());
}

/* ═══════════════════════════════════════════════════════════════════════════
 * Status queries — matches nativeGetToksPerSec, nativeGetMemoryMb
 * ═════════════════════════════════════════════════════════════════════════ */
JNIEXPORT jdouble JNICALL
Java_com_ariaagent_mobile_core_ai_LlamaEngine_nativeGetToksPerSec(
    JNIEnv*, jobject)
{
    return g_engine ? (jdouble)aria_engine_toks_per_sec(g_engine) : 0.0;
}

JNIEXPORT jdouble JNICALL
Java_com_ariaagent_mobile_core_ai_LlamaEngine_nativeGetMemoryMb(
    JNIEnv*, jobject)
{
    return g_engine ? (jdouble)aria_engine_memory_mb(g_engine) : 0.0;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * On-device training — matches nativeTrainLora in LoraTrainer.kt
 * ═════════════════════════════════════════════════════════════════════════ */
JNIEXPORT jboolean JNICALL
Java_com_ariaagent_mobile_core_rl_LoraTrainer_nativeTrainLora(
    JNIEnv* env, jobject,
    jstring model_path_jstr,
    jstring dataset_path_jstr,
    jstring output_path_jstr,
    jint    rank,
    jint    epochs)
{
    if (!g_engine) g_engine = aria_engine_create();
    if (!g_engine) return JNI_FALSE;

    aria_train_params_t p{};
    p.model_path   = env->GetStringUTFChars(model_path_jstr,   nullptr);
    p.dataset_path = env->GetStringUTFChars(dataset_path_jstr, nullptr);
    p.output_path  = env->GetStringUTFChars(output_path_jstr,  nullptr);
    p.rank         = (int)rank;
    p.epochs       = (int)epochs;

    int ok = aria_engine_train(g_engine, &p, jni_train_progress_cb, nullptr);

    env->ReleaseStringUTFChars(model_path_jstr,   p.model_path);
    env->ReleaseStringUTFChars(dataset_path_jstr, p.dataset_path);
    env->ReleaseStringUTFChars(output_path_jstr,  p.output_path);

    return ok ? JNI_TRUE : JNI_FALSE;
}

/* ═══════════════════════════════════════════════════════════════════════════
 * SIMD math — matches EmbeddingEngine + PolicyNetwork JNI calls
 * ═════════════════════════════════════════════════════════════════════════ */
JNIEXPORT jfloat JNICALL
Java_com_ariaagent_mobile_core_memory_EmbeddingEngine_nativeCosineSimilarity(
    JNIEnv* env, jobject,
    jfloatArray a_arr, jfloatArray b_arr)
{
    jint n = env->GetArrayLength(a_arr);
    jfloat* a = env->GetFloatArrayElements(a_arr, nullptr);
    jfloat* b = env->GetFloatArrayElements(b_arr, nullptr);
    float r = aria_cosine_similarity(a, b, (int)n);
    env->ReleaseFloatArrayElements(a_arr, a, JNI_ABORT);
    env->ReleaseFloatArrayElements(b_arr, b, JNI_ABORT);
    return r;
}

JNIEXPORT jfloatArray JNICALL
Java_com_ariaagent_mobile_core_memory_EmbeddingEngine_nativeL2Normalize(
    JNIEnv* env, jobject,
    jfloatArray v_arr)
{
    jint n = env->GetArrayLength(v_arr);
    jfloat* v = env->GetFloatArrayElements(v_arr, nullptr);

    jfloatArray result = env->NewFloatArray(n);
    jfloat* out = env->GetFloatArrayElements(result, nullptr);
    for (int i = 0; i < n; i++) out[i] = v[i];
    aria_l2_normalize(out, (int)n);

    env->ReleaseFloatArrayElements(v_arr, v, JNI_ABORT);
    env->ReleaseFloatArrayElements(result, out, 0);
    return result;
}

JNIEXPORT jfloat JNICALL
Java_com_ariaagent_mobile_core_memory_EmbeddingEngine_nativeDotProduct(
    JNIEnv* env, jobject,
    jfloatArray a_arr, jfloatArray b_arr)
{
    jint n = env->GetArrayLength(a_arr);
    jfloat* a = env->GetFloatArrayElements(a_arr, nullptr);
    jfloat* b = env->GetFloatArrayElements(b_arr, nullptr);
    float r = aria_dot_product(a, b, (int)n);
    env->ReleaseFloatArrayElements(a_arr, a, JNI_ABORT);
    env->ReleaseFloatArrayElements(b_arr, b, JNI_ABORT);
    return r;
}

JNIEXPORT jfloatArray JNICALL
Java_com_ariaagent_mobile_core_rl_PolicyNetwork_nativeMatVecRelu(
    JNIEnv* env, jobject,
    jfloatArray W_arr, jfloatArray x_arr,
    jint rows, jint cols)
{
    jfloat* W = env->GetFloatArrayElements(W_arr, nullptr);
    jfloat* x = env->GetFloatArrayElements(x_arr, nullptr);
    jfloatArray out_arr = env->NewFloatArray(rows);
    jfloat* out = env->GetFloatArrayElements(out_arr, nullptr);
    aria_mat_vec_relu(W, x, out, (int)rows, (int)cols);
    env->ReleaseFloatArrayElements(W_arr, W, JNI_ABORT);
    env->ReleaseFloatArrayElements(x_arr, x, JNI_ABORT);
    env->ReleaseFloatArrayElements(out_arr, out, 0);
    return out_arr;
}

JNIEXPORT jfloatArray JNICALL
Java_com_ariaagent_mobile_core_rl_PolicyNetwork_nativeSoftmax(
    JNIEnv* env, jobject,
    jfloatArray logits_arr)
{
    jint n = env->GetArrayLength(logits_arr);
    jfloat* logits = env->GetFloatArrayElements(logits_arr, nullptr);
    jfloatArray out_arr = env->NewFloatArray(n);
    jfloat* out = env->GetFloatArrayElements(out_arr, nullptr);
    for (int i = 0; i < n; i++) out[i] = logits[i];
    aria_softmax(out, (int)n);
    env->ReleaseFloatArrayElements(logits_arr, logits, JNI_ABORT);
    env->ReleaseFloatArrayElements(out_arr, out, 0);
    return out_arr;
}

} /* extern "C" */
