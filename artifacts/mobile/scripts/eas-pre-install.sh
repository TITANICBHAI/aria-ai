#!/usr/bin/env bash
# ─── EAS Build Pre-Install Hook ───────────────────────────────────────────────
# Runs before `npm install` on EAS Cloud machines.
#
# Tasks:
#   1. Clone llama.cpp into the NDK build tree so CMake can compile
#      libllama-jni.so into the APK at build time.
#
#   2. Install @react-native/gradle-plugin into android/node_modules/ so
#      Gradle can reliably find it in this pnpm monorepo.
#
#      Why this is needed:
#        In a pnpm workspace, packages are hoisted to the workspace root's
#        node_modules/ as entries in pnpm's virtual store (.pnpm/).
#        Gradle's includeBuild() can fail to compile and register
#        com.facebook.react.settings when the source lives behind pnpm's
#        symlink chain. A real npm-installed copy in android/node_modules/
#        gives Gradle a clean, writable directory it can build reliably.
#        This location is checked first by settings.gradle (Strategy 2, i=0).
# ──────────────────────────────────────────────────────────────────────────────
set -eu

# ── Task 1: llama.cpp ────────────────────────────────────────────────────────
LLAMA_DIR="android/app/src/main/cpp/llama.cpp"

echo "=== ARIA EAS pre-install: checking llama.cpp ==="

if [ -d "$LLAMA_DIR/.git" ]; then
    CURRENT_COMMIT=$(git -C "$LLAMA_DIR" rev-parse --short HEAD 2>/dev/null || echo "unknown")
    echo "llama.cpp already present (commit: $CURRENT_COMMIT) — skipping clone"
else
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
    echo "=== llama.cpp cloned (commit: $CLONED_COMMIT) — ready for NDK build ==="
fi

# ── Task 2: @react-native/gradle-plugin ─────────────────────────────────────
RN_PLUGIN_VERSION="0.81.5"
GRADLE_PLUGIN_DIR="android/node_modules/@react-native/gradle-plugin"

echo "=== ARIA EAS pre-install: checking @react-native/gradle-plugin ==="

if [ -d "$GRADLE_PLUGIN_DIR" ]; then
    echo "@react-native/gradle-plugin already in android/node_modules/ — skipping install"
else
    echo "Installing @react-native/gradle-plugin@${RN_PLUGIN_VERSION} into android/node_modules/…"

    RN_PLUGIN_TMP=$(mktemp -d)

    if npm install \
        --prefix "$RN_PLUGIN_TMP" \
        --no-save \
        "@react-native/gradle-plugin@${RN_PLUGIN_VERSION}" 2>&1; then

        mkdir -p "$(dirname "$GRADLE_PLUGIN_DIR")"
        cp -r "$RN_PLUGIN_TMP/node_modules/@react-native/gradle-plugin" \
              "$GRADLE_PLUGIN_DIR"
        echo "=== @react-native/gradle-plugin installed at $GRADLE_PLUGIN_DIR ==="
    else
        echo "WARNING: npm install for @react-native/gradle-plugin failed — Gradle may not locate the plugin"
    fi

    rm -rf "$RN_PLUGIN_TMP"
fi
