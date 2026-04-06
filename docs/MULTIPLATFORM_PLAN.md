# ARIA Multiplatform Migration Plan
# Android → Android + Windows (C++ Cross-Platform Core)

> **Ground rules for this migration**
> - No existing Android files are removed until the cross-platform layer is fully working on both targets
> - Android remains the primary target throughout — it never regresses
> - All new shared code lives in `core/` alongside the existing `android/` tree
> - Windows is the first new platform; the same structure will later support Linux/macOS

---

## 1. What We Already Have (Current State)

```
android/app/src/main/cpp/
├── CMakeLists.txt          ← Android NDK build root (arm64-v8a, Vulkan + OpenCL)
├── llama.cpp/              ← llama.cpp submodule (ALREADY fully cross-platform C++)
│   ├── ggml/               ← ggml tensor engine (CPU/Vulkan/OpenCL/CUDA backends)
│   ├── src/                ← llama model loading, inference, sampling
│   └── tools/mtmd/         ← multimodal CLIP vision encoder
├── llama_jni.cpp           ← Android JNI bridge (Android-ONLY: JNIEnv*, jstring…)
├── aria_math.cpp           ← NEON SIMD math (Android/ARM-ONLY: arm_neon.h)
├── FindOpenCL.cmake
└── opencl-headers/
```

### What is already cross-platform
- **llama.cpp + ggml** — pure C++17, builds on Windows/Linux/macOS/Android out of the box with CMake
- **Vulkan backend** — cross-platform (Vulkan SDK available on Windows, Android API 29+)
- **OpenCL backend** — cross-platform (OpenCL SDK on Windows, Mali driver on Android)
- **CPU backend** — fully portable

### What is Android-only right now
| File | Why it's Android-only | Cross-platform fix |
|---|---|---|
| `llama_jni.cpp` | Uses `JNIEnv*`, `jstring`, `jlong`, `__android_log_print` | Extract logic → `aria_engine.cpp`; keep thin JNI wrapper |
| `aria_math.cpp` | Uses `<arm_neon.h>` (ARMv8 SIMD) | Wrap with `#ifdef __ARM_NEON`; add SSE2/AVX path for x86-64 |
| `android/build.gradle` | Gradle/Android Studio build system | Windows uses CMake directly (already the underlying system) |

---

## 2. Target Architecture (End State)

```
Ai-android/                         ← repo root (rename to Aria or keep as is)
│
├── core/                           ← NEW — shared C++ engine (no platform deps)
│   ├── CMakeLists.txt
│   ├── include/
│   │   ├── aria_engine.h           ← public C++ API (no JNI, no Win32)
│   │   └── aria_math.h             ← platform-abstracted SIMD math
│   └── src/
│       ├── aria_engine.cpp         ← inference logic moved out of llama_jni.cpp
│       ├── aria_math_arm.cpp       ← NEON implementation (Android/ARM)
│       └── aria_math_x86.cpp       ← SSE2/AVX implementation (Windows/x86-64)
│
├── android/                        ← UNCHANGED during migration
│   └── app/src/main/cpp/
│       ├── CMakeLists.txt          ← updated: add_subdirectory(../../../../core)
│       ├── jni_bridge.cpp          ← RENAMED from llama_jni.cpp; thin JNI adapter
│       ├── aria_math.cpp           ← KEPT until aria_math_arm.cpp is verified
│       └── llama.cpp/              ← submodule stays here (shared via CMake path)
│
├── windows/                        ← NEW — Windows-specific layer
│   ├── CMakeLists.txt
│   ├── main.cpp                    ← entry point (CLI or WinUI 3 bootstrap)
│   ├── ui/
│   │   └── MainWindow.cpp/.h      ← WinUI 3 or ImGui frontend
│   └── README_BUILD.md             ← Windows build instructions
│
└── llama.cpp/                      ← OPTIONAL: move submodule to repo root
    (so both android/ and windows/ reference it from one place)
```

---

## 3. The Migration Plan (Step by Step)

### Phase 1 — Establish `core/` (no Android changes yet)

**Step 1.1 — Create `core/include/aria_engine.h`**

This is the pure C++ API that both Android JNI and Windows code will call.
No JNI types, no Android types, no Win32 types.

```cpp
// core/include/aria_engine.h
#pragma once
#include <string>
#include <functional>
#include <vector>
#include <cstdint>

namespace aria {

struct ModelParams {
    std::string   model_path;
    int           ctx_size      = 2048;
    int           n_gpu_layers  = 99;
    std::string   gpu_backend   = "auto"; // "vulkan" | "opencl" | "cpu" | "auto"
    std::string   memory_mode   = "mmap"; // "mmap" | "heap"
};

struct GenerateParams {
    std::string   prompt;
    int           max_tokens    = 512;
    float         temperature   = 0.7f;
    float         top_p         = 0.9f;
    std::string   image_path;            // empty = text-only
};

// Callbacks — called from inference thread, no platform dependencies
using TokenCallback   = std::function<void(const std::string& token)>;
using StatusCallback  = std::function<void(double toks_per_sec, double mem_mb)>;

class Engine {
public:
    Engine();
    ~Engine();

    bool     load_model(const ModelParams& params);
    void     generate(const GenerateParams& params,
                      TokenCallback on_token,
                      StatusCallback on_status);
    void     stop();
    void     unload();

    double   toks_per_sec() const;
    double   memory_mb()    const;
    bool     is_loaded()    const;

private:
    struct Impl;
    Impl* impl_ = nullptr;          // pImpl — hides llama.cpp types from callers
};

} // namespace aria
```

**Step 1.2 — Create `core/src/aria_engine.cpp`**

Move the model loading, inference loop, and backend selection logic out of
`llama_jni.cpp` into `aria_engine.cpp`. The JNI file becomes a thin adapter:

```
llama_jni.cpp (before)              jni_bridge.cpp (after)
━━━━━━━━━━━━━━━━━━━━━━━━            ━━━━━━━━━━━━━━━━━━━━━━━━
nativeLoadModel()                   aria::Engine::load_model()   ← engine.cpp
  ├─ llama_model_load()   ──►       ├─ llama_model_load()
  ├─ backend routing      ──►       ├─ backend routing
  └─ mmap setup           ──►       └─ mmap setup
                                    nativeLoadModel() (JNI)  ← thin wrapper
                                      └─ engine.load_model(params)
```

**Step 1.3 — Create `core/include/aria_math.h` + platform impls**

```cpp
// core/include/aria_math.h
#pragma once
#include <vector>

namespace aria::math {
    float cosine_similarity(const float* a, const float* b, int n);
    void  l2_normalize(float* v, int n);
    void  mat_vec_relu(const float* W, const float* x,
                       float* out, int rows, int cols);
    void  softmax(float* logits, int n);
    float dot_product(const float* a, const float* b, int n);
}
```

```cpp
// core/src/aria_math_arm.cpp  — compiled only when __ARM_NEON defined
#ifdef __ARM_NEON
#include <arm_neon.h>
// ... NEON implementations (moved from aria_math.cpp) ...
#endif
```

```cpp
// core/src/aria_math_x86.cpp  — compiled only when SSE2/AVX available
#if defined(__SSE2__) || defined(_M_X64)
#include <immintrin.h>
// ... SSE2/AVX implementations (semantically identical to NEON versions) ...
#endif
```

```cmake
# core/CMakeLists.txt

cmake_minimum_required(VERSION 3.22.1)
project(aria-core CXX)
set(CMAKE_CXX_STANDARD 17)

add_library(aria-core STATIC
    src/aria_engine.cpp
)

# Platform SIMD math
if(CMAKE_SYSTEM_PROCESSOR MATCHES "aarch64|arm64|ARM64")
    target_sources(aria-core PRIVATE src/aria_math_arm.cpp)
elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "x86_64|AMD64")
    target_sources(aria-core PRIVATE src/aria_math_x86.cpp)
else()
    # Scalar fallback — no SIMD
    target_sources(aria-core PRIVATE src/aria_math_scalar.cpp)
endif()

target_include_directories(aria-core PUBLIC include)
target_link_libraries(aria-core PUBLIC llama common mtmd)
```

---

### Phase 2 — Wire `core/` into the Android build (no regressions)

Update `android/app/src/main/cpp/CMakeLists.txt` to pull in `aria-core`:

```cmake
# Add the shared engine (path relative to android/app/src/main/cpp/)
add_subdirectory(../../../../core aria_core_build)

# jni_bridge.cpp replaces llama_jni.cpp — same JNI exports, now delegates to Engine
add_library(llama-jni SHARED jni_bridge.cpp aria_math.cpp)

target_link_libraries(llama-jni
    aria-core       # ← new: shared engine
    llama
    common
    mtmd
    ${LOG_LIB}
)
```

At this point:
- Android still builds exactly as before
- `aria_math.cpp` (NEON) is still compiled in Android — the old path stays
- `jni_bridge.cpp` is `llama_jni.cpp` renamed with JNI boilerplate thinned out
- All inference logic now lives in `aria_engine.cpp`

---

### Phase 3 — Create the Windows build

**Step 3.1 — `windows/CMakeLists.txt`**

```cmake
cmake_minimum_required(VERSION 3.22.1)
project(aria-windows CXX)
set(CMAKE_CXX_STANDARD 17)

# Point to the llama.cpp submodule (same one Android uses)
set(LLAMA_DIR ${CMAKE_SOURCE_DIR}/../android/app/src/main/cpp/llama.cpp)

# Backend selection for Windows
# Vulkan: install Vulkan SDK from https://vulkan.lunarg.com/
# OpenCL: install OpenCL SDK (Intel, AMD, or NVIDIA)
# CUDA:   set GGML_CUDA ON if NVIDIA GPU present
option(ARIA_VULKAN "Enable Vulkan backend" ON)
option(ARIA_OPENCL "Enable OpenCL backend" OFF)
option(ARIA_CUDA   "Enable CUDA backend"   OFF)

set(GGML_VULKAN ${ARIA_VULKAN} CACHE BOOL "" FORCE)
set(GGML_OPENCL ${ARIA_OPENCL} CACHE BOOL "" FORCE)
set(GGML_CUDA   ${ARIA_CUDA}   CACHE BOOL "" FORCE)
set(GGML_METAL  OFF            CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS    OFF   CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF   CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF   CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON   ON    CACHE BOOL "" FORCE)

add_subdirectory(${LLAMA_DIR} llama_build)
add_subdirectory(${LLAMA_DIR}/tools/mtmd mtmd_build)
add_subdirectory(../core aria_core_build)

# Windows entry point
add_executable(aria-windows WIN32
    main.cpp
    ui/MainWindow.cpp
)

target_link_libraries(aria-windows PRIVATE aria-core)

# Windows-specific: link against Vulkan loader if enabled
if(ARIA_VULKAN)
    find_package(Vulkan REQUIRED)
    target_link_libraries(aria-windows PRIVATE Vulkan::Vulkan)
endif()
```

**Step 3.2 — `windows/main.cpp`** (start with CLI, upgrade to WinUI 3 later)

```cpp
// windows/main.cpp — CLI entry point (Phase 3 start)
#include <iostream>
#include <string>
#include "aria_engine.h"

int main(int argc, char* argv[]) {
    if (argc < 2) {
        std::cerr << "Usage: aria-windows <model.gguf> [prompt]\n";
        return 1;
    }

    aria::Engine engine;
    aria::ModelParams params;
    params.model_path   = argv[1];
    params.gpu_backend  = "auto";   // picks Vulkan/CUDA/CPU automatically
    params.n_gpu_layers = 99;

    if (!engine.load_model(params)) {
        std::cerr << "Failed to load model\n";
        return 1;
    }

    aria::GenerateParams gen;
    gen.prompt = (argc > 2) ? argv[2] : "Hello, ARIA!";
    gen.max_tokens = 256;

    engine.generate(gen,
        [](const std::string& tok) { std::cout << tok << std::flush; },
        [](double tps, double mb)  { /* optional status */ }
    );
    std::cout << "\n";
    return 0;
}
```

---

### Phase 4 — Windows UI (after CLI is working)

**Option A — WinUI 3 (recommended for modern Windows)**
- Requires Visual Studio 2022 + Windows App SDK
- Native Windows 11 look-and-feel
- XAML-based layout similar in concept to Jetpack Compose
- Reference: https://learn.microsoft.com/en-us/windows/apps/winui/winui3/

**Option B — Dear ImGui (fastest to prototype)**
- Works with any renderer (Vulkan, DirectX 11/12, OpenGL)
- Same Vulkan instance ARIA already uses for inference → render UI on the same GPU
- No external SDK needed — just a few `.cpp` files
- Reference: https://github.com/ocornut/imgui
- Best for: showing token stream, memory usage bar, model picker

**Option C — Qt 6 (if true cross-platform UI is needed later)**
- Single UI codebase across Windows/Linux/macOS
- Requires Qt 6 license (free for open-source/LGPL projects)
- Most portable but heaviest dependency

**Recommended path**: Start with CLI (Phase 3), add ImGui in Phase 4, evaluate WinUI 3 for the final polished release.

---

## 4. Windows Build Instructions (after Phase 3)

### Prerequisites
```
1. CMake 3.22+         → cmake.org/download
2. Visual Studio 2022  → Community edition is free
   └─ "Desktop development with C++" workload
3. Vulkan SDK          → vulkan.lunarg.com  (for Vulkan backend)
4. Git for Windows     → to manage submodules
```

### Build steps
```batch
cd windows
mkdir build && cd build
cmake .. -G "Visual Studio 17 2022" -A x64 -DARIA_VULKAN=ON
cmake --build . --config Release
aria-windows.exe ..\models\your-model.gguf "Explain quantum computing"
```

### GPU backend notes for Windows
| GPU | Best backend | Flag |
|---|---|---|
| NVIDIA RTX/GTX | CUDA (fastest) or Vulkan | `-DARIA_CUDA=ON` |
| AMD Radeon | Vulkan | `-DARIA_VULKAN=ON` |
| Intel Arc | Vulkan or OpenCL | `-DARIA_VULKAN=ON` |
| Intel integrated | Vulkan or CPU | `-DARIA_VULKAN=ON` |
| No discrete GPU | CPU only | all OFF |

---

## 5. Model Compatibility (same .gguf files, both platforms)

The GGUF model format is platform-neutral. The exact same `.gguf` file runs on:

| Quant format | Android (Vulkan/GPU) | Windows (Vulkan/CUDA) | Recommended for |
|---|---|---|---|
| Q4_K_M | Yes (Mali-G72) | Yes | Best balance — use this |
| Q4_0 | Yes | Yes | Slightly smaller, slightly lower quality |
| Q5_K_M | Yes | Yes | Higher quality, larger |
| Q8_0 | Yes (GPU) | Yes | Near-lossless, needs more RAM |
| BF16 | Yes (Vulkan) | Yes (CUDA native) | Full precision, large GPU only |
| IQ4_NL | Yes (Vulkan shader) | Yes | Good quality at Q4 size |

No model re-download needed when adding Windows support.

---

## 6. What NOT to Touch During Migration

| File / Directory | Reason to leave alone |
|---|---|
| `android/app/src/main/cpp/llama_jni.cpp` | Keep working until `jni_bridge.cpp` is verified on device |
| `android/app/src/main/cpp/aria_math.cpp` | Keep NEON code until `aria_math_arm.cpp` passes same tests |
| `android/app/build.gradle` | Don't touch unless minSdk or NDK version needs changing |
| `android/app/src/main/cpp/llama.cpp/` | Submodule — update only via `git submodule update` |
| All Kotlin source files | UI layer is not part of this migration |

---

## 7. New GitHub Repo Structure (when you're ready)

Since the old `Ai-android` remote has been removed, the new repo should reflect
the broader scope. Suggested name: **`aria-core`** or **`aria-multiplatform`**.

```
New repo structure to push:
  android/   — unchanged Android project
  core/      — new shared C++ engine
  windows/   — new Windows layer
  docs/      — this document + architecture diagrams
  .github/workflows/
    build-android.yml   — existing (already fixed Vulkan API level)
    build-windows.yml   — NEW: CMake + MSVC on windows-latest runner
```

### `build-windows.yml` (GitHub Actions for Windows)
```yaml
name: Build Windows
on: [push, pull_request]
jobs:
  build:
    runs-on: windows-latest
    steps:
      - uses: actions/checkout@v4
        with:
          submodules: recursive
      - name: Install Vulkan SDK
        run: |
          Invoke-WebRequest -Uri "https://sdk.lunarg.com/sdk/download/latest/windows/vulkan-sdk.exe" -OutFile vulkan-sdk.exe
          Start-Process -Wait -FilePath vulkan-sdk.exe -Args "--accept-licenses --default-answer --confirm-command install"
      - name: Configure CMake
        run: cmake -S windows -B build -A x64 -DARIA_VULKAN=ON
      - name: Build
        run: cmake --build build --config Release
      - uses: actions/upload-artifact@v4
        with:
          name: aria-windows
          path: build/Release/aria-windows.exe
```

---

## 8. Migration Checklist

### Phase 1 — Core extraction
- [ ] Create `core/include/aria_engine.h`
- [ ] Create `core/src/aria_engine.cpp` (logic from `llama_jni.cpp`)
- [ ] Create `core/include/aria_math.h`
- [ ] Create `core/src/aria_math_arm.cpp` (NEON, from `aria_math.cpp`)
- [ ] Create `core/src/aria_math_x86.cpp` (SSE2/AVX)
- [ ] Create `core/src/aria_math_scalar.cpp` (fallback, no SIMD)
- [ ] Create `core/CMakeLists.txt`

### Phase 2 — Android wired to core (zero regression)
- [ ] Update `android/app/src/main/cpp/CMakeLists.txt` to add `aria-core`
- [ ] Create `android/app/src/main/cpp/jni_bridge.cpp` (thin JNI wrapper)
- [ ] Verify Android CI still passes (both APKs — debug + release)
- [ ] Run on-device test (Galaxy M31): load model, generate 100 tokens, check tok/s

### Phase 3 — Windows CLI
- [ ] Create `windows/CMakeLists.txt`
- [ ] Create `windows/main.cpp`
- [ ] Confirm builds clean on `windows-latest` GitHub Actions runner
- [ ] Test with Q4_K_M model: load, generate, correct output

### Phase 4 — Windows UI
- [ ] Integrate ImGui (or WinUI 3) in `windows/ui/`
- [ ] Parity features: model picker, GPU backend selector, token stream display
- [ ] Add `build-windows.yml` to GitHub Actions

### Phase 5 — Cleanup (ONLY after both platforms are verified)
- [ ] Remove `android/app/src/main/cpp/aria_math.cpp` (replaced by `aria_math_arm.cpp`)
- [ ] Rename `llama_jni.cpp` → `jni_bridge.cpp` in git
- [ ] Create new GitHub repo and push the full tree
- [ ] Update `replit.md` with new architecture

---

## 9. Key Technical Decisions

**Why pImpl in `aria_engine.h`?**
The `Impl` struct hides all llama.cpp types (`llama_model*`, `llama_context*`, etc.)
from callers. This means `aria_engine.h` has zero dependency on llama.cpp headers,
which is critical: the Windows code can include `aria_engine.h` without needing to
configure all of llama.cpp's CMake before using the API.

**Why keep the llama.cpp submodule under `android/`?**
Moving it to the repo root would require changing the Android CMakeLists.txt path
references. During migration, it stays where it is; both `core/CMakeLists.txt` and
`windows/CMakeLists.txt` reference it via relative paths. After Phase 5 it can be
moved if desired.

**Why Vulkan for Windows instead of CUDA first?**
Vulkan already works in the Android build (now fixed). The same GLSL shader code
(`ggml-vulkan`) compiles for both Android and Windows with zero changes. This gives
you a working GPU path on Windows immediately without requiring an NVIDIA GPU.
CUDA can be added as an optional flag later for NVIDIA users.

**Why SSE2/AVX for `aria_math_x86.cpp`?**
The `aria_math.cpp` NEON functions (cosine similarity, dot product, softmax) operate
on 384-dim float vectors. The SSE2 equivalent (`_mm_dp_ps`, `_mm256_fmadd_ps`) gives
equivalent speedup on x86-64. All modern Windows x86-64 CPUs support at minimum SSE2;
AVX2 is available on Intel Haswell+ (2013) and AMD Zen+ (2018) and should be the
target for new code.
