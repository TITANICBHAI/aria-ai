# Current Task

> Update this file every time a new task begins.
> Format: WHAT → WHY → HOW → Acceptance criteria → Blockers → Next task

---

## Task: Phase 1 → EAS Build + llama.cpp NDK Wiring

**Status:** Code complete. Awaiting user to run EAS build on machine with Android SDK.
**Priority:** Critical — no Kotlin code runs until the first EAS build succeeds.

---

## WHAT WAS JUST BUILT (this session)

### Build Infrastructure
| File | Purpose |
|------|---------|
| `eas.json` | EAS build profiles: development (debug APK) / preview (release APK) / production (AAB) |
| `app/build.gradle` | Updated: CMake externalNativeBuild block, TFLite + MediaPipe deps added |

### NDK / llama.cpp JNI (Phase 1.4)
| File | Purpose |
|------|---------|
| `src/main/cpp/CMakeLists.txt` | Builds `llama-jni.so` with Vulkan (Mali-G72), arm64-v8a, -O3 |
| `src/main/cpp/llama_jni.cpp` | Full JNI implementation: load, create context, infer with streaming, free |
| `core/ai/LlamaEngine.kt` | Updated: real JNI declarations + graceful fallback to stub if .so not found |

### Perception Layer (Phase 2)
| File | Purpose |
|------|---------|
| `core/perception/ScreenObserver.kt` | Fuses accessibility tree + OCR into ScreenSnapshot for LLM prompt |
| `system/accessibility/AgentAccessibilityService.kt` | Updated: `currentPackage` + `currentActivity` tracked from events |

### Agent Loop (Phase 3)
| File | Purpose |
|------|---------|
| `core/ai/PromptBuilder.kt` | Assembles full Llama 3.2-1B Instruct chat template with system prompt, screen, goal, history, memory |
| `core/agent/AgentLoop.kt` | Full Observe→Reason→Act loop: screen capture → LLM → gesture → experience store → events to JS |

### Memory (Phase 4)
| File | Purpose |
|------|---------|
| `core/memory/EmbeddingEngine.kt` | MiniLM-L3-v2 via TFLite; hash-embed fallback until model downloaded; cosine similarity retrieval |

### Learning Pipeline (Phase 5)
| File | Purpose |
|------|---------|
| `core/rl/LoraTrainer.kt` | LoRA training: JSONL dataset from ExperienceStore → llama.cpp train API stub |
| `core/rl/IrlModule.kt` | IRL from video: frame extraction → OCR → text delta → action inference → experience tuples |
| `core/rl/LearningScheduler.kt` | Updated: now calls LoraTrainer + PolicyNetwork; emits `learning_cycle_complete` event |

### Bridge (JS ↔ Kotlin)
| File | Purpose |
|------|---------|
| `bridge/AgentCoreModule.kt` | Added: `startAgent`, `stopAgent`, `pauseAgent`, `getAgentLoopStatus`, `runRlCycle`, `processIrlVideo`, `getLearningStatus`. LearningScheduler wired in init block. |
| `native-bindings/NativeAgentCore.ts` | Full TurboModule codegen spec — every bridge method typed and declared |
| `native-bindings/AgentCoreBridge.ts` | Updated: `startAgent(goal, appPackage)`, `getAgentLoopStatus`, `runRlCycle`, `processIrlVideo`, `getLearningStatus` all wired to Kotlin |

---

## WHAT EXISTS (full file list)

### Kotlin (`artifacts/mobile/android/app/src/main/kotlin/com/ariaagent/mobile/`)
```
bridge/
  AgentCoreModule.kt     ← bridge, event emitter, init hooks
  AgentCorePackage.kt    ← registers module with React Native

core/
  agent/
    AgentLoop.kt         ← Observe→Reason→Act autonomous loop  [NEW]
  ai/
    LlamaEngine.kt       ← llama.cpp JNI (stub fallback ready)  [UPDATED]
    ModelManager.kt      ← GGUF path + SHA256 verify
    ModelDownloadService.kt ← OkHttp resume download + notification
    PromptBuilder.kt     ← Llama 3.2-1B chat template builder  [NEW]
  memory/
    ExperienceStore.kt   ← SQLite (state, action, reward) tuples
    EmbeddingEngine.kt   ← MiniLM-L3-v2 / hash fallback  [NEW]
  ocr/
    OcrEngine.kt         ← ML Kit OCR, white-space layout
  rl/
    IrlModule.kt         ← IRL from screen recording video  [NEW]
    LearningScheduler.kt ← idle+charging gating → LoraTrainer  [UPDATED]
    LoraTrainer.kt       ← LoRA adapter training  [NEW]
    PolicyNetwork.kt     ← MLP REINFORCE (stub training)

system/
  accessibility/
    AgentAccessibilityService.kt ← UI tree + currentPackage/Activity  [UPDATED]
  actions/
    GestureEngine.kt     ← tap/swipe/type → dispatchGesture
  screen/
    ScreenCaptureService.kt ← MediaProjection → 512×512 JPEG

MainActivity.kt
MainApplication.kt

cpp/
  CMakeLists.txt         ← NDK build config for llama-jni.so  [NEW]
  llama_jni.cpp          ← JNI implementation for llama.cpp  [NEW]
```

### TypeScript / JS (`artifacts/mobile/`)
```
native-bindings/
  AgentCoreBridge.ts     ← all bridge calls with web stubs  [UPDATED]
  NativeAgentCore.ts     ← TurboModule codegen spec  [NEW]

eas.json                 ← EAS build profiles  [NEW]
```

---

## BLOCKER — User Action Required

**The llama.cpp submodule must be added before the first EAS build:**

```bash
cd artifacts/mobile/android/app/src/main/cpp
git submodule add https://github.com/ggml-org/llama.cpp llama.cpp
git submodule update --init --recursive
```

**Then run the EAS build:**

```bash
# Option A — Cloud build (no local Android SDK needed)
npm install -g eas-cli
eas login
cd artifacts/mobile
eas build --platform android --profile preview

# Option B — Local Android Studio
npx expo prebuild --platform android
cd android && ./gradlew assembleRelease
```

---

## Acceptance Criteria (Phase 1 complete when all pass)

- [ ] `eas build --profile preview` succeeds (or `./gradlew assembleDebug`)
- [ ] App installs on M31 without crash
- [ ] `checkModelReady()` reaches Kotlin, returns false on fresh install
- [ ] Model download screen appears (GGUF not present)
- [ ] Tap "Download AI Brain" → foreground notification, progress updates
- [ ] After download: main tabs appear
- [ ] `AgentCore.loadModel()` runs without crash (stub inference active)
- [ ] `AgentCore.startAgent("Open WiFi", "com.android.settings")` triggers loop

---

## Next Tasks After EAS Build

1. **llama.cpp JNI real test** — `loadModel()` with real GGUF, measure tok/s on M31
2. **MediaProjection permission** — wire `ScreenCaptureService` to get real screenshots  
3. **Accessibility service test** — verify node tree output for Settings app
4. **First agent run** — `startAgent("Open WiFi settings", "com.android.settings")` → watch loop
5. **Download MiniLM** — ~23MB TFLite model for real embedding retrieval
