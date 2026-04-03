# ARIA AI — Error Registry

> **Last updated:** 2026-04-03 (session 4 — ThermalGuard · NativeAgentCore spec · import cleanup)  
> **Scanned:** All 48 Kotlin files · 23 TypeScript/TSX files · AndroidManifest · build configs · web-dashboard · shared schemas · markdown docs

---

## Legend
| Severity | Meaning |
|----------|---------|
| 🔴 CRITICAL | Prevents build / app from running |
| 🟠 HIGH | Compile/type error, CI breaks |
| 🟡 MEDIUM | Wrong platform code, deprecated API, runtime surprise |
| 🟢 LOW | Warning / cleanup item, no functional impact |
| ⚙️ AUTO | Stubs that self-resolve when the native library is compiled |

---

## Status Summary
| ✅ Fixed | ⚙️ Auto-fix on native build |
|---------|----------------------------|
| E01 E02 E03 E05 E06 E07 E08 E09 E11 E12 E13 | E10a E10b |

---

## Error Table

| ID | Sev | Type | Location | Description | Status |
|----|-----|------|----------|-------------|--------|
| E01 | 🟠 HIGH | TypeScript | `artifacts/mockup-sandbox/src/components/ui/calendar.tsx:132` | TS2322 React 19 ref callback type incompatibility (`VoidOrUndefinedOnly`). | ✅ FIXED — cast to `RefCallback<HTMLDivElement>` |
| E02 | 🟠 HIGH | TypeScript | `artifacts/mockup-sandbox/src/components/ui/spinner.tsx:7` | TS2322 conflicting `@types/react` copies; ref spread onto Lucide icon. | ✅ FIXED — simplified Spinner to `{ className?: string }` |
| E03 | 🔴 CRITICAL | Native Build | `artifacts/mobile/android/app/src/main/cpp/llama.cpp/` | **llama.cpp submodule missing.** `CMakeLists.txt` calls `add_subdirectory(llama.cpp)` but directory doesn't exist. Android native JNI build fails. JS/Expo Go unaffected (LlamaEngine auto-stubs). | ✅ FIXED — `eas-build-pre-install` hook (`scripts/eas-pre-install.sh`) shallow-clones llama.cpp before NDK build. EAS Cloud picks it up automatically via `package.json` script. |
| E04 | 🟡 MEDIUM | iOS-only API | `artifacts/mobile/app/(tabs)/_layout.tsx` | `expo-router/unstable-native-tabs` — unstable iOS-18+ experimental API. Subsumed by E06. | ✅ FIXED via E06 |
| E05 | 🟢 LOW | Workspace | `pnpm-workspace.yaml` | `lib/*` and `lib/integrations/*` point to empty directory — pnpm install warnings. | ✅ FIXED — removed stale globs |
| E06 | 🟡 MEDIUM | iOS-only Code | `artifacts/mobile/app/(tabs)/_layout.tsx` | Entire `NativeTabLayout` block uses iOS-18+ APIs (`NativeTabs`, `Icon`, `Label`, `SymbolView`, `isLiquidGlassAvailable`). `isIOS` conditionals throughout `ClassicTabLayout`. All dead code on Android. | ✅ FIXED — rewrote to pure Android tab layout; removed all iOS branches and dead imports |
| E07 | 🟢 LOW | iOS-only Code | `artifacts/mobile/app/(tabs)/chat.tsx:262` | `behavior={Platform.OS === "ios" ? "padding" : "height"}` — dead iOS branch, both arms produce same result on Android. | ✅ FIXED — hardcoded `behavior="height"`, removed redundant offset prop |
| E08 | 🟢 LOW | App Config | `artifacts/mobile/app.json` | `"ios": { "supportsTablet": false }` stale section. `"android": {}` missing `package` field. | ✅ FIXED — removed `ios` section, added `"package": "com.ariaagent.mobile"` |
| E09 | 🟡 MEDIUM | Dead Packages | `artifacts/mobile/package.json` | `expo-symbols@~1.0.8` (SF Symbols, iOS-only) and `expo-glass-effect@~0.1.4` (Liquid Glass, iOS-only) installed but fully unused on Android. | ✅ FIXED — removed both packages |
| E10a | ⚙️ AUTO | Kotlin Stub | `…/core/ai/LlamaEngine.kt` | `jniAvailable=false` when `libllama-jni.so` absent. Returns hardcoded stub JSON. Catches `UnsatisfiedLinkError` in `companion object init`. | ⚙️ AUTO-FIXES when E03 resolved and native build compiles the `.so` |
| E10b | ⚙️ AUTO | Kotlin Stub | `…/core/rl/LoraTrainer.kt` | `jniTrainingAvailable=false`; `stubTrainLora()` writes metadata-only `.bin` so versioning/hot-reload paths work without real training. | ⚙️ AUTO-FIXES when E03 resolved |
| E11 | 🔴 CRITICAL | Kotlin API | `…/core/system/ThermalGuard.kt:93` | `clearThermalStatusListeners()` called on `PowerManager` — this method does **NOT exist** in the Android SDK. Causes `Unresolved reference` build failure. Fix: store the `Consumer<Int>` reference at object level; call `removeThermalStatusListener(consumer)` with the same instance. | ✅ FIXED — `thermalConsumer` field stores lambda; `unregisterThermalManager()` calls `removeThermalStatusListener(thermalConsumer)` |
| E12 | 🟠 HIGH | TS Spec | `artifacts/mobile/native-bindings/NativeAgentCore.ts` | 6 TurboModule spec signatures do not match `AgentCoreModule.kt` implementations. With `newArchEnabled=true`, codegen generates C++ stubs from this spec — mismatches cause calling convention divergence and runtime crashes. Mismatched methods: `getObjectLabels` (missing `screenHash`), `saveObjectLabels` (missing `appPackage`, `screenHash`), `enrichLabelsWithLLM` (missing `screenContext`), `getProgressContext` (wrong return type `Object` vs `string`), `initGoals` (wrong param `goalsJson` vs `goal + subTasksJson`), `enqueueTask` (wrong param `taskJson` vs `goal + appPackage + priority`, wrong return `boolean` vs `Object`). | ✅ FIXED — all 6 signatures corrected to match Kotlin `@ReactMethod` declarations and `AgentCoreBridge.ts` call sites |
| E13 | 🟢 LOW | Kotlin | `…/core/agent/AgentLoop.kt:28-29` and `…/core/rl/LearningScheduler.kt:14-16` | Redundant self-imports: classes in the same package explicitly imported (`AppSkillRegistry`, `TaskQueueManager` in AgentLoop; `LlmRewardEnricher`, `LoraTrainer`, `PolicyNetwork` in LearningScheduler). Kotlin resolves same-package symbols without imports. Also: `android.util.Log` import appeared after kotlinx imports in AgentLoop (misordered). | ✅ FIXED — removed all same-package imports; reordered AgentLoop import block alphabetically |

---

## Fixed in Prior Session (Task #2)

| ID | Type | Description |
|----|------|-------------|
| P01 | TypeScript | Root `tsconfig.json` — removed 3 stale lib/ project references |
| P02 | Workspace | Removed empty `packages/schemas/` stub conflicting with canonical `shared/schemas/` |
| P03 | TypeScript | Added `@types/react` to `packages/ui-core/package.json` devDeps |
| P04 | TypeScript | Added `@types/lodash` to `artifacts/web-dashboard/package.json` |
| P05 | Dependencies | Pinned `react-native-keyboard-controller` to `1.18.5` |

---

## EAS Build & Runtime Download Flow

### Build time (EAS Cloud — automatic)
```
eas build --profile development --platform android
  └─ package.json: "eas-build-pre-install" → scripts/eas-pre-install.sh
       └─ git clone --depth=1 https://github.com/ggerganov/llama.cpp
              → android/app/src/main/cpp/llama.cpp/
  └─ Gradle → CMake → NDK arm64-v8a compile
       └─ libllama-jni.so compiled into APK  ← E10a + E10b auto-resolve here
```

### Runtime (first launch — sequential, in order)
```
Step 1  GGUF model         ~870 MB  HuggingFace   (user-triggered via startModelDownload())
        ModelDownloadService foreground service — resumable, progress notification
        Event: model_download_progress / model_download_complete

Step 2  MiniLM-L6-v2 ONNX  ~23 MB  HuggingFace   (auto, after GGUF ready)
        EmbeddingModelManager.download() — resumable
        Event: bootstrap_stage { stage:"downloading_minilm", step:2, percent }

Step 3  EfficientDet-Lite0  ~4.4 MB Google CDN    (auto, after MiniLM ready)
        ObjectDetectorEngine.ensureModel() — simple single-pass
        Event: bootstrap_stage { stage:"downloading_detector", step:3, percent }

Step 4  READY
        All models on disk. AgentLoop can run full-capability steps.
        Event: bootstrap_stage { stage:"ready", step:4, percent:100 }
```

Once compiled, **E10a and E10b auto-resolve** — both `LlamaEngine` and `LoraTrainer`
detect `libllama-jni.so` on startup and switch from stub → real JNI automatically.

---

## Notes

- `org.json.JSONArray/JSONObject` in Kotlin — **not errors**, standard Android SDK class in `android.jar`
- `ComposeMainActivity` registered in `AndroidManifest.xml` but not set as launcher — **intentional**, secondary activity for Jetpack Compose native UI testing
- `@tanstack/react-query` polyfills (`@stardazed/streams-text-encoding`, `@ungap/structured-clone`) — **not dead code**, required for React Native Web fetch compatibility
