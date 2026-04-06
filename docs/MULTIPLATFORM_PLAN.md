# ARIA Multiplatform Migration Plan
# C API Core — Android + Windows (+ future platforms)

> **Ground rules**
> - No existing Android files are removed until the cross-platform layer is fully verified on both targets
> - Android remains the primary target throughout — it never regresses
> - All new shared code lives in `core/` alongside the existing `android/` tree
> - JNI is kept only where Kotlin is genuinely mandatory (UI, lifecycle, permissions)

---

## 1. The Core Insight — Why C, Not C++ API

JNI exists for one reason: the JVM cannot call C++ directly.
But it **can** call C (`extern "C"`) — and so can everything else:

| Platform / Language | How it calls the core |
|---|---|
| Android (Kotlin) | JNI → `aria_c_api.h` (1-line wrappers, no logic) |
| Windows (C++ / WinUI 3) | Direct `#include "aria_c_api.h"` — no JNI |
| Linux CLI | Direct `#include "aria_c_api.h"` |
| Python | `ctypes.cdll` → `aria_c_api.h` |
| Rust | `bindgen` → `aria_c_api.h` |
| Swift / iOS | Direct C import → `aria_c_api.h` |
| C# (WPF / MAUI) | P/Invoke → `aria_c_api.h` |

This is exactly the pattern llama.cpp itself uses.
`llama.h` is a **pure C header** even though all implementation is C++.
`llama_load_model()`, `llama_new_context_with_model()` etc. are all `extern "C"`.
We copy that pattern at the ARIA layer.

### What Kotlin is genuinely needed for (non-negotiable Android)
- Jetpack Compose UI (screen layout, navigation, theming)
- `Activity` / `Service` lifecycle (`onCreate`, `onDestroy`, foreground service)
- Android permissions (`requestPermissions`, `checkSelfPermission`)
- `NotificationManager`, `WorkManager`, `ContentResolver`
- `DownloadManager` / `okhttp` for model downloads

### What Kotlin is NOT needed for (move to C/C++)
- Inference engine — llama.cpp already pure C
- Tokenization — llama.cpp `llama_tokenize()` is C
- Math / SIMD — `arm_neon.h` is C, not Kotlin
- File I/O for model loading — `mmap()` / `std::fstream` is C/C++
- Backend selection (Vulkan / OpenCL / CPU) — ggml registry is C
- Multimodal image encoding — mtmd is C++, exposed through C API
- HTTP server for local API — cpp-httplib is already in the project
- JSON — nlohmann/json or cJSON, pure C/C++
- Embeddings, cosine similarity — currently NEON C, stays C

---

## 2. Current State vs Target State

```
CURRENT                                 TARGET
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Kotlin UI                               Kotlin UI
    ↓ JNI (heavy, logic-filled)             ↓ JNI (trivial 1-line wrappers)
llama_jni.cpp                           jni_bridge.cpp
  ├─ model loading logic                    ↓ calls C API
  ├─ inference loop                     aria_c_api.h  ← stable C interface
  ├─ backend selection                      ↓ implemented by
  ├─ mmap setup                         aria_engine.cpp (C++17)
  ├─ token callbacks                        ↓ calls
  └─ status tracking                    llama.h (already C API)
                                            ↓
                                        ggml backends (Vulkan/OpenCL/CPU)

                                        Windows/Linux/others:
                                        main.cpp / WinUI
                                            ↓ direct include — no JNI
                                        aria_c_api.h
```

---

## 3. The C API Design (`core/include/aria_c_api.h`)

```c
/*
 * aria_c_api.h — ARIA inference engine public C interface
 *
 * Stable ABI. No C++ types. No JNI types. No platform headers.
 * Implemented in aria_engine.cpp (C++17 internally).
 * Called directly on all platforms; JNI is a thin adapter on Android only.
 */
#pragma once

#ifdef __cplusplus
extern "C" {
#endif

#include <stdint.h>
#include <stddef.h>

/* ── Opaque handle — never exposed directly ──────────────────────────────── */
typedef struct aria_engine aria_engine_t;

/* ── Model loading ──────────────────────────────────────────────────────── */
typedef struct {
    const char* model_path;       /* path to .gguf file                      */
    int         ctx_size;         /* context window size (tokens)            */
    int         n_gpu_layers;     /* layers offloaded to GPU (99 = all)      */
    const char* gpu_backend;      /* "vulkan" | "opencl" | "cpu" | "auto"   */
    const char* memory_mode;      /* "mmap" | "heap"                         */
} aria_model_params_t;

aria_engine_t* aria_engine_create(void);
int            aria_engine_load_model(aria_engine_t* e,
                                      const aria_model_params_t* p);
void           aria_engine_unload(aria_engine_t* e);
void           aria_engine_destroy(aria_engine_t* e);

/* ── Inference ──────────────────────────────────────────────────────────── */
typedef struct {
    const char* prompt;
    const char* image_path;       /* NULL = text-only                        */
    int         max_tokens;
    float       temperature;
    float       top_p;
} aria_gen_params_t;

/*
 * Callbacks — called from the inference thread.
 * on_token: receives each decoded token as a UTF-8 string
 * on_status: periodic stats (toks/sec, memory MB)
 * userdata:  caller-owned pointer passed through untouched
 */
typedef void (*aria_token_cb)(const char* token,  void* userdata);
typedef void (*aria_status_cb)(double toks_per_sec, double mem_mb, void* userdata);

int  aria_engine_generate(aria_engine_t*        e,
                          const aria_gen_params_t* p,
                          aria_token_cb          on_token,
                          aria_status_cb         on_status,
                          void*                  userdata);
void aria_engine_stop(aria_engine_t* e);

/* ── Math utilities (platform-abstracted SIMD) ──────────────────────────── */
float aria_cosine_similarity(const float* a, const float* b, int n);
void  aria_l2_normalize(float* v, int n);
float aria_dot_product(const float* a, const float* b, int n);
void  aria_softmax(float* logits, int n);
void  aria_mat_vec_relu(const float* W, const float* x,
                        float* out, int rows, int cols);

/* ── Status / info ──────────────────────────────────────────────────────── */
int    aria_engine_is_loaded(const aria_engine_t* e);
double aria_engine_toks_per_sec(const aria_engine_t* e);
double aria_engine_memory_mb(const aria_engine_t* e);
const char* aria_version(void);   /* returns "1.0.0" — semver string         */

#ifdef __cplusplus
}
#endif
```

### Why this design is correct

- **Opaque handle** (`aria_engine_t*`) — callers never see `llama_model*` or
  `llama_context*`. The C++ impl details stay hidden. ABI is stable even if
  llama.cpp internals change.
- **Plain C types only** — `int`, `float`, `const char*`. No `std::string`,
  no `std::function`, no C++ templates crossing the boundary.
- **Callbacks with `void* userdata`** — the standard C pattern for closures.
  On Android, `userdata` holds the JNI `jobject` global ref for Kotlin callbacks.
  On Windows, it holds a `std::function*` or a window handle, caller's choice.
- **No `#include` of llama.h** in the public header — consumers never need
  the llama.cpp headers to use this API.

---

## 4. How JNI Becomes Trivial

With the C API in place, `jni_bridge.cpp` is no longer logic — it's glue:

```cpp
// android/app/src/main/cpp/jni_bridge.cpp
// This is the ENTIRE JNI file. No inference logic here at all.

#include <jni.h>
#include "aria_c_api.h"

// Global engine instance for this JNI session
static aria_engine_t* g_engine = nullptr;

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_ariaagent_mobile_core_ai_LlamaEngine_nativeLoadModel(
    JNIEnv* env, jobject, jstring path, jint ctx, jint layers,
    jstring backend, jstring memmode)
{
    if (!g_engine) g_engine = aria_engine_create();

    aria_model_params_t p{};
    p.model_path   = env->GetStringUTFChars(path,    nullptr);
    p.ctx_size     = ctx;
    p.n_gpu_layers = layers;
    p.gpu_backend  = env->GetStringUTFChars(backend, nullptr);
    p.memory_mode  = env->GetStringUTFChars(memmode, nullptr);

    return (jlong)aria_engine_load_model(g_engine, &p);
}

JNIEXPORT void JNICALL
Java_com_ariaagent_mobile_core_ai_LlamaEngine_nativeGenerate(
    JNIEnv* env, jobject thiz, jstring prompt, jint max_tok,
    jfloat temp, jfloat top_p)
{
    aria_gen_params_t p{};
    p.prompt      = env->GetStringUTFChars(prompt, nullptr);
    p.max_tokens  = max_tok;
    p.temperature = temp;
    p.top_p       = top_p;

    // userdata holds a JNI global ref to the Kotlin LlamaEngine object
    // so the callback can call back into Kotlin via env->CallVoidMethod()
    jobject cb_ref = env->NewGlobalRef(thiz);

    aria_engine_generate(g_engine, &p,
        [](const char* tok, void* ud) {
            // call Kotlin: LlamaEngine.onToken(String)
            JNIEnv* e = /* attach thread */;
            jstring jtok = e->NewStringUTF(tok);
            e->CallVoidMethod((jobject)ud, /* onToken method id */, jtok);
        },
        [](double tps, double mb, void* ud) { /* status callback */ },
        cb_ref
    );
}

JNIEXPORT void JNICALL
Java_com_ariaagent_mobile_core_ai_LlamaEngine_nativeStop(
    JNIEnv*, jobject) { aria_engine_stop(g_engine); }

JNIEXPORT void JNICALL
Java_com_ariaagent_mobile_core_ai_LlamaEngine_nativeUnload(
    JNIEnv*, jobject) { aria_engine_unload(g_engine); }

JNIEXPORT jfloat JNICALL
Java_com_ariaagent_mobile_core_ai_LlamaEngine_nativeCosineSimilarity(
    JNIEnv* env, jobject, jfloatArray a, jfloatArray b, jint n)
{
    float* pa = env->GetFloatArrayElements(a, nullptr);
    float* pb = env->GetFloatArrayElements(b, nullptr);
    float  r  = aria_cosine_similarity(pa, pb, n);
    env->ReleaseFloatArrayElements(a, pa, JNI_ABORT);
    env->ReleaseFloatArrayElements(b, pb, JNI_ABORT);
    return r;
}

} // extern "C"
```

Compare that to the current `llama_jni.cpp` which has hundreds of lines of
model loading, mmap setup, backend routing, and inference loop logic all mixed
with JNI boilerplate. With the C API layer, JNI becomes mechanical.

---

## 5. Windows Uses the C API Directly (No JNI)

```cpp
// windows/main.cpp — CLI, no JNI anywhere in sight

#include "aria_c_api.h"
#include <stdio.h>
#include <string.h>

int main(int argc, char* argv[]) {
    if (argc < 3) {
        printf("Usage: aria <model.gguf> <prompt>\n");
        return 1;
    }

    aria_engine_t* e = aria_engine_create();

    aria_model_params_t mp = {
        .model_path   = argv[1],
        .ctx_size     = 4096,
        .n_gpu_layers = 99,
        .gpu_backend  = "auto",   /* picks Vulkan/CUDA/CPU automatically */
        .memory_mode  = "mmap"
    };

    if (!aria_engine_load_model(e, &mp)) {
        printf("Failed to load model\n");
        return 1;
    }

    aria_gen_params_t gp = {
        .prompt      = argv[2],
        .image_path  = NULL,
        .max_tokens  = 512,
        .temperature = 0.7f,
        .top_p       = 0.9f
    };

    aria_engine_generate(e, &gp,
        [](const char* tok, void*) { printf("%s", tok); fflush(stdout); },
        [](double tps, double mb, void*) { /* status */ },
        NULL
    );

    printf("\n");
    aria_engine_destroy(e);
    return 0;
}
```

No JNI. No Java. No Android. Just C, calling the same engine that Android uses.

---

## 6. Platform-Abstracted SIMD Math

The math functions in `aria_c_api.h` have a single implementation file per
platform. The C API header is identical everywhere — callers never know which
SIMD path ran.

```
core/src/
├── aria_math_arm.c      ← NEON  (Android/ARM64, Raspberry Pi, Apple Silicon)
├── aria_math_x86.c      ← AVX2  (Windows/Linux x86-64, modern Intel/AMD)
└── aria_math_scalar.c   ← pure C (any CPU, fallback, also used for tests)
```

CMake selects the right one:

```cmake
# core/CMakeLists.txt — math source selection
if(CMAKE_SYSTEM_PROCESSOR MATCHES "aarch64|arm64|ARM64")
    target_sources(aria-core PRIVATE src/aria_math_arm.c)
    target_compile_options(aria-core PRIVATE -march=armv8-a)
elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "x86_64|AMD64")
    target_sources(aria-core PRIVATE src/aria_math_x86.c)
    target_compile_options(aria-core PRIVATE -mavx2 -mfma)
else()
    target_sources(aria-core PRIVATE src/aria_math_scalar.c)
endif()
```

On Android the NEON path compiles exactly as `aria_math.cpp` does today —
same intrinsics, same performance. The difference is it's now called through
the C API so Windows gets an equivalent SSE2/AVX path with zero code duplication.

---

## 7. Revised Directory Structure

```
project-root/
│
├── core/                               ← all new work goes here
│   ├── CMakeLists.txt
│   ├── include/
│   │   └── aria_c_api.h                ← single public header, pure C
│   └── src/
│       ├── aria_engine.cpp             ← C++17 implementation (hidden from callers)
│       ├── aria_math_arm.c             ← NEON SIMD
│       ├── aria_math_x86.c             ← AVX2 SIMD
│       └── aria_math_scalar.c          ← portable fallback
│
├── android/                            ← UNCHANGED during migration
│   └── app/src/main/cpp/
│       ├── CMakeLists.txt              ← updated: add_subdirectory(../../../../core)
│       ├── jni_bridge.cpp              ← replaces llama_jni.cpp; thin JNI adapter only
│       ├── aria_math.cpp               ← KEPT until aria_math_arm.c is verified
│       └── llama.cpp/                  ← submodule stays here
│
├── windows/
│   ├── CMakeLists.txt
│   ├── main.cpp                        ← CLI entry (Phase 1)
│   └── ui/                             ← ImGui or WinUI 3 (Phase 2)
│
└── docs/
    └── MULTIPLATFORM_PLAN.md           ← this file
```

---

## 8. Migration Phases

### Phase 1 — Write the C API and engine impl (Android untouched)
- [ ] `core/include/aria_c_api.h` — design is in §3 above
- [ ] `core/src/aria_engine.cpp` — logic extracted from `llama_jni.cpp`
- [ ] `core/src/aria_math_arm.c` — NEON code from `aria_math.cpp`
- [ ] `core/src/aria_math_x86.c` — SSE2/AVX equivalents
- [ ] `core/src/aria_math_scalar.c` — scalar fallback
- [ ] `core/CMakeLists.txt`

### Phase 2 — Wire Android to the C API (zero regression)
- [ ] Update `android/app/src/main/cpp/CMakeLists.txt` to `add_subdirectory(../../../../core)`
- [ ] Write `jni_bridge.cpp` as shown in §4 — thin wrappers only
- [ ] Confirm Android debug APK still builds and runs on Galaxy M31
- [ ] Keep `llama_jni.cpp` and `aria_math.cpp` intact as fallback until confirmed

### Phase 3 — Windows CLI (proves C API is platform-agnostic)
- [ ] `windows/CMakeLists.txt`
- [ ] `windows/main.cpp` as shown in §5
- [ ] GitHub Actions `build-windows.yml` — confirms clean build on `windows-latest`
- [ ] Test: load Q4_K_M model, generate 100 tokens, check output correctness

### Phase 4 — Windows UI
- [ ] ImGui + Vulkan renderer (`windows/ui/`) — shares the Vulkan instance ARIA already uses
- [ ] Features: model picker, GPU backend selector, token stream, stats bar
- [ ] Or: WinUI 3 wrapper calling the C API for a native Windows 11 look

### Phase 5 — Cleanup (ONLY after both platforms verified on CI)
- [ ] Remove `android/app/src/main/cpp/aria_math.cpp` (replaced by `aria_math_arm.c`)
- [ ] Remove `android/app/src/main/cpp/llama_jni.cpp` (replaced by `jni_bridge.cpp`)
- [ ] Push to new GitHub repo

---

## 9. Windows GPU Backends via the Same C API

The `gpu_backend` field in `aria_model_params_t` works identically on both platforms.
The ggml backend registry handles routing — the C API caller just passes a string.

| GPU | String to pass | Notes |
|---|---|---|
| NVIDIA RTX/GTX | `"cuda"` | Fastest on NVIDIA; requires CUDA toolkit |
| Any GPU (NVIDIA/AMD/Intel) | `"vulkan"` | Same GLSL shaders as Android; works out of the box |
| AMD Radeon | `"vulkan"` | ROCm alternative but Vulkan is easier to ship |
| Intel Arc / integrated | `"vulkan"` | Works well |
| No GPU / fallback | `"cpu"` | Fully portable |
| Let ARIA decide | `"auto"` | Tries CUDA → Vulkan → OpenCL → CPU |

---

## 10. What This Unlocks Beyond Windows

Once `aria_c_api.h` exists, other integrations become straightforward:

```python
# Python — zero new C code needed
import ctypes
lib = ctypes.CDLL("libaria-core.so")
engine = lib.aria_engine_create()
# ... call any function in aria_c_api.h
```

```rust
// Rust — bindgen auto-generates bindings from aria_c_api.h
// cargo add bindgen
// then: use aria_sys::*;
```

```csharp
// C# / .NET MAUI — P/Invoke, works on Windows and Android
[DllImport("aria-core")]
static extern IntPtr aria_engine_create();
```

The C API is the universal interface. You write it once and every platform,
language, and tool can reach the engine without any additional bridging code.
