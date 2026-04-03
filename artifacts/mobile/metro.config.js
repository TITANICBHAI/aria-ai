const { getDefaultConfig } = require("expo/metro-config");
const path = require("path");

const PROJECT_ROOT = __dirname;
const WORKSPACE_ROOT = path.resolve(PROJECT_ROOT, "../..");

const config = getDefaultConfig(PROJECT_ROOT);

// ─── Monorepo watchFolders ────────────────────────────────────────────────────
// In a pnpm hoisted workspace, Metro must watch the workspace root node_modules
// and all workspace packages so it can resolve transitive dependencies correctly.
// Without this, Metro only watches the artifact's local node_modules and misses
// packages hoisted to the workspace root.
// Reference: https://docs.expo.dev/guides/monorepos/#update-the-metro-config
config.watchFolders = [WORKSPACE_ROOT];

// Ensure Metro can resolve workspace packages from the root node_modules
config.resolver = {
  ...config.resolver,
  nodeModulesPaths: [
    path.resolve(PROJECT_ROOT, "node_modules"),
    path.resolve(WORKSPACE_ROOT, "node_modules"),
  ],
};

module.exports = config;
