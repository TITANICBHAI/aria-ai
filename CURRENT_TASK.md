# Current Task

> Update this file every time a new task begins.
> Format: WHAT → WHY → HOW → Acceptance criteria → Blockers → Next task

---

## Task: Phase 1 — EAS Build Setup + First Kotlin Compile

**Status:** In progress (`[~]`)
**Priority:** Critical — no Kotlin code runs until the Android project compiles
**Before this:** Phase 0 complete (android/ structure created, all Kotlin files written, bridge wired)
**After this:** Phase 1 model download service can be tested on real M31

---

## WHAT WAS JUST BUILT (Phase 0 complete)

### Kotlin Android Project (`artifacts/mobile/android/`)
| File | Purpose |
|------|---------|
| `build.gradle` (root) | Gradle plugin declarations — react-native + kotlin |
| `settings.gradle` | pnpm monorepo paths — points to hoisted node_modules |
| `gradle.properties` | `newArchEnabled=true`, `hermesEnabled=true`, arm64-v8a only |
| `app/build.gradle` | App module — react-native paths, ML Kit dep, OkHttp dep |
| `AndroidManifest.xml` | All required permissions (INTERNET, FOREGROUND_SERVICE, etc.) |
| `MainActivity.kt` | New Architecture delegate |
| `MainApplication.kt` | Registers AgentCorePackage, enables JSI |

### Bridge (JS ↔ Kotlin)
| File | Purpose |
|------|---------|
| `bridge/AgentCorePackage.kt` | Registers AgentCoreModule in React Native |
| `bridge/AgentCoreModule.kt` | All @ReactMethod calls JS can make — COMPLETE interface |
| `native-bindings/AgentCoreBridge.ts` | JS side — calls real NativeModules on Android, stubs on web |

### Core AI
| File | Purpose |
|------|---------|
| `core/ai/ModelManager.kt` | Checks if GGUF exists, manages paths, SHA256 verify |
| `core/ai/ModelDownloadService.kt` | Foreground service — OkHttp + Range resume + notifications |
| `core/ai/LlamaEngine.kt` | llama.cpp JNI stubs — ready for NDK wiring in Phase 1.3 |

### Perception
| File | Purpose |
|------|---------|
| `core/ocr/OcrEngine.kt` | ML Kit OCR — white-space structured text output |
| `system/accessibility/AgentAccessibilityService.kt` | Reads UI tree → LLM-friendly semantic IDs |
| `system/screen/ScreenCaptureService.kt` | MediaProjection → 512×512 JPEG |

### Action
| File | Purpose |
|------|---------|
| `system/actions/GestureEngine.kt` | tap/swipe/type/scroll from JSON → dispatchGesture() |

### Memory + Learning
| File | Purpose |
|------|---------|
| `core/memory/ExperienceStore.kt` | SQLite — (state, action, reward) tuples, edge case flags |
| `core/rl/PolicyNetwork.kt` | MLP policy (stub) — REINFORCE training target |
| `core/rl/LearningScheduler.kt` | Idle+charging detector → triggers training |

### UI Gate
| File | Purpose |
|------|---------|
| `components/ModelDownloadScreen.tsx` | Progress screen — shown until GGUF is on device |
| `app/_layout.tsx` | Checks `checkModelReady()` on Android launch — gates main tabs |

---

## WHAT IS NEXT

### Step 1 — EAS Build (User action needed)

The Kotlin code needs an actual Android build environment to compile.
Replit doesn't have the Android SDK. The user must:

**Option A — EAS Cloud Build (recommended, no local setup):**
```bash
npm install -g eas-cli
eas login
eas build --platform android --profile preview
```
This builds an APK in the cloud, installs on M31 via QR code.

**Option B — Local Android Studio:**
```bash
cd artifacts/mobile
npx expo prebuild --platform android
cd android && ./gradlew assembleDebug
```
Installs via: `adb install app/build/outputs/apk/debug/app-debug.apk`

**What to check after first build:**
- App launches on M31 without crash
- Model download screen appears (GGUF not present yet)
- Tap "Download AI Brain" → foreground notification appears
- Progress % updates in the UI as GGUF downloads
- After download: main tabs appear

### Step 2 — Add `eas.json`

```json
{
  "build": {
    "preview": {
      "android": {
        "buildType": "apk",
        "gradleCommand": ":app:assembleRelease"
      }
    },
    "production": {
      "android": {
        "buildType": "aab"
      }
    }
  }
}
```

### Step 3 — llama.cpp NDK Integration (Phase 1.3)

After the basic build works, wire up the actual LLM inference:

1. Add llama.cpp as a git submodule:
   ```bash
   git submodule add https://github.com/ggml-org/llama.cpp \
     artifacts/mobile/android/app/src/main/cpp/llama.cpp
   ```

2. Create `app/src/main/cpp/CMakeLists.txt`:
   ```cmake
   cmake_minimum_required(VERSION 3.22.1)
   project(llama-jni)
   set(LLAMA_VULKAN ON)   # Mali-G72 GPU offload
   set(LLAMA_METAL OFF)   # Android only
   add_subdirectory(llama.cpp)
   add_library(llama-jni SHARED llama_jni.cpp)
   target_link_libraries(llama-jni llama android log)
   ```

3. Create `app/src/main/cpp/llama_jni.cpp`:
   - `Java_com_ariaagent_mobile_core_ai_LlamaJNI_nativeLoadModel()`
   - `Java_com_ariaagent_mobile_core_ai_LlamaJNI_nativeRunInference()`
   - `Java_com_ariaagent_mobile_core_ai_LlamaJNI_nativeFreeModel()`

4. Update `LlamaEngine.kt` — replace stubs with JNI calls + `System.loadLibrary("llama-jni")`

5. Add `externalNativeBuild` block to `app/build.gradle`

---

## Acceptance Criteria (Phase 1 complete when all pass)

- [ ] `./gradlew assembleDebug` succeeds (or EAS build succeeds)
- [ ] App installs on M31 without crash
- [ ] `checkModelReady()` reaches Kotlin, returns false on fresh install
- [ ] Model download service starts, notification appears
- [ ] Download resumes after network interruption
- [ ] GGUF lands at correct path after complete download
- [ ] `AgentCore.loadModel()` calls `LlamaEngine.load()` without crash
- [ ] `AgentCore.runInference("hello", 20)` returns stub JSON (llama.cpp not yet wired)

---

## Files That Need User Action (Cannot Be Done in Replit)

| File | Action |
|------|--------|
| `eas.json` | Create with build profiles |
| `android/app/src/main/cpp/llama.cpp/` | `git submodule add` for llama.cpp source |
| EAS Account | `eas login` on the user's machine |
| `google-services.json` | NOT needed (ML Kit works without Firebase) |

---

## Next Task (after Phase 1 EAS build passes)

**Phase 1.3 — llama.cpp JNI wiring**
Wire `LlamaEngine.kt` stubs to real C++ inference via NDK.
Target: >8 tok/s on M31 with Q4_K_M + Vulkan offload.
See `DEVELOPMENT_ROADMAP.md` → Phase 1 → 1.3 llama.cpp JNI Integration.
