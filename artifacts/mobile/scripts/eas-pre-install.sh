#!/usr/bin/env bash
# ─── EAS Build Pre-Install Hook ───────────────────────────────────────────────
# Runs before `npm install` on EAS Cloud machines.
# Clones llama.cpp source into the NDK build tree so CMake can compile
# libllama-jni.so into the APK at build time.
#
# Path (relative to artifacts/mobile/ project root, where EAS runs):
#   android/app/src/main/cpp/llama.cpp/
#
# Shallow clone (--depth=1) keeps the EAS build disk usage minimal (~150 MB
# source vs ~1.5 GB full history). No submodules needed — CMakeLists.txt only
# requires the llama.cpp source itself; ggml is vendored inside the repo.
#
# If the directory already exists (e.g., a cached EAS build layer), the script
# skips the clone entirely so incremental builds stay fast.
# ──────────────────────────────────────────────────────────────────────────────
set -euo pipefail

LLAMA_DIR="android/app/src/main/cpp/llama.cpp"

echo "=== ARIA EAS pre-install: checking llama.cpp ==="

if [ -d "$LLAMA_DIR/.git" ]; then
    CURRENT_COMMIT=$(git -C "$LLAMA_DIR" rev-parse --short HEAD 2>/dev/null || echo "unknown")
    echo "llama.cpp already present at $LLAMA_DIR (commit: $CURRENT_COMMIT) — skipping clone"
    exit 0
fi

if [ -d "$LLAMA_DIR" ] && [ "$(ls -A "$LLAMA_DIR" 2>/dev/null)" ]; then
    echo "llama.cpp directory exists but has no .git — removing stale contents"
    rm -rf "$LLAMA_DIR"
fi

echo "Cloning llama.cpp (shallow, default branch)…"
git clone \
    --depth=1 \
    --single-branch \
    https://github.com/ggerganov/llama.cpp.git \
    "$LLAMA_DIR"

CLONED_COMMIT=$(git -C "$LLAMA_DIR" rev-parse --short HEAD 2>/dev/null || echo "unknown")
echo "=== llama.cpp cloned at $LLAMA_DIR (commit: $CLONED_COMMIT) — ready for NDK build ==="
