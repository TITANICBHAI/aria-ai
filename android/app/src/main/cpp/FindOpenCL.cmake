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

    # ── Link-time stub libOpenCL.so ─────────────────────────────────────────────
    # The NDK sysroot does NOT ship libOpenCL.so — it is a vendor library present
    # only on the device (/vendor/lib64/libOpenCL.so on Mali devices like the M31).
    # lld refuses "-lOpenCL" when it cannot find the file anywhere in its search
    # paths, even with --allow-shlib-undefined (that flag only relaxes symbol
    # resolution, not library file lookup).
    #
    # Solution: compile a minimal empty stub shared library named libOpenCL.so
    # using the NDK toolchain that is already in use for the cross-compile. The
    # linker finds and records the SONAME. The stub is then EXCLUDED from the APK
    # (see packagingOptions in build.gradle) so that at runtime the device's real
    # Mali vendor libOpenCL.so is used instead.
    if(NOT TARGET OpenCLStub)
        set(_OCL_STUB_SRC "${CMAKE_BINARY_DIR}/opencl_stub.c")
        file(WRITE "${_OCL_STUB_SRC}"
            "/* OpenCL link-time stub\n"
            " * Compiled for arm64-v8a cross-build only.\n"
            " * Excluded from APK — the Mali vendor libOpenCL.so is used at runtime.\n"
            " */\n"
            "void __opencl_link_stub_unused__(void) {}\n"
        )
        add_library(OpenCLStub SHARED "${_OCL_STUB_SRC}")
        set_target_properties(OpenCLStub PROPERTIES
            OUTPUT_NAME "OpenCL"
            LIBRARY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/opencl_stub_dir"
        )
    endif()

    # ── INTERFACE target ────────────────────────────────────────────────────────
    # OpenCL::OpenCL is the canonical modern CMake target — ggml-opencl links against it.
    if(NOT TARGET OpenCL::OpenCL)
        add_library(OpenCL::OpenCL INTERFACE IMPORTED GLOBAL)
        target_include_directories(OpenCL::OpenCL INTERFACE "${_OCL_INCLUDE_DIR}")
        # Link against the stub target (full path). CMake resolves this to the
        # actual libOpenCL.so binary so lld never sees a bare "-lOpenCL".
        # The resulting libggml-opencl.so records DT_NEEDED: libOpenCL.so and the
        # device dynamic linker satisfies this from the vendor partition at runtime.
        target_link_libraries(OpenCL::OpenCL INTERFACE OpenCLStub)
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

    message(STATUS "ARIA FindOpenCL: Android — vendored Khronos headers + link-time stub libOpenCL.so (runtime: Mali vendor driver)")

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
