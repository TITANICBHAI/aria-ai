#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# eas-pre-install.sh — ARIA Mobile EAS Build Pre-Install Hook
#
# Runs via the "eas-build-pre-install" script in package.json, which EAS
# executes BEFORE pnpm install on EAS Cloud machines.
#
# Tasks
# ─────
#  1. Clone llama.cpp into the NDK build tree so CMake can compile
#     libllama-jni.so into the APK at build time.
#
#  2. Enforce Gradle wrapper at 8.13 (minimum required by AGP 8.8.0).
#
# NOTE: @react-native/gradle-plugin is resolved via root-level includeBuild()
# in settings.gradle (not pluginManagement — avoids Gradle 8.13 bug).
# autolinking.json is generated in settings.gradle at build time (no CLI call needed).
# ─────────────────────────────────────────────────────────────────────────────
set -eu

# ── Task 1: Clone llama.cpp ──────────────────────────────────────────────────
LLAMA_DIR="android/app/src/main/cpp/llama.cpp"

echo "=== [ARIA] Task 1: llama.cpp ==="

if [ -d "$LLAMA_DIR/.git" ]; then
    COMMIT=$(git -C "$LLAMA_DIR" rev-parse --short HEAD 2>/dev/null || echo "unknown")
    echo "llama.cpp already present at commit ${COMMIT} — skipping clone"
else
    if [ -d "$LLAMA_DIR" ] && [ "$(ls -A "$LLAMA_DIR" 2>/dev/null)" ]; then
        echo "Stale llama.cpp directory found — removing before fresh clone"
        rm -rf "$LLAMA_DIR"
    fi

    echo "Cloning llama.cpp @ b4200 (pinned for reproducible NDK builds)…"
    git clone \
        --depth=1 \
        --branch b4200 \
        --single-branch \
        https://github.com/ggerganov/llama.cpp.git \
        "$LLAMA_DIR"

    COMMIT=$(git -C "$LLAMA_DIR" rev-parse --short HEAD 2>/dev/null || echo "unknown")
    echo "llama.cpp cloned at commit ${COMMIT}"
fi

# ── Task 2: Enforce Gradle wrapper version ───────────────────────────────────
GRADLE_PROPS="android/gradle/wrapper/gradle-wrapper.properties"
TARGET_GRADLE="gradle-8.13-all"

echo "=== [ARIA] Task 2: Gradle wrapper ==="

if [ -f "$GRADLE_PROPS" ]; then
    if grep -q "$TARGET_GRADLE" "$GRADLE_PROPS" 2>/dev/null; then
        echo "Gradle wrapper already at 8.13 — no change needed"
    else
        node -e "
const fs = require('fs');
const filePath = process.argv[1];
let content = fs.readFileSync(filePath, 'utf8');
content = content.replace(
  /distributionUrl=.*gradle-[^\\n]+\\.zip/,
  'distributionUrl=https\\\\://services.gradle.org/distributions/gradle-8.13-all.zip'
);
fs.writeFileSync(filePath, content);
console.log('Enforced gradle-8.13-all in ' + filePath);
" "$GRADLE_PROPS"
        echo "Gradle wrapper pinned to 8.13"
    fi
else
    echo "WARNING: ${GRADLE_PROPS} not found — skipping Gradle wrapper enforcement"
fi

echo "=== [ARIA] eas-pre-install complete ==="
