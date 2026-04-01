# Current Task

> Update this file every time you start a new task.
> Format: What → Why → How → Acceptance criteria → Blockers

---

## Task: Phase 1 — LLM Model Delivery System

**Status:** Not started  
**Priority:** Critical path — nothing else in Phase 1 works without the model on disk  
**Depends on:** Phase 0 (complete — JS UI shell done, Gradle Kotlin structure pending)

---

## WHAT

Build the model acquisition pipeline so the GGUF model lands in the app's internal storage on first launch and the LLM can be loaded for inference.

**Specific model:** `Llama-3.2-1B-Instruct-Q4_K_M.gguf`  
**Source:** `https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf`  
**Destination:** `context.filesDir/models/llama-3.2-1b-q4_k_m.gguf`  
**Size:** ~870 MB on disk → ~1,700 MB RSS when loaded

---

## WHY

### Why this model?
From the technical documents:
- The M31 has ~2.5–3.5 GB available for app use after the OS takes its share
- Llama 3.2-1B at Q4_K_M uses ~1,500–1,900 MB RAM — this fits with headroom for OCR + screen buffers
- BF16 (16-bit) would use ~3,185 MB — no headroom, guaranteed OOM crash
- IQ2_S (2-bit) uses ~900 MB but has poor IFEval (instruction-following accuracy) scores — navigation tasks would fail
- Llama 3.2-1B has **multimodal vision training** (unlike Phi-3), making it better for "vision-to-action" tasks where the agent reads screenshots
- Phi-3 at 3.8B parameters doesn't fit alongside vision and accessibility services on 6GB

### Why first-launch download instead of bundling?
- APK/AAB hard limit is ~150 MB. The GGUF is 870 MB. It cannot be bundled.
- Play Store OBBs are deprecated. Modern replacement is **Play Asset Delivery** (Phase 5 migration).
- First-launch download works for: Expo Go testing, sideloaded APK, Play Store (all distribution methods).
- HuggingFace provides resumable HTTPS downloads with Content-Range support.

### Why a foreground service?
- Android kills background processes aggressively to free RAM.
- An 870 MB download can take 5–20 minutes on mobile data.
- A foreground service with a persistent notification survives the download even if the user backgrounds the app.
- Required by Android API 26+ for long-running background work.

---

## HOW

### Step 1 — Kotlin: `ModelManager.kt`
```kotlin
// android/core/ai/ModelManager.kt
object ModelManager {
    fun modelPath(context: Context): File =
        File(context.filesDir, "models/llama-3.2-1b-q4_k_m.gguf")

    fun isModelReady(context: Context): Boolean =
        modelPath(context).exists() && modelPath(context).length() > 800_000_000L

    fun sha256(file: File): String // verify after download
}
```

### Step 2 — Kotlin: `ModelDownloadService.kt`
```kotlin
// android/core/ai/ModelDownloadService.kt
class ModelDownloadService : Service() {
    // Foreground service — shows notification: "Downloading AI model 45% (392 MB / 870 MB)"
    // Uses OkHttp with Range header support for resumable download
    // Emits progress via EventEmitter → JS receives it via DeviceEventEmitter
    // On complete: verify SHA256, then broadcast MODEL_READY event

    val MODEL_URL = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf"
}
```

### Step 3 — Kotlin: `AgentCoreModule.kt` (TurboModule)
```kotlin
@ReactMethod
fun checkModelReady(promise: Promise) {
    promise.resolve(ModelManager.isModelReady(reactApplicationContext))
}

@ReactMethod
fun startModelDownload(promise: Promise) {
    val intent = Intent(reactApplicationContext, ModelDownloadService::class.java)
    reactApplicationContext.startForegroundService(intent)
    promise.resolve(true)
}
```
Events pushed to JS:
- `model_download_progress` → `{ percent: 45, downloadedMb: 392, totalMb: 870 }`
- `model_download_complete` → `{ path: "/data/user/0/.../files/models/..." }`
- `model_download_error` → `{ error: "Network timeout" }`

### Step 4 — Update `AgentCoreBridge.ts` stubs
```typescript
// Replace stubs with real NativeModules calls:
import { NativeModules, NativeEventEmitter } from 'react-native';
const { AgentCore } = NativeModules;

checkModelReady: () => AgentCore.checkModelReady(),
startModelDownload: () => AgentCore.startModelDownload(),
```

### Step 5 — JS Download Screen
Add a download screen that shows when `checkModelReady()` returns `false`:
- Progress bar (animated, shows MB/total)
- "This downloads once — 870 MB required for on-device AI"
- Estimated time remaining
- Cancel button (pauses download, resumes next launch)
- Triggered in `app/_layout.tsx` before rendering main tabs

### Step 6 — Gradle: Resume Support with OkHttp
```groovy
// android/app/build.gradle
dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
```
Resume logic:
```kotlin
val existingSize = partialFile.length()
val request = Request.Builder()
    .url(MODEL_URL)
    .addHeader("Range", "bytes=$existingSize-")
    .build()
```

---

## Acceptance Criteria

- [ ] `ModelManager.isModelReady()` returns `false` on fresh install
- [ ] Download starts when JS calls `startModelDownload()`
- [ ] Foreground notification shows correct progress %
- [ ] Download survives app being backgrounded
- [ ] Partial download resumes correctly after network interruption
- [ ] SHA256 verification passes after complete download
- [ ] `model_download_complete` event fires and JS transitions to main tabs
- [ ] `ModelManager.isModelReady()` returns `true` on subsequent launches (no re-download)
- [ ] Total download size confirmed as ~870 MB

---

## Blockers

- **Kotlin Android project not yet initialized** — `android/` directory needs full Gradle setup before any Kotlin code can run
- **TurboModule codegen** — requires New Architecture Gradle plugin configuration
- **Manifest permissions** — `FOREGROUND_SERVICE`, `INTERNET`, `POST_NOTIFICATIONS` need to be declared

---

## Next Task (after this completes)

**Phase 1 — Task 1.2: llama.cpp JNI Integration**
Build the NDK/JNI layer that loads the GGUF from disk and runs inference via `llama.cpp`.
See `DEVELOPMENT_ROADMAP.md` → Phase 1 → 1.2 llama.cpp Integration.
