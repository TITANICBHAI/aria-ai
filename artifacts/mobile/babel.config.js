const path = require("path");

const PROJECT_ROOT = __dirname;
const APP_ROOT = path.resolve(PROJECT_ROOT, "app");

/**
 * Directly inlines EXPO_ROUTER_APP_ROOT and EXPO_ROUTER_ABS_APP_ROOT using
 * the actual filename of the file being transformed.
 *
 * WHY THIS EXISTS:
 * babel-preset-expo's expoRouterBabelPlugin resolves these env vars via
 * api.caller({ routerRoot }), which is injected by @expo/metro-config's
 * rewriteRequestUrl middleware. That middleware only fires for
 * /.expo/.virtual-metro-entry.bundle URLs (used in dev/expo-start).
 * EAS production builds bundle via the direct entry file URL, so
 * rewriteRequestUrl is never called, customTransformOptions.routerRoot is
 * never set, and the Babel plugin can't compute the path — leaving
 * process.env.EXPO_ROUTER_APP_ROOT unreplaced, which crashes Metro's
 * require.context dependency collector.
 *
 * This plugin runs BEFORE presets (Babel evaluation order), so it replaces
 * the member expressions first. expoRouterBabelPlugin then finds nothing to
 * replace and skips gracefully.
 */
function inlineExpoRouterEnvPlugin() {
  return {
    name: "inline-expo-router-env",
    visitor: {
      MemberExpression(nodePath, state) {
        const { node } = nodePath;
        if (
          node.object?.type !== "MemberExpression" ||
          node.object.object?.name !== "process" ||
          node.object.property?.name !== "env"
        ) {
          return;
        }

        const envKey = node.property?.name;
        if (!envKey) return;

        if (envKey === "EXPO_ROUTER_APP_ROOT") {
          const filename = state.filename || state.file?.opts?.filename;
          if (!filename) return;
          const relPath = path
            .relative(path.dirname(filename), APP_ROOT)
            .replace(/\\/g, "/");
          nodePath.replaceWith({ type: "StringLiteral", value: relPath });
        } else if (envKey === "EXPO_ROUTER_ABS_APP_ROOT") {
          nodePath.replaceWith({
            type: "StringLiteral",
            value: APP_ROOT.replace(/\\/g, "/"),
          });
        } else if (envKey === "EXPO_ROUTER_IMPORT_MODE") {
          // 'sync' for production builds; 'lazy' only when async routes are enabled
          nodePath.replaceWith({ type: "StringLiteral", value: "sync" });
        }
      },
    },
  };
}

module.exports = function (api) {
  api.cache(true);
  return {
    presets: [["babel-preset-expo", { unstable_transformImportMeta: true }]],
    plugins: [inlineExpoRouterEnvPlugin],
  };
};
