'use strict';
/**
 * ensure-gradle-plugin.js — postinstall hook for LOCAL development
 *
 * Copies @react-native/gradle-plugin into android/node_modules/ and patches
 * its settings.gradle.kts so Gradle's composite-build (includeBuild) works
 * correctly in a pnpm monorepo.
 *
 * Why this is needed (local dev only):
 *   eas-pre-install.sh handles this for EAS builds (runs before pnpm install).
 *   For local development, this postinstall script ensures the patched copy is
 *   always present after every `pnpm install`.
 *
 * Why patching is needed:
 *   The npm package ships settings.gradle.kts with a pluginManagement block
 *   that can interfere with plugin resolution in Gradle 8.8+ when used as
 *   an includeBuild. Removing the block eliminates the failure point.
 *
 * NOTE: expo-module-gradle-plugin (expo-modules-core/expo-module-gradle-plugin)
 *   does NOT need this — it has no settings.gradle.kts and settings.gradle
 *   finds it directly from node_modules after pnpm install.
 */

const path = require('path');
const fs   = require('fs');

const TARGET = path.join(__dirname, '../android/node_modules/@react-native/gradle-plugin');

// ── Step 1: Copy if not present ───────────────────────────────────────────────
if (fs.existsSync(TARGET)) {
  console.log('[ARIA] @react-native/gradle-plugin already in android/node_modules/ — skip copy');
} else {
  let pluginSrc;
  try {
    pluginSrc = path.dirname(require.resolve('@react-native/gradle-plugin/package.json'));
  } catch (e) {
    console.warn('[ARIA] Could not resolve @react-native/gradle-plugin:', e.message, '— skipping (non-fatal)');
    process.exit(0);
  }

  fs.mkdirSync(path.dirname(TARGET), { recursive: true });
  fs.cpSync(pluginSrc, TARGET, { recursive: true });
  console.log('[ARIA] Copied @react-native/gradle-plugin →', TARGET);
}

// ── Step 2: Patch settings.gradle.kts — remove pluginManagement block ────────
const settingsKts = path.join(TARGET, 'settings.gradle.kts');

if (!fs.existsSync(settingsKts)) {
  console.warn('[ARIA] settings.gradle.kts not found at', settingsKts, '— skipping patch (non-fatal)');
  process.exit(0);
}

const originalText = fs.readFileSync(settingsKts, 'utf8');

if (!originalText.includes('pluginManagement')) {
  console.log('[ARIA] settings.gradle.kts already has no pluginManagement block — no patch needed');
  process.exit(0);
}

// Remove the entire pluginManagement { ... } block using a brace-counting state machine.
const out   = [];
let pos     = 0;
let inBlock = false;
let depth   = 0;
const text  = originalText;

while (pos < text.length) {
  if (!inBlock) {
    const idx = text.indexOf('pluginManagement', pos);
    if (idx === -1) { out.push(text.slice(pos)); break; }
    const bracePos = text.indexOf('{', idx);
    if (bracePos === -1) { out.push(text.slice(pos)); break; }
    const prefix = text.slice(pos, idx).replace(/\s+$/, '');
    if (prefix.length > 0) out.push(prefix + '\n');
    depth   = 1;
    inBlock = true;
    pos     = bracePos + 1;
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
        if (pos < text.length && text[pos] === '\n') pos++;
      }
    }
  }
}

const result = out.join('').replace(/^\n+/, '');
fs.writeFileSync(settingsKts, result);
console.log('[ARIA] Patched settings.gradle.kts — removed pluginManagement block');
