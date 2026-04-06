cmake_minimum_required(VERSION 3.22.1)
project(aria-core VERSION 1.0.0 LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ─── llama.cpp submodule path ────────────────────────────────────────────────
# The submodule lives under android/app/src/main/cpp/llama.cpp/.
# Both the Android build and the Windows build point here via LLAMA_DIR.
# When consumed via add_subdirectory from Android, LLAMA_DIR is already set.
# When built standalone from windows/server/CMakeLists.txt, LLAMA_DIR is passed
# in from the caller.
if(NOT DEFINED LLAMA_DIR)
    # Fallback: resolve from known relative path from the core/ directory
    get_filename_component(REPO_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/.." ABSOLUTE)
    set(LLAMA_DIR "${REPO_ROOT}/android/app/src/main/cpp/llama.cpp")
endif()

# ─── Build aria-core static library ──────────────────────────────────────────
add_library(aria-core STATIC
    src/aria_engine.cpp
)

# ─── Platform SIMD math source selection ─────────────────────────────────────
# One math implementation file is compiled per target architecture.
# cmake -DFORCE_SCALAR=ON to override (useful for tests).
if(FORCE_SCALAR)
    target_sources(aria-core PRIVATE src/aria_math_scalar.c)
    message(STATUS "[aria-core] SIMD: scalar (FORCE_SCALAR=ON)")
elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "aarch64|arm64|ARM64")
    target_sources(aria-core PRIVATE src/aria_math_arm.c)
    target_compile_options(aria-core PRIVATE -march=armv8-a)
    message(STATUS "[aria-core] SIMD: NEON (${CMAKE_SYSTEM_PROCESSOR})")
elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "x86_64|AMD64|amd64")
    target_sources(aria-core PRIVATE src/aria_math_x86.c)
    # SSE2 is the baseline; AVX2 is opt-in for the fast path
    if(MSVC)
        target_compile_options(aria-core PRIVATE /arch:AVX2)
    else()
        target_compile_options(aria-core PRIVATE -msse2 -mavx2 -mfma)
    endif()
    message(STATUS "[aria-core] SIMD: SSE2/AVX2 (${CMAKE_SYSTEM_PROCESSOR})")
else()
    target_sources(aria-core PRIVATE src/aria_math_scalar.c)
    message(STATUS "[aria-core] SIMD: scalar fallback (${CMAKE_SYSTEM_PROCESSOR})")
endif()

# ─── Include paths ────────────────────────────────────────────────────────────
target_include_directories(aria-core
    PUBLIC
        include                     # aria_c_api.h — consumers only need this
    PRIVATE
        ${LLAMA_DIR}/include        # llama.h
        ${LLAMA_DIR}                # common.h is at root
        ${LLAMA_DIR}/common         # common/ headers
        ${LLAMA_DIR}/ggml/include   # ggml-backend.h, ggml.h, etc.
        ${LLAMA_DIR}/ggml/src       # ggml internal headers
        ${LLAMA_DIR}/tools/mtmd     # mtmd.h, mtmd-helper.h
)

# ─── Link llama.cpp libraries ─────────────────────────────────────────────────
# llama, common, and mtmd are expected to already exist as CMake targets
# (added via add_subdirectory in the parent CMakeLists.txt before this one).
target_link_libraries(aria-core
    PRIVATE
        llama
        common
        mtmd
)

# ─── Android log library ─────────────────────────────────────────────────────
if(ANDROID)
    find_library(LOG_LIB log)
    target_link_libraries(aria-core PRIVATE ${LOG_LIB})
    target_compile_definitions(aria-core PRIVATE LLAMA_HAS_TRAINING)
endif()

# ─── Compiler optimization flags ─────────────────────────────────────────────
if(NOT MSVC)
    target_compile_options(aria-core PRIVATE
        -O3
        -ffast-math
        -fno-finite-math-only     # ggml/vec.h requires this override
        -funroll-loops
    )
endif()
