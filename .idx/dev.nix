# Firebase Studio (Project IDX) environment for ARIA Agent
# ─────────────────────────────────────────────────────────────────────────────
# What this file does
# ─────────────────────────────────────────────────────────────────────────────
# • Installs JDK 17, NDK r27.1, Android SDK 35, Build-tools 35
# • Spins up an arm64-v8a emulator (Pixel 7 API 35) so you can run and debug
#   the app without a physical device
# • Writes local.properties every time the workspace starts so Gradle always
#   knows where the SDK and NDK live
# • Installs the Kotlin + Gradle VS Code extensions used by the IDE
#
# ─────────────────────────────────────────────────────────────────────────────
# Why arm64-v8a emulator?
# ─────────────────────────────────────────────────────────────────────────────
# This project's native code (llama.cpp, ONNX, MediaPipe) is compiled with
#   -march=armv8-a
# and the NDK abiFilter is "arm64-v8a" only.  An x86_64 emulator cannot run
# these libraries.  Firebase Studio cloud instances run on Google Axion (ARM64)
# so the arm64-v8a emulator gets full hardware acceleration.
# ─────────────────────────────────────────────────────────────────────────────

{ pkgs, ... }: {

  # nixpkgs channel — stable-24.11 ships JDK 17, cmake 3.30, ninja 1.12
  channel = "stable-24.11";

  # ── System packages ──────────────────────────────────────────────────────
  packages = [
    pkgs.jdk17        # required: AGP 8.8 + Kotlin 2.0 need JDK 17
    pkgs.python312    # optional: some Gradle scripts invoke python
    pkgs.unzip        # needed by sdkmanager to unpack .zip SDK components
    pkgs.cmake        # for llama.cpp NDK build (CMake 3.22+)
    pkgs.ninja        # cmake build system backend
    pkgs.git          # version control in workspace
    pkgs.nodejs_20    # pnpm / metro (still needed while RN layer compiles)
  ];

  # ── Android SDK ───────────────────────────────────────────────────────────
  android = {
    enable = true;

    # SDK platforms
    platforms = [ 35 ];          # android-35 (matches compileSdk / targetSdk)

    # Build tools (matches buildToolsVersion in build.gradle)
    buildTools = [ "35.0.0" ];

    # Always-useful SDK components
    platformTools.enable = true; # adb, fastboot, etc.
    tools.enable = true;         # sdkmanager, avdmanager, etc.

    # NDK — exact version from build.gradle ndkVersion
    ndk.packages = [ "27.1.12297006" ];

    # Emulator
    emulator.enable = true;

    # System image: arm64-v8a so native llama.cpp code runs unmodified
    systemImages = [
      {
        platform = "35";
        tag      = "google_apis_playstore"; # includes Play Store for realism
        abi      = "arm64-v8a";
      }
    ];
  };

  # ── Firebase Studio / VS Code ─────────────────────────────────────────────
  idx = {

    # Extensions installed into the embedded VS Code
    extensions = [
      "mathiasfrohlich.Kotlin"           # Kotlin syntax highlight + snippets
      "ms-vscode-gradle.gradle-for-java" # Gradle task runner
      "vscjava.vscode-java-debug"        # JDWP debugger (attach to device/emu)
      "redhat.java"                      # Java language server (class resolve)
      "naumovs.color-highlight"          # See ARIAColors hex values inline
    ];

    workspace = {

      # ── onCreate — runs ONCE when workspace is first created ───────────────
      onCreate = {

        # Write local.properties so Gradle can find the SDK/NDK
        # $ANDROID_HOME is set by the android block above
        write-local-properties = ''
          printf 'sdk.dir=%s\nndk.dir=%s/ndk/27.1.12297006\n' \
            "$ANDROID_HOME" "$ANDROID_HOME" \
            > android/local.properties
          echo "[IDX] local.properties written → $ANDROID_HOME"
        '';

        # Install JS dependencies (Expo / Metro / RN still compile in Phase 1-7)
        # Uses pnpm workspaces; --ignore-scripts skips postinstall native builds
        install-node-deps = ''
          command -v pnpm >/dev/null 2>&1 || npm install -g pnpm@latest
          pnpm install --ignore-scripts 2>/dev/null \
            && echo "[IDX] pnpm install complete" \
            || echo "[IDX] pnpm install skipped (no package.json or already done)"
        '';

        # Pre-download Gradle dependencies so first build is fast
        # --no-daemon keeps it lightweight during setup
        gradle-prefetch = ''
          cd android
          ./gradlew dependencies --no-daemon -q 2>/dev/null \
            && echo "[IDX] Gradle deps prefetched" \
            || echo "[IDX] Gradle prefetch skipped (will resolve on first build)"
        '';
      };

      # ── onStart — runs EVERY time the workspace starts ────────────────────
      onStart = {

        # Re-sync local.properties in case ANDROID_HOME path changed
        sync-local-properties = ''
          printf 'sdk.dir=%s\nndk.dir=%s/ndk/27.1.12297006\n' \
            "$ANDROID_HOME" "$ANDROID_HOME" \
            > android/local.properties
        '';
      };
    };

    # ── Previews — the Run ▶ button in Firebase Studio ────────────────────
    previews = {
      enable = true;
      previews = {
        android = {
          command = [];        # IDX Android panel manages the launch command
          manager = "android"; # "android" = uses the built-in Android run panel
        };
      };
    };
  };
}
