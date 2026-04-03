const { getDefaultConfig } = require("expo/metro-config");
const path = require("path");

// Must be set before workers are spawned so Expo Router's Babel plugin
// can inline the path as a string literal in require.context().
// In a monorepo the EAS env injection happens too late for the transform worker.
if (!process.env.EXPO_ROUTER_APP_ROOT) {
  process.env.EXPO_ROUTER_APP_ROOT = path.resolve(__dirname, "app");
}

module.exports = getDefaultConfig(__dirname);
