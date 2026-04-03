cmake_minimum_required(VERSION 3.22.1)

project(aria-llama-jni VERSION 1.0 LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ─── llama.cpp submodule path ────────────────────────────────────────────────
# To add llama.cpp: from artifacts/mobile/android/app/src/main/cpp/
#   git submodule add https://github.com/ggerganov/llama.cpp llama.cpp
#   git submodule update --init --recursive
set(LLAMA_DIR ${CMAKE_SOURCE_DIR}/llama.cpp)

# ─── Hardware flags for Exynos 9611 (Samsung Galaxy M31) ─────────────────────
# Mali-G72 MP3 → Vulkan 1.1 supported → offload all 22 Llama 1B layers
# ARM Cortex-A73 big cores → use 4 threads (arm64-v8a, neon enabled by default)
# No dedicated NPU → CPU + Vulkan only

set(LLAMA_VULKAN ON CACHE BOOL "Enable Vulkan backend for Mali-G72" FORCE)
set(LLAMA_METAL OFF CACHE BOOL "Metal is iOS only" FORCE)
set(LLAMA_CUBLAS OFF CACHE BOOL "No CUDA on Android" FORCE)
set(LLAMA_BLAS OFF CACHE BOOL "No external BLAS needed for Q4_K_M" FORCE)
set(LLAMA_NATIVE OFF CACHE BOOL "Cross-compiling for arm64-v8a" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)

# ─── Add llama.cpp as a static library ───────────────────────────────────────
add_subdirectory(${LLAMA_DIR} llama_build)

# ─── Vulkan SDK (Android NDK bundles headers, but we link against the device lib)
find_library(VULKAN_LIB vulkan)

# ─── Android log library ─────────────────────────────────────────────────────
find_library(LOG_LIB log)

# ─── Our JNI bridge shared library ───────────────────────────────────────────
# Two source files:
#   llama_jni.cpp  — wraps llama.cpp for LLM inference
#   aria_math.cpp  — NEON SIMD math for EmbeddingEngine + PolicyNetwork
add_library(
    llama-jni
    SHARED
    llama_jni.cpp
    aria_math.cpp
)

target_include_directories(llama-jni PRIVATE
    ${LLAMA_DIR}/include
    ${LLAMA_DIR}
    ${LLAMA_DIR}/common
    ${LLAMA_DIR}/ggml/include
)

target_link_libraries(llama-jni
    llama           # llama.cpp core
    common          # llama.cpp common utilities
    ${VULKAN_LIB}   # Mali-G72 GPU offload
    ${LOG_LIB}      # __android_log_print
    android         # ANativeWindow (Vulkan surface)
)

# ─── Compiler flags ──────────────────────────────────────────────────────────
target_compile_options(llama-jni PRIVATE
    -O3                  # Maximum optimization
    -march=armv8-a       # Exynos 9611 is ARMv8-A
    -mfpu=neon           # NEON SIMD (always present on arm64)
    -ffast-math          # Aggressive float optimization for inference
    -funroll-loops
)

# ─── Enable on-device LoRA training via llama_opt_* API ─────────────────────
# Activates the real gradient-descent training path in nativeTrainLora().
# Requires llama.cpp present (already cloned to llama.cpp/).
# Uses ggml_opt_init / llama_opt_init / llama_opt_epoch from ggml-opt.h.
# The ggml-opt.o target is compiled by add_subdirectory(llama_build) above.
add_compile_definitions(LLAMA_HAS_TRAINING)
