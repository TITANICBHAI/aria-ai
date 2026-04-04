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
#        The npm package's settings.gradle.kts declares a pluginManagement
#        block that, when used as a composite included build, can interfere
#        with plugin resolution in Gradle 8.8+. When a project is used as a
#        composite included build, Gradle ignores its pluginManagement block
#        but the presence of the block can still break settings plugin
#        registration. Removing the entire block eliminates the failure point.
#
#      Why android/node_modules/:
#        pnpm hoists @react-native/gradle-plugin to the workspace root's
#        node_modules/. That workspace-root copy is unpatched (pnpm install
#        runs AFTER this hook). Our patched copy in android/node_modules/ is
#        checked FIRST by settings.gradle, ensuring Gradle always uses it.
#
#   3. Enforce Gradle wrapper at 8.13 — the minimum version required by
#      AGP 8.8.0 (com.android.tools.build:gradle:8.8.0). Gradle 8.13 is also
#      compatible with @react-native/gradle-plugin 0.81.x.
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

# ── Task 3: Enforce Gradle wrapper version ──────────────────────────────────
#
# Pin the main project's Gradle wrapper to 8.13. This is the minimum version
# required by AGP 8.8.0 (com.android.tools.build:gradle:8.8.0).
#
GRADLE_PROPS="android/gradle/wrapper/gradle-wrapper.properties"

echo "=== ARIA EAS pre-install: enforcing Gradle wrapper version ==="

if [ -f "$GRADLE_PROPS" ]; then
    if ! grep -q "gradle-8.13-all" "$GRADLE_PROPS"; then
        node -e "
const fs = require('fs');
const path = '$GRADLE_PROPS';
let content = fs.readFileSync(path, 'utf8');
content = content.replace(
  /distributionUrl=.*gradle-[^\\n]+\.zip/,
  'distributionUrl=https\\\\://services.gradle.org/distributions/gradle-8.13-all.zip'
);
fs.writeFileSync(path, content);
console.log('Enforced gradle-8.13 in ' + path);
"
        echo "=== Enforced Gradle wrapper: pinned to 8.13 in $GRADLE_PROPS ==="
    else
        echo "=== Gradle wrapper already at 8.13 — no change needed ==="
    fi
else
    echo "WARNING: $GRADLE_PROPS not found — skipping Gradle version enforcement"
fi

# ── Task 4: Patch the included build to remove pluginManagement block ────────
#
# When @react-native/gradle-plugin is used as a composite included build, its
# own pluginManagement block is ignored by Gradle but can interfere with plugin
# resolution in Gradle 8.8+. Remove the entire block (not just foojay) to
# ensure the included build integrates cleanly.
#
SETTINGS_KTS="$GRADLE_PLUGIN_DIR/settings.gradle.kts"

echo "=== ARIA EAS pre-install: patching @react-native/gradle-plugin settings ==="

if grep -q "pluginManagement" "$SETTINGS_KTS" 2>/dev/null; then
    node -e "
const fs = require('fs');
const path = '$SETTINGS_KTS';
let text = fs.readFileSync(path, 'utf8');

// Remove the entire pluginManagement { ... } block using a brace-counting state machine
let out = [];
let pos = 0;
let inBlock = false;
let depth = 0;

while (pos < text.length) {
  if (!inBlock) {
    const idx = text.indexOf('pluginManagement', pos);
    if (idx === -1) {
      out.push(text.slice(pos));
      break;
    }
    const bracePos = text.indexOf('{', idx);
    if (bracePos === -1) {
      out.push(text.slice(pos));
      break;
    }
    // Keep everything before the pluginManagement keyword (strip trailing whitespace)
    const prefix = text.slice(pos, idx).replace(/\\s+$/, '');
    if (prefix.length > 0) {
      out.push(prefix + '\\n');
    }
    depth = 1;
    inBlock = true;
    pos = bracePos + 1;
  } else {
    const nextOpen = text.indexOf('{', pos);
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
        // Skip trailing newline after closing brace
        if (pos < text.length && text[pos] === '\\n') pos++;
      }
    }
  }
}

const result = out.join('').replace(/^\\n+/, '');
fs.writeFileSync(path, result);
console.log('Removed pluginManagement block from ' + path);
"
    echo "=== Patched $SETTINGS_KTS: removed pluginManagement block ==="
else
    echo "=== $SETTINGS_KTS already has no pluginManagement block ==="
fi

# NOTE: expo-module-gradle-plugin (which provides ExpoModuleExtension) lives
# inside expo-modules-core/expo-module-gradle-plugin/. It is installed by
# pnpm install (which runs before Gradle on EAS), so no pre-install copying is
# needed. settings.gradle includes it via includeBuild() at the root level.
echo "=== ARIA EAS pre-install: complete ==="
