const { getDefaultConfig } = require("expo/metro-config");
const path = require("path");

const PROJECT_ROOT = __dirname;
const WORKSPACE_ROOT = path.resolve(PROJECT_ROOT, "../..");

const config = getDefaultConfig(PROJECT_ROOT);

// ─── Monorepo watchFolders ────────────────────────────────────────────────────
// Metro must watch the workspace root so it can resolve hoisted dependencies.
config.watchFolders = [WORKSPACE_ROOT];

// ─── Block volatile/temp directories from resolution and watching ─────────────
// expo-router creates short-lived `expo-router_tmp_N` directories inside
// node_modules during bundling. The FallbackWatcher (used on Linux without
// watchman) walks directories synchronously and crashes with ENOENT when these
// temp dirs are deleted between the walk and the fs.watch() call.
// Blocking them from resolution also prevents Metro from attempting to watch them.
const blockList = [
  // expo-router temp bundling directories
  /node_modules[/\\]expo-router_tmp_[^/\\]+[/\\].*/,
  // General pattern for any other volatile pnpm temp dirs
  /node_modules[/\\]\.pnpm[/\\].*_tmp_[^/\\]+[/\\].*/,
];

// ─── Resolver ─────────────────────────────────────────────────────────────────
config.resolver = {
  ...config.resolver,
  nodeModulesPaths: [
    path.resolve(PROJECT_ROOT, "node_modules"),
    path.resolve(WORKSPACE_ROOT, "node_modules"),
  ],
  alias: {
    "@": PROJECT_ROOT,
  },
  blockList,
};

// ─── Watcher ──────────────────────────────────────────────────────────────────
// On Linux/Replit, Metro falls back to FallbackWatcher when inotify limits are
// hit or watchman is unavailable. Configure it to be resilient to missing paths.
config.watcher = {
  ...config.watcher,
  // Increase the health-check interval — avoids spurious restarts on slow VMs.
  healthCheck: {
    enabled: true,
    interval: 30000,
    timeout: 10000,
    filePrefix: ".metro-health-check",
  },
};

module.exports = config;
