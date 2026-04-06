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
# ARM Cortex-A73 × 4 (big) + Cortex-A53 × 4 (LITTLE) → arm64-v8a, NEON always on
# Mali-G72 MP3 → Vulkan 1.1 + OpenCL 2.0
# No dedicated NPU
#
# ── GPU backends: Vulkan + OpenCL both compiled in ───────────────────────────
#
# Both backends are linked into the same .so. The user selects which one to use
# at runtime via the in-app Settings → GPU Backend chip selector (Vulkan / OpenCL / CPU).
# GGML's backend registry handles the routing; no rebuild needed to switch.
#
# Vulkan (~15–30 tok/s on Mali-G72):
#   Requires glslc (GLSL→SPIR-V compiler) on the BUILD machine.
#   • Firebase Studio: pkgs.shaderc is in .idx/dev.nix — glslc auto-installed.
#   • Android Studio local: sudo apt install glslc
#                       or  https://vulkan.lunarg.com/sdk/home
#   • GitHub Actions CI:  add shaderc to the runner image.
#   Runtime: libvulkan.so — standard on Android 7+ (M31 ships Android 10).
#
# OpenCL (~8–15 tok/s on Mali-G72 MP3, OpenCL 2.0):
#   No glslc needed — kernels compile on-device at first run.
#   Requires: opencl-headers/ vendored in this directory (already present).
#             Python3 on the BUILD machine (for GGML_OPENCL_EMBED_KERNELS —
#             embeds .cl sources as C strings so no runtime file I/O is needed).
#   Runtime: libOpenCL.so — present on M31 via Mali vendor driver.
#   Custom FindOpenCL.cmake (same directory) satisfies find_package(OpenCL REQUIRED)
#   without requiring libOpenCL.so in the NDK sysroot.

set(GGML_VULKAN ON  CACHE BOOL "Vulkan backend — Mali-G72 MP3 Vulkan 1.1 (requires glslc at build time)" FORCE)
set(GGML_OPENCL ON  CACHE BOOL "OpenCL backend — Mali-G72 MP3 OpenCL 2.0 (runtime-switchable with Vulkan)" FORCE)
set(GGML_METAL  OFF CACHE BOOL "Metal is iOS only" FORCE)
set(GGML_CUDA   OFF CACHE BOOL "No CUDA on Android" FORCE)
set(GGML_BLAS   OFF CACHE BOOL "No external BLAS needed for Q4_K_M" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "Cross-compiling for arm64-v8a" FORCE)

# ─── OpenCL build options ─────────────────────────────────────────────────────
# FindOpenCL.cmake in this directory resolves the NDK+Android path.
list(PREPEND CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}")

set(GGML_OPENCL_EMBED_KERNELS    ON  CACHE BOOL   "Embed .cl kernels as C strings (no runtime file I/O on Android)" FORCE)
set(GGML_OPENCL_TARGET_VERSION   "300" CACHE STRING "OpenCL host-API target version (300 = OpenCL 3.0 headers; Mali-G72 is 2.0 at runtime)" FORCE)
set(GGML_OPENCL_USE_ADRENO_KERNELS OFF CACHE BOOL "Adreno-specific matmul kernels — disable for Mali" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
# LLAMA_BUILD_COMMON defaults to LLAMA_STANDALONE, which is OFF when llama.cpp
# is consumed via add_subdirectory (not a top-level build). Force it ON so the
# 'common' CMake target is always produced and can be linked by llama-jni.
set(LLAMA_BUILD_COMMON ON CACHE BOOL "Build llama.cpp common utilities" FORCE)

# ─── Vulkan HPP headers for Android NDK cross-build ──────────────────────────
# ggml-vulkan.cpp includes <vulkan/vulkan.hpp> (Vulkan C++ API wrapper from
# KhronosGroup/Vulkan-Hpp).  This file is NOT shipped with the Android NDK —
# only the C API headers (vulkan/vulkan.h, vulkan/vulkan_core.h) are.
#
# Strategy: the CI runner (ubuntu-24.04) provides vulkan.hpp via libvulkan-dev
# at /usr/include/vulkan/vulkan.hpp.  Because vulkan.hpp is a header-only
# template library (zero architecture-specific code), it is safe to use the
# host copy when cross-compiling for arm64-v8a.
#
# We search /usr/include first.  If found, we prepend it to the system include
# path so the NDK cross-compiler finds <vulkan/vulkan.hpp>.
# If not found (local build without libvulkan-dev), we fall back by disabling
# the Vulkan backend so the rest of the build (OpenCL + CPU) still succeeds.
if(ANDROID)
    # Use if(EXISTS) instead of find_path() — find_path caches its result in
    # CMakeCache.txt and returns the stale "not found" value when the .cxx/
    # directory is restored from a GitHub Actions cache that pre-dates
    # libvulkan-dev being installed.  if(EXISTS) re-evaluates every CMake run.
    if(EXISTS "/usr/include/vulkan/vulkan.hpp")
        message(STATUS "[ARIA] vulkan.hpp found at /usr/include/vulkan/vulkan.hpp — using host headers for Vulkan C++ API")
        include_directories(BEFORE SYSTEM "/usr/include")
    else()
        message(WARNING "[ARIA] vulkan/vulkan.hpp not found on host — disabling Vulkan backend; run: sudo apt-get install -y libvulkan-dev")
        set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
    endif()
endif()

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
# ENABLED: LLAMA_HAS_TRAINING is defined. The JNI function nativeTrainLora()
# in llama_jni.cpp is compiled with the real llama_opt_* / ggml_opt_* code path.
#
# The build uses the vendored llama.cpp submodule at android/app/src/main/cpp/llama.cpp/.
# The llama_jni.cpp training block is guarded by #if defined(LLAMA_HAS_TRAINING)
# so removing this line is safe and causes a graceful fallback:
#   → nativeTrainLora() returns JNI_FALSE
#   → LoraTrainer.kt calls stubTrainLora() — writes metadata-only .bin
#   → No inference regression
#
# If the vendored llama.cpp version does not export these symbols, the linker
# will fail at build time (not at runtime). Remove the definition below to
# disable training and get a guaranteed clean build, then re-enable once the
# API surface is verified.
add_compile_definitions(LLAMA_HAS_TRAINING)
