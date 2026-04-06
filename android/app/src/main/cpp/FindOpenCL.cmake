# FindOpenCL.cmake — Android NDK override
#
# Placed in android/app/src/main/cpp/ and prepended to CMAKE_MODULE_PATH so that
# CMake finds THIS file before its built-in FindOpenCL.cmake when any subdirectory
# (including llama.cpp/ggml/src/ggml-opencl/) calls find_package(OpenCL [REQUIRED]).
#
# Why a custom finder?
#   The NDK sysroot does not ship libOpenCL.so — it is a vendor/system library
#   provided by the Mali GPU driver at runtime (/vendor/lib64/libOpenCL.so on M31).
#   CMake's built-in FindOpenCL.cmake calls find_library(OpenCL_LIBRARY OpenCL)
#   which searches the NDK sysroot and fails with OpenCL_FOUND=FALSE, aborting the
#   build even though the library WILL be present on the device.
#
# This file satisfies find_package(OpenCL REQUIRED) for Android by:
#   1. Using the Khronos OpenCL headers vendored at
#      android/app/src/main/cpp/opencl-headers/  (downloaded from KhronosGroup/OpenCL-Headers)
#   2. Creating an INTERFACE target OpenCL::OpenCL that passes -lOpenCL to the NDK
#      linker and allows the unresolved shared-library reference at link time
#      (resolved at runtime by the device's Mali driver).
#
# On non-Android hosts the standard CMake FindOpenCL.cmake is used as normal.

if(ANDROID)
    # ── Headers ────────────────────────────────────────────────────────────────
    get_filename_component(_OCL_SELF_DIR "${CMAKE_CURRENT_LIST_FILE}" DIRECTORY)
    set(_OCL_INCLUDE_DIR "${_OCL_SELF_DIR}/opencl-headers")

    if(NOT EXISTS "${_OCL_INCLUDE_DIR}/CL/cl.h")
        message(FATAL_ERROR
            "OpenCL headers not found at ${_OCL_INCLUDE_DIR}/CL/cl.h\n"
            "Run from android/app/src/main/cpp/:\n"
            "  git clone --depth 1 https://github.com/KhronosGroup/OpenCL-Headers opencl-headers\n"
            "Then rebuild.")
    endif()

    # ── INTERFACE target ────────────────────────────────────────────────────────
    # OpenCL::OpenCL is the canonical modern CMake target — ggml-opencl links against it.
    if(NOT TARGET OpenCL::OpenCL)
        add_library(OpenCL::OpenCL INTERFACE IMPORTED GLOBAL)
        target_include_directories(OpenCL::OpenCL INTERFACE "${_OCL_INCLUDE_DIR}")
        # -lOpenCL: NDK linker records the SONAME; device driver resolves it at runtime.
        # -Wl,--allow-shlib-undefined: lets lld proceed even though libOpenCL.so is not
        #   in the NDK sysroot — identical to how -llog / -landroid are handled for
        #   optional system/vendor libraries.
        target_link_options(OpenCL::OpenCL INTERFACE
            -lOpenCL
            -Wl,--allow-shlib-undefined
        )
    endif()

    # ── Variables expected by FindOpenCL consumers ──────────────────────────────
    set(OpenCL_FOUND        TRUE)
    set(OpenCL_INCLUDE_DIRS "${_OCL_INCLUDE_DIR}")
    set(OpenCL_INCLUDE_DIR  "${_OCL_INCLUDE_DIR}")
    set(OpenCL_LIBRARIES    OpenCL::OpenCL)
    set(OpenCL_LIBRARY      OpenCL::OpenCL)
    set(OpenCL_VERSION_STRING  "2.0")
    set(OpenCL_VERSION_MAJOR   2)
    set(OpenCL_VERSION_MINOR   0)

    mark_as_advanced(OpenCL_INCLUDE_DIR OpenCL_LIBRARY)

    message(STATUS "ARIA FindOpenCL: Android — using vendored Khronos headers + runtime -lOpenCL (Mali-G72)")

else()
    # ── Non-Android: delegate to CMake's built-in FindOpenCL.cmake ─────────────
    # Temporarily remove ourselves from MODULE_PATH to avoid infinite recursion,
    # then restore after the built-in finder runs.
    list(REMOVE_ITEM CMAKE_MODULE_PATH "${_OCL_SELF_DIR}")

    # Pass through REQUIRED / QUIET so the caller's intent is respected
    if(OpenCL_FIND_REQUIRED AND OpenCL_FIND_QUIETLY)
        find_package(OpenCL REQUIRED QUIET)
    elseif(OpenCL_FIND_REQUIRED)
        find_package(OpenCL REQUIRED)
    elseif(OpenCL_FIND_QUIETLY)
        find_package(OpenCL QUIET)
    else()
        find_package(OpenCL)
    endif()

    list(PREPEND CMAKE_MODULE_PATH "${_OCL_SELF_DIR}")
endif()
