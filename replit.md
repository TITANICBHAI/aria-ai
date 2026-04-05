# Workspace

## Overview

pnpm workspace monorepo — pure Kotlin + Jetpack Compose Android AI agent.
React Native / Expo are a **temporary UI shell** being phased out per `migration.md`.
Kotlin owns all logic permanently. All RN/bridge code is deleted in Phase 8.

## Migration Status (see migration.md for full detail)

| Phase | Status | Description |
|-------|--------|-------------|
| 0 | `[x]` | Environment + baseline build |
| 1 | `[x]` | ComposeMainActivity promoted as launcher; RN host stripped |
| 2 | `[ ]` | Fill SettingsScreen.kt gaps |
| 3 | `[ ]` | Fill ActivityScreen.kt gaps |
| 4 | `[ ]` | Fill ControlScreen.kt gaps |
| 5 | `[~]` | ChatScreen.kt — written, needs emulator verify |
| 6 | `[~]` | TrainScreen.kt — written, needs emulator verify |
| 7 | `[~]` | LabelerScreen.kt — written, needs emulator verify |
| 8 | `[ ]` | Delete RN layer — all gates must be green first |
| 9 | `[ ]` | Strip build system |

## Architecture

### Core Brain (Kotlin, permanent)
- `android/core/ai/` — llama.cpp JNI, Llama 3.2-1B Q4_K_M, model download
- `android/core/ocr/` — ML Kit text recognition
- `android/core/rl/` — on-device reinforcement learning (IRL, LoRA trainer)
- `android/core/memory/` — embeddings, experience store, object label store
- `android/core/events/AgentEventBus.kt` — internal SharedFlow event backbone
- `android/core/agent/AgentLoop.kt` — main reasoning loop

### System Control (Kotlin, permanent)
- `android/system/accessibility/` — AccessibilityService (reads UI tree, dispatches gestures)
- `android/system/screen/` — MediaProjection screen capture
- `android/system/actions/` — GestureEngine (tap, swipe, intent)
- `android/system/AgentForegroundService.kt` — keeps agent alive on 6GB RAM device

### UI (Jetpack Compose, permanent)
- `android/ui/ComposeMainActivity.kt` — launcher Activity (since Phase 1)
- `android/ui/ARIAComposeApp.kt` — NavHost + bottom nav (7 tabs + labeler route)
- `android/ui/viewmodel/AgentViewModel.kt` — single ViewModel, subscribes to AgentEventBus
- `android/ui/screens/` — DashboardScreen, ControlScreen, ChatScreen, TrainScreen,
                           LabelerScreen, ModulesScreen, ActivityScreen, SettingsScreen
- `android/ui/theme/ARIATheme.kt` — ARIA color palette

### RN / Expo (temporary — Phases 2–7 specification source only)
- `artifacts/mobile/app/(tabs)/` — .tsx screens used as feature spec for Kotlin screens
- Per migration.md: **DO NOT delete** any .tsx until its Kotlin replacement is `[x]`

## Target Device

Samsung Galaxy M31 — Exynos 9611 · Mali-G72 MP3 · 6 GB LPDDR4X
- Model: Llama 3.2-1B Instruct @ Q4_K_M (~870 MB, ~10–15 tok/s)
- Context window: 4096 tokens
- OCR: ML Kit (512×512 downsampled captures)
- Build target: arm64-v8a only (halves build time, required for NDK libs)

## Android Project — Firebase Studio / Android Studio Build

### How native-only mode works

`settings.gradle` auto-detects whether `node_modules` is present:
- **node_modules found** → hybrid mode: RN + Expo sub-projects loaded (EAS / local pnpm)
- **node_modules NOT found** → native-only mode: pure Kotlin + NDK (Firebase Studio / Android Studio)

No manual flag required. The mode is logged at Gradle configuration time.

### Key config files

| File | Purpose |
|------|---------|
| `android/settings.gradle` | Auto-detects mode, includes RN/Expo sub-projects in hybrid only |
| `android/build.gradle` | AGP 8.8, Kotlin 2.0.21, NDK r27.1 versions |
| `android/app/build.gradle` | CMake for llama-jni, Compose, all Kotlin deps |
| `android/gradle.properties` | JVM args (4 GB), AndroidX flags, RN arch (ignored in native-only) |
| `android/local.properties` | SDK + NDK paths — auto-written by .idx/dev.nix, never commit |
| `.idx/dev.nix` | Firebase Studio: installs JDK 17, SDK 35, NDK r27.1, arm64-v8a emulator |

### To open in Firebase Studio
1. Open the workspace — `.idx/dev.nix` runs automatically
2. SDK, NDK, and emulator are provisioned by the Nix environment
3. `local.properties` is written automatically with correct SDK/NDK paths
4. Open `artifacts/mobile/android/` as the project root in the Android panel
5. Gradle syncs in native-only mode (no node_modules needed)
6. Run ▶ launches on the arm64-v8a emulator

### To open in Android Studio
1. File → Open → select `artifacts/mobile/android/`
2. Gradle syncs in native-only mode automatically
3. If `local.properties` is missing, Android Studio writes it with your local SDK path

## Native Build (NDK / llama.cpp)

- `llama.cpp` submodule at `android/app/src/main/cpp/llama.cpp/`
- JNI bridge: `llama_jni.cpp` + `aria_math.cpp` → shared library `llama-jni`
- `LlamaEngine.kt` loads `llama-jni` via `System.loadLibrary("llama-jni")`
- `LLAMA_HAS_TRAINING` is NOT defined — `nativeTrainLora()` returns false, Kotlin stubs training
- Updated llama.cpp JNI API calls:
  - `llama_model_load_from_file` (replaces deprecated `llama_load_model_from_file`)
  - `llama_memory_clear(llama_get_memory(ctx), true)` (replaces `llama_kv_cache_clear`)
  - `llama_vocab_is_eog` / `llama_model_get_vocab`
  - `llama_adapter_lora_init` / `llama_set_adapters_lora` / `llama_adapter_lora_free`
  - `llama_model_free` (replaces deprecated `llama_free_model`)

## Directory Structure

```
root/
├── .idx/dev.nix                # Firebase Studio environment config
├── migration.md                # Migration plan and phase tracker
├── artifacts/
│   ├── mobile/
│   │   ├── android/            # ← Open this in Android Studio / Firebase Studio
│   │   │   ├── app/src/main/
│   │   │   │   ├── kotlin/com/ariaagent/mobile/
│   │   │   │   │   ├── MainActivity.kt         # stub — no launcher intent (Phase 8: delete)
│   │   │   │   │   ├── MainApplication.kt      # clean Application (SoLoader removed)
│   │   │   │   │   ├── core/                   # AI brain (permanent)
│   │   │   │   │   ├── system/                 # system services (permanent)
│   │   │   │   │   └── ui/                     # Compose UI (permanent)
│   │   │   │   ├── kotlin/expo/modules/
│   │   │   │   │   └── ExpoModulesPackageList.kt # empty stub (Phase 8: delete)
│   │   │   │   ├── cpp/                        # llama.cpp NDK sources
│   │   │   │   ├── res/                        # drawables, strings, styles
│   │   │   │   └── AndroidManifest.xml
│   │   │   ├── build.gradle    # dual-mode app build
│   │   │   ├── settings.gradle # auto-detects native-only vs hybrid
│   │   │   ├── gradle.properties
│   │   │   └── local.properties # SDK/NDK paths — never commit
│   │   └── app/(tabs)/         # RN screens — spec source for Kotlin (do not delete yet)
│   └── web-dashboard/          # Local Vite+React monitoring UI
├── shared/
└── packages/
```

## Events (AgentEventBus)

All internal events flow through `AgentEventBus` (SharedFlow). AgentViewModel subscribes
and converts to StateFlow for Compose UI. No direct service → UI calls.

| Event | Payload keys |
|-------|-------------|
| `agent_status_changed` | status, currentTask, currentApp, stepCount, lastAction, lastError, gameMode |
| `token_generated` | token, tokensPerSecond |
| `action_performed` | tool, nodeId, success, reward, stepCount |
| `step_started` | stepNumber, activity |
| `learning_cycle_complete` | loraVersion, policyVersion |
| `thermal_status_changed` | level, inferenceSafe, trainingSafe, emergency |
| `game_loop_status` | isActive, gameType, episodeCount, stepCount, currentScore, highScore, totalReward, lastAction, isGameOver |
| `model_download_progress` | percent, downloadedMb, totalMb, speedMbps |
| `model_download_complete` | path |
| `model_download_error` | error |
| `config_updated` | (same keys as getConfig) |

## Agent Preferences

- **GitHub push**: Run `git push github HEAD:main` manually in the Shell after each session.
  The `github` remote already has credentials embedded.
- **Auto-push**: Not possible — GitHub OAuth was dismissed and the platform blocks direct git push.
