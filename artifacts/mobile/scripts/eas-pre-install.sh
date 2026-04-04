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
#  2. Install @react-native/gradle-plugin into android/node_modules/ and
#     patch its settings.gradle.kts.
#
#     Why android/node_modules/?
#       This hook runs BEFORE pnpm install. The patched copy must be present
#       before Gradle starts so settings.gradle can find it at priority-1
#       and avoid the symlinked version that pnpm may create.
#
#     Why patching?
#       The npm package's settings.gradle.kts has a pluginManagement block
#       that, when used as an included build, can interfere with plugin
#       resolution in Gradle 8.8+. Removing the entire block eliminates the
#       failure point. The foojay toolchain resolver it used to declare is
#       also unnecessary on EAS workers (JDK 17 is provided directly).
#
#  3. Enforce Gradle wrapper at 8.13 (minimum required by AGP 8.8.0).
#
#  NOTE: expo-module-gradle-plugin lives inside expo-modules-core/
#  expo-module-gradle-plugin/. It is installed by pnpm install (which runs
#  AFTER this hook on EAS), so no pre-install action is needed for it.
#  settings.gradle finds it automatically after pnpm install completes.
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

# ── Task 2: Install @react-native/gradle-plugin ──────────────────────────────
RN_PLUGIN_VERSION="0.81.5"
RN_PLUGIN_DIR="android/node_modules/@react-native/gradle-plugin"

echo "=== [ARIA] Task 2: @react-native/gradle-plugin ==="

if [ ! -d "$RN_PLUGIN_DIR" ]; then
    echo "Installing @react-native/gradle-plugin@${RN_PLUGIN_VERSION} into android/node_modules/…"

    TMP=$(mktemp -d)

    if npm install \
        --prefix "$TMP" \
        --no-save \
        "@react-native/gradle-plugin@${RN_PLUGIN_VERSION}" 2>&1; then

        mkdir -p "$(dirname "$RN_PLUGIN_DIR")"
        cp -r "$TMP/node_modules/@react-native/gradle-plugin" "$RN_PLUGIN_DIR"
        echo "@react-native/gradle-plugin installed at ${RN_PLUGIN_DIR}"
    else
        echo "WARNING: npm install for @react-native/gradle-plugin failed — build may fail"
    fi

    rm -rf "$TMP"
else
    echo "@react-native/gradle-plugin already present at ${RN_PLUGIN_DIR}"
fi

# ── Task 2b: Patch settings.gradle.kts — remove pluginManagement block ───────
SETTINGS_KTS="${RN_PLUGIN_DIR}/settings.gradle.kts"

echo "=== [ARIA] Task 2b: patching ${SETTINGS_KTS} ==="

if [ -f "$SETTINGS_KTS" ] && grep -q "pluginManagement" "$SETTINGS_KTS" 2>/dev/null; then
    node -e "
const fs = require('fs');
const filePath = process.argv[1];
let text = fs.readFileSync(filePath, 'utf8');

// Remove the entire pluginManagement { ... } block using brace counting
let out = [];
let pos = 0;
let inBlock = false;
let depth = 0;

while (pos < text.length) {
  if (!inBlock) {
    const idx = text.indexOf('pluginManagement', pos);
    if (idx === -1) { out.push(text.slice(pos)); break; }
    const bracePos = text.indexOf('{', idx);
    if (bracePos === -1) { out.push(text.slice(pos)); break; }
    const prefix = text.slice(pos, idx).replace(/\\s+\$/, '');
    if (prefix.length > 0) out.push(prefix + '\\n');
    depth = 1;
    inBlock = true;
    pos = bracePos + 1;
  } else {
    const nextOpen  = text.indexOf('{', pos);
    const nextClose = text.indexOf('}', pos);
    if (nextClose === -1) break;
    if (nextOpen !== -1 && nextOpen < nextClose) {
      depth++;
      pos = nextOpen + 1;
    } else {
      depth--;
      pos = nextClose + 1;
      if (depth === 0) {
        inBlock = false;
        if (pos < text.length && text[pos] === '\\n') pos++;
      }
    }
  }
}

const result = out.join('').replace(/^\\n+/, '');
fs.writeFileSync(filePath, result);
console.log('Patched: removed pluginManagement block from ' + filePath);
" "$SETTINGS_KTS"
    echo "Patched ${SETTINGS_KTS}"
else
    echo "${SETTINGS_KTS} already has no pluginManagement block — no patch needed"
fi

# ── Task 3: Enforce Gradle wrapper version ───────────────────────────────────
GRADLE_PROPS="android/gradle/wrapper/gradle-wrapper.properties"
TARGET_GRADLE="gradle-8.13-all"

echo "=== [ARIA] Task 3: Gradle wrapper ==="

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
