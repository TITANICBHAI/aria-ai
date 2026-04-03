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
# LLAMA_BUILD_COMMON defaults to LLAMA_STANDALONE, which is OFF when llama.cpp
# is consumed via add_subdirectory (not a top-level build). Force it ON so the
# 'common' CMake target is always produced and can be linked by llama-jni.
set(LLAMA_BUILD_COMMON ON CACHE BOOL "Build llama.cpp common utilities" FORCE)

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
    ${LLAMA_DIR}/ggml/src
)

# NOTE: In llama.cpp post-b3800 the 'common' library was split into multiple
# targets. For the pinned b4200 tag the target is still 'common' but the
# include path is now directly ${LLAMA_DIR}/common (already listed above).
# If the build fails with 'common not found', check the tag's CMakeLists.txt
# and update the target name here accordingly.
target_link_libraries(llama-jni
    llama           # llama.cpp core
    common          # llama.cpp common utilities (b4200 layout)
    ${VULKAN_LIB}   # Mali-G72 GPU offload
    ${LOG_LIB}      # __android_log_print
    android         # ANativeWindow (Vulkan surface)
)

# ─── Compiler flags ──────────────────────────────────────────────────────────
target_compile_options(llama-jni PRIVATE
    -O3                  # Maximum optimization
    -march=armv8-a       # Exynos 9611 is ARMv8-A
    # NOTE: -mfpu=neon is intentionally omitted. It is a 32-bit ARM flag only.
    # On arm64-v8a (AArch64), NEON is always available and requires no flag.
    # NDK r23+ rejects -mfpu=neon on AArch64 with a compiler error.
    -ffast-math          # Aggressive float optimization for inference
    -funroll-loops
)

# ─── On-device LoRA training via llama_opt_* API ─────────────────────────────
# DISABLED: The ggml_opt_* / llama_opt_* training API (llama_opt_init,
# llama_opt_epoch, ggml_opt_dataset_t, etc.) is not stable in llama.cpp b4200.
# The API surface changed significantly between b3800 and b4200; enabling this
# without verification causes compile errors.
#
# To re-enable: verify that the pinned llama.cpp tag exports:
#   llama_opt_init, llama_opt_epoch, ggml_opt_dataset_t, ggml_opt_dataset_init,
#   ggml_opt_dataset_data, llama_opt_params, llama_opt_param_filter_all,
#   ggml_opt_get_constant_optimizer_params
# Then uncomment the line below.
#
# add_compile_definitions(LLAMA_HAS_TRAINING)
#
# Without LLAMA_HAS_TRAINING, nativeTrainLora() returns JNI_FALSE and Kotlin
# falls back to stubTrainLora() — safe, no inference regression.
