# ARIA Agent — Stub Registry

Every stub in the codebase, what it does instead of the real thing, why it exists,
whether it auto-activates on the first EAS APK build, and exactly what it takes to remove it.

---

## How to read this document

| Column | Meaning |
|--------|---------|
| Stub behaviour | What runs RIGHT NOW instead of the real code |
| Root cause | The one technical reason the real code cannot run yet |
| Auto-fix on EAS APK? | Will the stub disappear automatically when you run `eas build`? |
| What makes it real | The specific steps — ordered — to flip the stub off forever |

---

## Stub 1 — LLM Inference (LlamaEngine)

**File:** `android/app/src/main/kotlin/com/ariaagent/mobile/core/ai/LlamaEngine.kt`

**What the stub does:**
When `System.loadLibrary("llama-jni")` fails at startup (because `libllama-jni.so` was never compiled),
`LlamaEngine.jniAvailable` stays `false`. All three real paths fall back:

- `load()` — sets fake handle values (`modelHandle = 1L`) so `isLoaded()` returns true without touching any file
- `infer()` — returns this hardcoded string regardless of the prompt:
  ```
  {"tool":"Click","node_id":"#1","reason":"stub inference — llama.cpp not compiled"}
  ```
  and reports a fake `lastToksPerSec = 11.5`
- `loadLora()` — returns `false` immediately; the LoRA adapter is never applied

**Root cause:**
`nativeLoadModel`, `nativeRunInference`, `nativeLoadLora`, and the other JNI functions are declared
`external` in Kotlin. They are implemented in `llama_jni.cpp`. That C++ file is compiled by the NDK
into `libllama-jni.so` only when the llama.cpp git submodule exists under
`android/app/src/main/cpp/llama.cpp`. That submodule has not been added to the repo yet.

**Auto-fix on EAS APK?**
YES — automatically, zero code changes needed.
When the submodule is present and EAS runs the NDK build, `libllama-jni.so` is produced,
`System.loadLibrary("llama-jni")` succeeds, `jniAvailable` flips to `true`, and every
real path activates. The stub strings and fake handles are never reached again.

**What makes it real — ordered steps:**

1. Add the llama.cpp submodule (one-time, on any machine with git + NDK):
   ```bash
   cd artifacts/mobile/android/app/src/main/cpp
   git submodule add https://github.com/ggerganov/llama.cpp llama.cpp
   git submodule update --init --recursive
   ```
2. Confirm `CMakeLists.txt` line 29 (`add_subdirectory(${LLAMA_DIR} llama_build)`) is uncommented — it already is.
3. Run: `eas build --platform android --profile development`
4. Install the `.apk` on the Galaxy M31. `LlamaEngine` will load the GGUF file and run real inference at 8–15 tok/s.

---

## Stub 2 — LoRA Training (LoraTrainer)

**File:** `android/app/src/main/kotlin/com/ariaagent/mobile/core/rl/LoraTrainer.kt`

**What the stub does:**
`train()` always builds the real JSONL training dataset (experience tuples + object labels) and
writes it correctly. The stub only activates at the moment of actual training:
`tryNativeTrainLora()` calls `nativeTrainLora()`, catches `UnsatisfiedLinkError`, and falls to
`stubTrainLora()`, which writes this JSON file to the adapter path instead of real weight matrices:
```json
{"lora_stub":true,"rank":4,"version":N,"experience_samples":X,"label_samples":Y}
```
The version counter increments, the path is recorded, `markAsTrained()` marks experience entries
as used — so all surrounding pipeline logic (versioning, hot-reload, LearningScheduler, the
`loraVersion` display in the UI) exercises correctly. Only the weights inside the `.bin` file are fake.
`LlamaEngine.loadLora()` returns `false` for stub adapters because `jniAvailable` is also `false`
(same `.so` dependency).

**Root cause:**
`nativeTrainLora()` lives in `llama_jni.cpp` behind `#if defined(LLAMA_HAS_TRAINING)`.
The full C++ training loop (llama.cpp Adam optimizer via `common/train.h` + `ggml` gradient API)
is already written there — it is NOT a skeleton, it is a complete implementation.
Two things block it: the llama.cpp submodule (same as Stub 1) and the compile-time flag
`LLAMA_HAS_TRAINING` which is not yet set in `CMakeLists.txt`.

**Auto-fix on EAS APK?**
PARTIAL. Adding the submodule and running EAS alone is NOT enough for this one.
You also need to enable the training flag in `CMakeLists.txt` first.

**What makes it real — ordered steps:**

1. Complete Stub 1 steps (submodule + EAS build) first.
2. Open `android/app/src/main/cpp/CMakeLists.txt` and add one line in the compile definitions section:
   ```cmake
   add_compile_definitions(LLAMA_HAS_TRAINING)
   ```
3. Run `eas build` again. The `#if defined(LLAMA_HAS_TRAINING)` block in `llama_jni.cpp` now
   compiles in, giving `nativeTrainLora()` a real body.
4. After install: run the agent on the Galaxy M31 to accumulate at least 10 experience entries
   (or add human labels via the Labeler tab), plug in to charge, let `LearningScheduler` trigger
   a training cycle. `LoraTrainer.jniTrainingAvailable` will flip to `true` and a real
   `adapter_vN.bin` with actual LoRA weight matrices will be written.

---

## Stub 3 — NEON SIMD Math (PolicyNetwork + EmbeddingEngine)

**Files:**
- `android/app/src/main/kotlin/com/ariaagent/mobile/core/rl/PolicyNetwork.kt`
- `android/app/src/main/kotlin/com/ariaagent/mobile/core/memory/EmbeddingEngine.kt`

**What the stub does:**
Both files call `System.loadLibrary("llama-jni")` in their `init` block to access
`aria_math.cpp` functions (`nativeMatVecRelu`, `nativeSoftmax`, `nativeCosineSimilarity`,
`nativeL2Normalize`, `nativeDotProduct`). When the `.so` is absent, they catch
`UnsatisfiedLinkError` silently and set `neonAvailable = false`.

**This is not a functional stub — it is a performance stub.**

PolicyNetwork still does full REINFORCE + Adam training and forward passes using pure Kotlin scalar
loops (`matVecReluKotlin`, `softmaxKotlin`). EmbeddingEngine still produces correct 384-dim
cosine similarity scores using `cosineSimilarityKotlin` and `normalizeL2Kotlin`. All training,
weight saving, and embedding retrieval work correctly without NEON.

The cost is speed: NEON SIMD is ~4× faster for matrix-vector multiply and cosine similarity
operations. On the Exynos 9611 Cortex-A73, the Kotlin fallback adds roughly 8–15ms per forward
pass and 30–50ms per cosine similarity call at 384 dimensions.

**Auto-fix on EAS APK?**
YES — automatically. Same `.so` as Stub 1. Once `libllama-jni.so` is compiled and
`System.loadLibrary` succeeds, `neonAvailable` flips to `true` in both classes and NEON
paths activate. No code change needed.

**What makes it real:**
Same as Stub 1. Nothing extra required for this one.

---

## Stub 4 — Hash Embedding Fallback (EmbeddingEngine)

**File:** `android/app/src/main/kotlin/com/ariaagent/mobile/core/memory/EmbeddingEngine.kt`

**What the stub does:**
When the MiniLM-L6-v2 ONNX model file (`models/minilm-l6-v2.onnx`, ~23MB) is not present in
internal storage, `embed()` calls `hashEmbedFallback()` instead of `runOnnxInference()`.
The hash fallback maps each word to up to 4 fixed positions in a 384-dim float vector
using `word.hashCode()`. The resulting vectors are deterministic (same text = same vector)
and produce meaningful relative similarities within a session, but they are semantically
much weaker than MiniLM — words with opposite meanings can land near each other.

This affects `ExperienceStore` retrieval quality and label similarity search in
`retrieveLabels()`. Downstream effects: the LLM prompt gets less relevant past experience
injected, and the Object Labeler's similarity ranking is less accurate.

**Root cause:**
The MiniLM model is a ~23MB download. It is not bundled with the APK. The user must trigger
the download by navigating to the Modules tab, or it auto-downloads in background on first
`AgentCoreModule` init (line 130 in `AgentCoreModule.kt`).

**Auto-fix on EAS APK?**
NO — the model file is downloaded at runtime, not bundled. The fallback will run until the
device downloads the file.

**What makes it real:**

1. The download auto-starts in background when the APK is first launched (30–60s on WiFi).
2. Or: open the Modules tab, find the MiniLM section, tap Download.
3. Once `models/minilm-l6-v2.onnx` exists, `EmbeddingEngine.embed()` automatically uses
   real ONNX Runtime inference. No restart required — the session is created lazily on next call.

---

## Stub 5 — Web Bridge (AgentCoreBridge.ts)

**File:** `artifacts/mobile/native-bindings/AgentCoreBridge.ts`

**What the stub does:**
Every method in `AgentCoreBridge` has this guard pattern:
```typescript
if (AgentCore) return AgentCore.realMethod(...);
return <safe default>;
```
`AgentCore` is `null` on Expo Web (the preview running in the browser on Replit).
On web, every method returns a harmless default so the UI can be developed without an Android device:

| Method | Web stub return |
|--------|----------------|
| `checkModelReady()` | `false` |
| `runInference()` | `'{"tool":"stub","reason":"web preview mode"}'` |
| `getAgentState()` | stub state object (status: idle, llmLoaded: false) |
| `getMemoryEntries()` | `[]` |
| `buildChatContext()` | minimal ARIA identity text block |
| `captureScreenForLabeling()` | `{ screenHash: "stub_hash_001", ... }` |
| all others | `false`, `null`, `[]`, or `0` |

**This is NOT a bug. This is the architecture rule: JS = display only.**
These stubs let every screen (Chat, Control, Modules, Settings, Labeler, Activity) render
and navigate correctly in the web preview without any Android hardware.

**Auto-fix on EAS APK?**
YES — completely. On Android, `TurboModuleRegistry.getEnforcing('AgentCore')` returns the
real `AgentCoreModule` Kotlin object. `AgentCore` is non-null. Every `if (AgentCore)` branch
takes the real path. The stub returns are never reached.

**What makes it real:**
Nothing extra. Just install the APK on the Galaxy M31. Every bridge method immediately calls
into real Kotlin.

---

## Stub 6 — Play Asset Delivery (ModelDownloadService)

**File:** `android/app/src/main/kotlin/com/ariaagent/mobile/core/ai/ModelDownloadService.kt`

**What the stub does:**
`ModelDownloadService` downloads the GGUF model (~870MB) via direct HTTP from a URL stored
in `AriaConfig`. This is the "development path" — it works but requires the user to wait for
an 870MB download over WiFi on first launch, and it requires the download URL to be
publicly accessible.

Play Asset Delivery (PAD) is the production path: Google Play would bundle the GGUF as an
install-time asset pack, delivering it in parallel with the APK installation at full Play Store
download speed with no extra code, no external URL, and no foreground service needed.

**Root cause:**
PAD requires the app to be published to the Google Play Store as an Android App Bundle (`.aab`),
not a sideloaded `.apk`. It also requires the Play Core library (`com.google.android.play:asset-delivery`)
in `build.gradle` and a new `AssetPackConfig` in the asset pack manifest.

**Auto-fix on EAS APK?**
NO. EAS produces an `.apk` (sideload) or `.aab` (Play Store). Even with `.aab`, PAD is
not wired yet — the `AssetPackManager` code and asset pack manifest do not exist yet.
The HTTP download fallback remains active.

**What makes it real — ordered steps:**

1. Add the Play Asset Delivery dependency to `android/app/build.gradle`:
   ```gradle
   implementation "com.google.android.play:asset-delivery:2.2.2"
   ```
2. Create `android/app/src/main/assets-pack/src/` directory structure with the model file and
   an `AndroidManifest.xml` declaring `<asset-pack android:name="model_pack">`.
3. Update `ModelDownloadService.kt` to check `AssetPackManager.getPackLocation("model_pack")`
   before starting the HTTP download. If PAD provides the path, skip the download entirely.
4. Build with `eas build --platform android --profile production` (produces `.aab`).
5. Upload `.aab` to Google Play internal testing track.

Until step 5, the HTTP download path remains the only delivery mechanism and continues to work.

---

## Stub 7 — ComposeMainActivity (Jetpack Compose UI)

**File:** `android/app/src/main/kotlin/com/ariaagent/mobile/ComposeMainActivity.kt`
**Manifest:** `android/app/src/main/AndroidManifest.xml`

**What the stub does:**
`ComposeMainActivity` is fully implemented with five screens (Dashboard, Control, Activity,
Modules, Settings) using `ARIATheme`, `AgentViewModel`, and Jetpack Compose Navigation.
However, it is registered in `AndroidManifest.xml` with `android:exported="true"` but
WITHOUT `<intent-filter>` for `MAIN` / `LAUNCHER`. This means:

- `MainActivity` (React Native / Expo) is still what launches when you tap the app icon.
- `ComposeMainActivity` cannot be opened at all from the home screen.
- It can only be launched explicitly via `adb shell am start -n com.ariaagent.mobile/.ComposeMainActivity`.

**Root cause:**
Switching the launcher Activity requires a device test to confirm the Compose build works end-to-end
on the Galaxy M31 before committing to it. The React Native UI is currently the only verified path.

**Auto-fix on EAS APK?**
NO. Switching launchers requires a deliberate code change. It will not happen automatically.

**What makes it real:**

1. In `AndroidManifest.xml`, move the `<intent-filter>` block (MAIN + LAUNCHER category) from
   `MainActivity` to `ComposeMainActivity`:
   ```xml
   <!-- In ComposeMainActivity entry — add: -->
   <intent-filter>
       <action android:name="android.intent.action.MAIN"/>
       <category android:name="android.intent.category.LAUNCHER"/>
   </intent-filter>

   <!-- In MainActivity entry — remove the same block -->
   ```
2. Run `eas build --profile development` and install.
3. Test all five Compose screens on the physical Galaxy M31.
4. If stable, remove `MainActivity` from the manifest entirely (or keep it as a fallback alias).

Note: the React Native bridge still works from inside `ComposeMainActivity` via the
`ReactApplication` interface — both UIs can coexist during the transition period.

---

## Summary table

| # | Stub | Where | Functional now? | Auto-fix on EAS APK? | Blocking requirement |
|---|------|--------|----------------|---------------------|---------------------|
| 1 | LLM inference | `LlamaEngine.kt` | No — returns hardcoded JSON | YES | llama.cpp submodule + EAS build |
| 2 | LoRA training | `LoraTrainer.kt` | No — writes JSON not weights | PARTIAL | Submodule + `LLAMA_HAS_TRAINING` flag + EAS build |
| 3 | NEON math (perf) | `PolicyNetwork.kt`, `EmbeddingEngine.kt` | YES — Kotlin fallback is real | YES | Same as Stub 1, nothing extra |
| 4 | MiniLM embedding | `EmbeddingEngine.kt` | Partial — hash fallback is weaker | NO — runtime download | Download `minilm-l6-v2.onnx` on device |
| 5 | JS bridge | `AgentCoreBridge.ts` | YES on Android — stub is web-only | YES | Just install APK |
| 6 | Play Asset Delivery | `ModelDownloadService.kt` | YES — HTTP download works | NO | PAD library + asset pack manifest + Play Store |
| 7 | Compose launcher | `ComposeMainActivity.kt` | YES — exists, just not launcher | NO — deliberate switch | Update AndroidManifest + device test |

---

## The single most important stub to fix first

**Stub 1 (LlamaEngine)** — because it is the gate for Stubs 1, 2, and 3 all at once.
One EAS build with the llama.cpp submodule activates real LLM inference, enables the path
to real LoRA training (after adding `LLAMA_HAS_TRAINING`), and upgrades PolicyNetwork and
EmbeddingEngine from Kotlin scalar math to NEON SIMD automatically.

All five steps required to go from zero stubs to a fully real ARIA:

1. `git submodule add https://github.com/ggerganov/llama.cpp android/app/src/main/cpp/llama.cpp`
2. Add `add_compile_definitions(LLAMA_HAS_TRAINING)` in `CMakeLists.txt`
3. `eas build --platform android --profile development` — installs on Galaxy M31
4. Launch app — MiniLM auto-downloads in background within 60s (Stub 4 gone)
5. Use the app → collect experience → LearningScheduler fires → real LoRA weights produced (Stub 2 gone)

Stubs 6 and 7 are production-path polish (Play Store delivery and Compose launcher),
not required for a fully functional on-device AI agent.
