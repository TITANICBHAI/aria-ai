/**
 * Copies @react-native/gradle-plugin into android/node_modules/ so Gradle's
 * includeBuild() finds a real, non-symlinked directory on EAS pnpm monorepo builds.
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
 */

'use strict';

const path = require('path');
const fs   = require('fs');

const TARGET = path.join(__dirname, '../android/node_modules/@react-native/gradle-plugin');

if (fs.existsSync(TARGET)) {
  console.log('[ARIA] @react-native/gradle-plugin already present in android/node_modules/ — skipping copy');
  process.exit(0);
}

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
