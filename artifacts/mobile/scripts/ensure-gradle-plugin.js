/**
 * Copies @react-native/gradle-plugin into android/node_modules/ so Gradle's
 * includeBuild() finds a real, non-symlinked directory on EAS pnpm monorepo builds.
 * After copying, patches the settings.gradle.kts to remove the pluginManagement
 * block so it works cleanly as a composite included build.
 *
 * Why this is needed:
 *   In a pnpm workspace, @react-native/gradle-plugin is hoisted to the workspace
 *   root's node_modules/ as a symlink into pnpm's virtual store (.pnpm/).
 *   Gradle's composite-build machinery (includeBuild) can fail to compile/register
 *   the com.facebook.react.settings plugin when the source lives behind a symlink
 *   chain in the pnpm store.
 *
 *   Placing a real copy at android/node_modules/@react-native/gradle-plugin
 *   matches the standard React Native template layout and is reliably found by
 *   settings.gradle Strategy 2 (i=0).
 *
 * Why the pluginManagement patch is needed:
 *   When a project is used as a composite included build, Gradle ignores its own
 *   pluginManagement block but the presence of the block can still interfere with
 *   settings plugin resolution in Gradle 8.8+. Removing the entire block ensures
 *   com.facebook.react.settings is registered correctly.
 */

'use strict';

const path = require('path');
const fs   = require('fs');

const TARGET = path.join(__dirname, '../android/node_modules/@react-native/gradle-plugin');

if (fs.existsSync(TARGET)) {
  console.log('[ARIA] @react-native/gradle-plugin already present in android/node_modules/ — skipping copy');
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

// Patch settings.gradle.kts: remove the entire pluginManagement { ... } block.
// When used as a composite included build, Gradle ignores the block anyway, but
// its presence can break settings plugin resolution in Gradle 8.8+.
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
let out = [];
let pos = 0;
let inBlock = false;
let depth = 0;
const text = originalText;

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
    const prefix = text.slice(pos, idx).replace(/\s+$/, '');
    if (prefix.length > 0) {
      out.push(prefix + '\n');
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
        if (pos < text.length && text[pos] === '\n') pos++;
      }
    }
  }
}

const result = out.join('').replace(/^\n+/, '');
fs.writeFileSync(settingsKts, result);
console.log('[ARIA] Patched settings.gradle.kts: removed pluginManagement block');
