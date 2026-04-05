# ARIA — Firebase Studio / Android Studio Import Guide

## What this guide covers

Opening the `android/` directory in **Firebase Studio** (or Android Studio) for
the first time. The project is dual-mode — it auto-detects whether
`node_modules` is present and adjusts the Gradle build accordingly.

---

## Quick summary

| Environment | `node_modules` present? | Mode | Result |
|---|---|---|---|
| Firebase Studio / Android Studio (standalone) | No | **Native-only** | Syncs and builds as pure Kotlin + NDK |
| `pnpm install` + EAS / local monorepo | Yes | **Hybrid** | RN bridge + Expo modules included |

You do not set any flag. The build detects the mode automatically.

---

## Step-by-step: Firebase Studio (native-only)

### 1. Prerequisites

- Firebase Studio or Android Studio **Hedgehog or newer**
- Android SDK installed (API 34 recommended)
- NDK **26.1.10909125** installed via SDK Manager
- CMake **3.22.1** installed via SDK Manager

### 2. Clone / open the project

Open **only the `android/` subdirectory** — not the monorepo root.

```
File → Open → select /path/to/project/android/
```

Firebase Studio will show the `android/` folder as the project root.

### 3. Set up `local.properties`

```bash
cp android/local.properties.template android/local.properties
```

Edit `local.properties` and set `sdk.dir` to your SDK path:

```
sdk.dir=/home/<you>/Android/Sdk
```

On macOS: `/Users/<you>/Library/Android/sdk`  
On Windows: `C:\\Users\\<you>\\AppData\\Local\\Android\\Sdk`

Do **not** commit `local.properties` — it is in `.gitignore`.

### 4. Gradle sync

Click **Sync Now** in the notification bar, or:

```
File → Sync Project with Gradle Files
```

In the Build output you should see:

```
[ARIA] node_modules not found — NATIVE-ONLY mode (Firebase Studio / Android Studio).
[ARIA] RN/Expo sub-projects and plugins are skipped. Run pnpm install for hybrid mode.
```

If you see this line, the sync is correct. All RN/Expo sub-projects are skipped.
If Gradle warns about anything else, check the NDK and CMake versions above.

### 5. Build

```bash
# From inside android/
./gradlew assembleDebug
```

Expected output on success:

```
BUILD SUCCESSFUL in Xs
```

The APK is at:

```
android/app/build/outputs/apk/debug/app-debug.apk
```

### 6. Launch on emulator

Create an emulator: **API 34, arm64-v8a** (or x86_64 for faster sync).

```bash
adb shell am start -n com.ariaagent.mobile/.ui.ComposeMainActivity
```

The app should open directly to the Jetpack Compose UI — no React Native
splash or bridge initialisation.

---

## What is and is not included in native-only mode

### Always included (native-only + hybrid)

| Component | File(s) |
|---|---|
| Jetpack Compose UI | `ui/screens/*.kt`, `ui/ARIAComposeApp.kt` |
| LLM inference (llama.cpp JNI) | `core/ai/LlamaEngine.kt`, `src/main/cpp/` |
| OCR | `core/ocr/OcrEngine.kt` |
| Object detection | `core/perception/ObjectDetectorEngine.kt` |
| Sentence embeddings (ONNX) | `core/memory/EmbeddingEngine.kt` |
| Reinforcement learning | `core/rl/LoraTrainer.kt`, `core/rl/PolicyNetwork.kt` |
| Accessibility service | `system/accessibility/AgentAccessibilityService.kt` |
| Screen capture | `system/screen/ScreenCaptureService.kt` |
| Agent foreground service | `system/AgentForegroundService.kt` |
| AgentViewModel + AgentEventBus | `ui/viewmodel/AgentViewModel.kt` |

### Excluded in native-only mode (hybrid only)

| Component | Notes |
|---|---|
| React Native bridge | `bridge/` — Phase 8 deletion target |
| Expo modules (haptics, image picker, etc.) | Only loaded when `node_modules` present |
| Hermes JS engine | Not on classpath in native-only mode |
| `ExpoModulesPackageList.kt` | Empty stub in native-only — safe to ignore |

---

## Dual-mode explained (for reference)

`android/settings.gradle` scans four candidate paths for `node_modules`. If
none are found it also tries `node --print require.resolve(...)`. If still
nothing, it sets `gradle.ext.rnEnabled = false`.

`android/app/build.gradle` reads `gradle.ext.rnEnabled`:
- `false` → no `com.facebook.react` plugin, no RN/Expo dependencies
- `true` → applies the React plugin, includes all RN/Expo sub-projects

This means Firebase Studio (which never has `node_modules` in the project
directory) always gets a clean native-only build with zero configuration.

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| `sdk.dir is missing` | Copy `local.properties.template` → `local.properties` and set `sdk.dir` |
| `NDK not configured` | Install NDK 26.1.10909125 via SDK Manager → SDK Tools |
| `CMake not found` | Install CMake 3.22.1 via SDK Manager → SDK Tools |
| `NATIVE-ONLY mode` not printed | A `node_modules/react-native` folder was found somewhere in the tree — this is hybrid mode, which is fine, but requires pnpm install to be complete |
| Compose preview blank in IDE | Run the app on an emulator — IDE previews require the Compose tooling annotation processor to complete first |
| `libllama-jni.so not found` at runtime | Rebuild from scratch: `./gradlew clean assembleDebug` |

---

## Migration status

See `migration.md` at the monorepo root for the full phase-by-phase plan.

Current phase: **Phases 1–7 complete (written). Awaiting emulator verification
of all screens before Phase 8 (RN/Expo deletion).**

The `bridge/` directory, `MainActivity.kt` stub, and `ExpoModulesPackageList.kt`
stub are intentionally present until Phase 8 gate check passes.
