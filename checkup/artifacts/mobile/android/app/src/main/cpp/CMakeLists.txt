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
# ARM Cortex-A73 big cores → use 4 threads (arm64-v8a, neon enabled by default)
# No dedicated NPU → CPU only (see Vulkan note below)
#
# NOTE: Vulkan (Mali-G72 MP3) is intentionally DISABLED.
# ggml-vulkan requires glslc (Vulkan SDK shader compiler) present on the build
# machine at CMake configuration time — it calls find_package(Vulkan COMPONENTS
# glslc REQUIRED) which is a hard failure. EAS cloud build servers do not have
# glslc installed, so enabling GGML_VULKAN aborts the build before any C++ is
# compiled. CPU-only inference with Q4_K_M on a 1B model is fast enough for
# on-device ARIA use. Re-enable once EAS custom build images supply glslc.

set(GGML_VULKAN OFF CACHE BOOL "Vulkan disabled — glslc not available on EAS servers" FORCE)
set(GGML_METAL OFF CACHE BOOL "Metal is iOS only" FORCE)
set(GGML_CUDA OFF CACHE BOOL "No CUDA on Android" FORCE)
set(GGML_BLAS OFF CACHE BOOL "No external BLAS needed for Q4_K_M" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "Cross-compiling for arm64-v8a" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
# LLAMA_BUILD_COMMON defaults to LLAMA_STANDALONE, which is OFF when llama.cpp
# is consumed via add_subdirectory (not a top-level build). Force it ON so the
# 'common' CMake target is always produced and can be linked by llama-jni.
set(LLAMA_BUILD_COMMON ON CACHE BOOL "Build llama.cpp common utilities" FORCE)

# ─── Add llama.cpp as a static library ───────────────────────────────────────
add_subdirectory(${LLAMA_DIR} llama_build)

# ─── mtmd: multimodal (vision) library ───────────────────────────────────────
# mtmd is under tools/mtmd/ and is NOT built by add_subdirectory(llama_dir)
# unless LLAMA_BUILD_TOOLS=ON (which requires LLAMA_STANDALONE). We add it
# directly so the 'mtmd' CMake target is available for linking.
# mtmd exposes its own PUBLIC include path (tools/mtmd/) so we do NOT need to
# manually add it to target_include_directories below.
#
# LLAMA_INSTALL_VERSION is set inside llama.cpp's own root CMakeLists.txt scope
# and does NOT propagate back to this parent scope (CMake subdirectory scoping).
# tools/mtmd/CMakeLists.txt:40 calls set_target_properties(... VERSION
# ${LLAMA_INSTALL_VERSION} ...) which silently expands to an empty string here,
# causing CMake to see malformed arguments and abort with "incorrect number of
# arguments". Define a fallback so the property call is always well-formed.
if(NOT DEFINED LLAMA_INSTALL_VERSION)
    set(LLAMA_INSTALL_VERSION "0.0.0")
endif()
add_subdirectory(${LLAMA_DIR}/tools/mtmd mtmd_build)

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

# NOTE: 'common' target is produced by llama.cpp's own CMake when
# LLAMA_BUILD_COMMON=ON (set above). The include path for common/ headers is
# already listed in target_include_directories above.
target_link_libraries(llama-jni
    llama           # llama.cpp core
    common          # llama.cpp common utilities
    mtmd            # multimodal: CLIP image encoder + projection layer
    ${LOG_LIB}      # __android_log_print
)

# ─── Compiler flags ──────────────────────────────────────────────────────────
target_compile_options(llama-jni PRIVATE
    -O3                  # Maximum optimization
    -march=armv8-a       # Exynos 9611 is ARMv8-A
    # NOTE: -mfpu=neon is intentionally omitted. It is a 32-bit ARM flag only.
    # On arm64-v8a (AArch64), NEON is always available and requires no flag.
    # NDK r23+ rejects -mfpu=neon on AArch64 with a compiler error.
    -ffast-math          # Aggressive float optimization for inference
    -fno-finite-math-only # ggml/vec.h rejects __FINITE_MATH_ONLY__ (set by -ffast-math); override it
    -funroll-loops
)

# ─── On-device LoRA training via llama_opt_* API ─────────────────────────────
# DISABLED: The ggml_opt_* / llama_opt_* training API (llama_opt_init,
# llama_opt_epoch, ggml_opt_dataset_t, etc.) has not been verified against the
# vendored llama.cpp version. Enabling without verification may cause compile
# errors if the API surface has changed.
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
