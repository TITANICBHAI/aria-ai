#!/usr/bin/env bash
# ─── EAS Build Pre-Install Hook ───────────────────────────────────────────────
# Runs before `npm install` on EAS Cloud machines.
#
# Tasks:
#   1. Clone llama.cpp into the NDK build tree so CMake can compile
#      libllama-jni.so into the APK at build time.
#
#   2. Install @react-native/gradle-plugin into android/node_modules/ and
#      patch it so Gradle can compile it reliably as a composite build.
#
#      Why patching is needed:
#        The npm package's settings.gradle.kts declares:
#          plugins { id("org.gradle.toolchains.foojay-resolver-convention").version("0.5.0") }
#        This forces Gradle to download foojay from the Gradle Plugin Portal
#        DURING the settings evaluation phase (before any caching). On a cold
#        EAS worker, this download silently fails, causing Gradle to report:
#          "No included builds contain this plugin"
#        Because EAS provides JDK 17 directly, foojay's JDK auto-resolver is
#        not needed. Removing the declaration eliminates the failure point.
#
#      Why android/node_modules/:
#        pnpm hoists @react-native/gradle-plugin to the workspace root's
#        node_modules/. That workspace-root copy is unpatched (pnpm install
#        runs AFTER this hook). Our patched copy in android/node_modules/ is
#        checked FIRST by settings.gradle, ensuring Gradle always uses it.
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

    echo "Cloning llama.cpp (pinned to b4200 for reproducible NDK builds)…"
    git clone \
        --depth=1 \
        --branch b4200 \
        --single-branch \
        https://github.com/ggerganov/llama.cpp.git \
        "$LLAMA_DIR"

    CLONED_COMMIT=$(git -C "$LLAMA_DIR" rev-parse --short HEAD 2>/dev/null || echo "unknown")
    echo "=== llama.cpp cloned (commit: $CLONED_COMMIT) — ready for NDK build ==="
fi

# ── Task 2: @react-native/gradle-plugin (install + patch) ───────────────────
RN_PLUGIN_VERSION="0.81.5"
GRADLE_PLUGIN_DIR="android/node_modules/@react-native/gradle-plugin"

echo "=== ARIA EAS pre-install: checking @react-native/gradle-plugin ==="

if [ ! -d "$GRADLE_PLUGIN_DIR" ]; then
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
        echo "WARNING: npm install for @react-native/gradle-plugin failed"
        rm -rf "$RN_PLUGIN_TMP"
        exit 0
    fi

    rm -rf "$RN_PLUGIN_TMP"
else
    echo "@react-native/gradle-plugin already in android/node_modules/"
fi

# ── Task 3: Patch the included build to remove foojay ───────────────────────
#
# The foojay-resolver-convention plugin in settings.gradle.kts forces Gradle
# to hit the Gradle Plugin Portal during settings evaluation. On a cold EAS
# worker (no Gradle cache) this download silently fails. Since EAS provides
# JDK 17, foojay's auto-resolver is unnecessary. Remove it.
#
SETTINGS_KTS="$GRADLE_PLUGIN_DIR/settings.gradle.kts"

if grep -q "foojay" "$SETTINGS_KTS" 2>/dev/null; then
    sed -i '/foojay/d' "$SETTINGS_KTS"
    echo "=== Patched $SETTINGS_KTS: removed foojay plugin declaration ==="
else
    echo "=== $SETTINGS_KTS already foojay-free ==="
fi
