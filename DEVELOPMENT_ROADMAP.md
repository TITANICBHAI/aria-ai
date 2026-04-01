# ARIA Agent — Development Roadmap

> Derived from both technical feasibility documents.
> Tick boxes as tasks complete. Each section maps to a real implementation step.
> Device target: Samsung Galaxy M31 · Exynos 9611 · 6GB LPDDR4X RAM

---

## Legend
- `[ ]` Not started
- `[~]` In progress
- `[x]` Complete

---

## Phase 0 — Foundation (Monorepo + JS UI Shell)

### 0.1 Monorepo Structure
- [x] Create pnpm workspace monorepo
- [x] Configure `pnpm-workspace.yaml` with `artifacts/*`, `lib/*`, `scripts`
- [x] Set `node-linker=hoisted` in `.npmrc` (required for Kotlin native module autolinking)
- [ ] Update `settings.gradle` to point `reactNativeDir` → workspace root (not local path)
- [ ] Update `build.gradle` to resolve `codegenDir` from hoisted root
- [ ] Add `android/` directory with full Kotlin project structure:
  ```
  android/
  ├── core/ai/        ← llama.cpp bindings
  ├── core/ocr/       ← ML Kit wrapper
  ├── core/rl/        ← RL module
  ├── core/memory/    ← SQLite + embeddings
  ├── system/accessibility/
  ├── system/screen/
  ├── system/actions/
  ├── bridge/turbo/   ← TurboModules
  ├── bridge/dto/     ← Data contracts
  └── ui-native/      ← future Jetpack Compose
  ```
- [ ] Add `models/llama/` and `models/adapters/` directories
- [ ] Add `shared/schemas/` for JS ↔ Kotlin data contracts

### 0.2 React Native New Architecture
- [x] Enable `newArchEnabled: true` in `app.json`
- [ ] Verify Hermes is the JS engine in Android build config
- [ ] Confirm JSI (not legacy bridge) is active in the build
- [ ] Verify TurboModule codegen runs during Gradle build

### 0.3 JS UI Shell (Phase 1)
- [x] Dashboard screen (status, metrics, module health)
- [x] Control screen (goal input, start/pause/stop)
- [x] Activity screen (action log + memory browser)
- [x] Modules screen (per-module deep status)
- [x] Settings screen (model config, RL toggle, LoRA path)
- [x] `AgentCoreBridge.ts` — TurboModule contract with Phase 1 stubs
- [x] `AgentContext.tsx` — centralized bridge state + polling
- [x] Dark space theme (`#0a0f1e` background, cyan primary)

---

## Phase 1 — LLM Integration (Core Brain — Kotlin)

### 1.1 Model Acquisition & Storage
- [ ] **Choose model:** `Llama-3.2-1B-Instruct-Q4_K_M.gguf` from HuggingFace (`bartowski/Llama-3.2-1B-Instruct-GGUF`)
  - Size on disk: ~870 MB
  - RAM at runtime: ~1,500–1,900 MB
  - Speed on M31: ~10–15 tok/s (sufficient — agent doesn't need real-time speed)
  - **NOT** BF16 (2358 MB, leaves no headroom)
  - **NOT** IQ2_S (2-bit, too inaccurate for navigation tasks)
- [ ] Set model destination: `context.filesDir + "/models/llama-3.2-1b-q4_k_m.gguf"`
- [ ] Create `android/core/ai/ModelDownloadService.kt` (foreground service)
  - Download URL: `https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf`
  - Show persistent notification with progress % and MB downloaded
  - Resume partial downloads (check `Content-Range`)
  - Verify SHA256 after download
  - Emit progress events back to JS via TurboModule callback
- [ ] Add download screen to JS UI (shown on first launch if model missing)
  - Progress bar, MB/total display, cancel button
  - Calls `AgentCoreBridge.downloadModel(url, dest, callback)`

### 1.2 llama.cpp Integration (JNI/NDK)
- [ ] Add llama.cpp as Android NDK submodule (`android/core/ai/llama.cpp/`)
- [ ] Write `CMakeLists.txt` to build llama.cpp as shared library (`.so`)
  - Enable ARM NEON SIMD instructions (automatic on Exynos A73)
  - Enable Vulkan GPU offload (Mali-G72 support, reduces CPU pressure)
  - Set `LLAMA_METAL=OFF`, `LLAMA_CUBLAS=OFF` (Android-specific flags)
- [ ] Write `LlamaJNI.kt` — Kotlin-side JNI wrapper:
  - `loadModel(path: String, contextSize: Int, nGpuLayers: Int): Long` → returns model handle
  - `createContext(modelHandle: Long): Long`
  - `runInference(ctx: Long, prompt: String, maxTokens: Int, callback: TokenCallback): String`
  - `freeModel(handle: Long)`
- [ ] Set `use_mmap = true` for efficient memory mapping on device
- [ ] Configure `n_ctx = 4096` (practical limit for M31; full 128K is infeasible on 6GB)
- [ ] Run inference on background thread (Kotlin Coroutine, `Dispatchers.Default`)
- [ ] Alternatively: evaluate MediaPipe LiteRT-LM (`litert-lm` Gradle dep):
  - `LlmInference.createFromOptions(context, LlmInferenceOptions.builder().setModelPath(path)...)`
  - Supports GGUF natively, handles GPU/NNAPI automatically
  - Better multimodal support if vision modality needed

### 1.3 LLM TurboModule Bridge
- [ ] Create `android/bridge/turbo/AgentCoreModule.kt`:
  ```kotlin
  class AgentCoreModule(reactContext: ReactApplicationContext) :
      ReactContextBaseJavaModule(reactContext), TurboModule {
      override fun getName() = "AgentCore"
      @ReactMethod fun loadModel(path: String, promise: Promise)
      @ReactMethod fun runInference(prompt: String, maxTokens: Int, promise: Promise)
      @ReactMethod fun downloadModel(url: String, dest: String, promise: Promise)
  }
  ```
- [ ] Register module in `ReactPackage` → `ReactNativeHost`
- [ ] Update `AgentCoreBridge.ts` stubs → real `NativeModules.AgentCore.*` calls
- [ ] Add TypeScript spec file for TurboModule codegen (`NativeAgentCore.ts`)

### 1.4 Prompt Engineering
- [ ] Design system prompt for agent reasoning:
  ```
  You are an autonomous Android agent. You observe screens and decide actions.
  Available tools: Click(node_id), Swipe(dir, node_id), Type(node_id, text), Scroll(dir)
  Always respond in JSON: {"tool": "Click", "node_id": "btn_play", "reason": "..."}
  ```
- [ ] Cap input at 512 tokens (reduce inference time on M31)
- [ ] Implement multi-turn context window (last N turns fit in 4096 token budget)

---

## Phase 2 — Perception (Screen Reading)

### 2.1 Screen Capture (MediaProjection)
- [ ] Create `android/system/screen/ScreenCaptureService.kt` (foreground service)
- [ ] Implement `MediaProjectionManager.createScreenCaptureIntent()` user consent flow
- [ ] Create `VirtualDisplay` from consent token
- [ ] Project screen onto `ImageReader` Surface
- [ ] Downsample captured bitmap to **512×512** before processing
  - Reduces memory bandwidth on Mali-G72
  - Sufficient for OCR and UI element detection
- [ ] Expose via TurboModule: `captureScreen(): String` → returns base64 JPEG or file path
- [ ] Handle `Exynos 9611` memory bandwidth limit — do NOT capture at full resolution

### 2.2 OCR Engine (ML Kit)
- [ ] Add `com.google.mlkit:text-recognition` Gradle dependency
- [ ] Create `android/core/ocr/OcrEngine.kt`:
  - Input: `Bitmap` (512×512 downsampled)
  - Output: "white-space structured text" (preserves relative position of text nodes)
  - Maintain spatial layout: label above input field, price next to product
- [ ] White-space alignment algorithm:
  - Sort text blocks by Y coordinate
  - Group into rows by Y-proximity threshold
  - Reconstruct layout as aligned text lines
- [ ] Run on background thread (Coroutine)
- [ ] Expose via TurboModule: `runOcr(imagePath: String): String`

### 2.3 Accessibility Tree Parser
- [ ] Create `android/system/accessibility/AgentAccessibilityService.kt`
  - Extends `AccessibilityService`
  - Declare in `AndroidManifest.xml` with `BIND_ACCESSIBILITY_SERVICE` permission
- [ ] Implement node tree traversal:
  - Filter to interactable nodes only (`isClickable`, `isEditable`, etc.)
  - Assign semantic IDs: `[#1]`, `[#2]`, etc.
  - Pair IDs with text labels
- [ ] Convert tree to LLM-friendly summary:
  ```
  [#1] Button: "Play" (center)
  [#2] Icon: "Settings" (top-right)
  [#3] EditText: "Search..." (top)
  ```
- [ ] Expose via TurboModule: `getAccessibilityTree(): String`

### 2.4 Semantic Fusion (Observation Phase)
- [ ] Create `android/core/ai/ScreenObserver.kt`
- [ ] Fuse OCR text + accessibility tree into unified semantic map:
  - Match OCR text blocks to accessibility node positions
  - Annotate visual elements not in accessibility tree (icons, images) using OCR only
- [ ] Output single string fed directly into LLM context

---

## Phase 3 — Action Layer

### 3.1 Gesture Engine
- [ ] Create `android/system/actions/GestureEngine.kt`
- [ ] Implement `dispatchTap(nodeId: String)`:
  - Resolve `nodeId` → `AccessibilityNodeInfo` → bounding rect center (X, Y)
  - `AccessibilityService.dispatchGesture(GestureDescription, callback)`
- [ ] Implement `dispatchSwipe(direction: String, nodeId: String)`:
  - Build `GestureDescription.StrokeDescription` path
  - Duration: 300ms (natural swipe timing)
- [ ] Implement `dispatchText(nodeId: String, text: String)`:
  - `performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)`
- [ ] Implement `dispatchScroll(direction: String, nodeId: String)`:
  - `performAction(ACTION_SCROLL_FORWARD / ACTION_SCROLL_BACKWARD)`
- [ ] Implement `dispatchLongPress(nodeId: String)`:
  - 800ms `GestureDescription` stroke
- [ ] Expose all via TurboModule: `executeAction(actionJson: String): Boolean`

### 3.2 Action Verification
- [ ] After each action, wait for `AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED`
- [ ] Capture new screen state (observation loop)
- [ ] Return success/failure + new screen summary to LLM context
- [ ] Feed result back into LLM as next-turn context (feedback loop)

### 3.3 Autonomous Agent Loop
- [ ] Create `android/core/ai/AgentLoop.kt` (background Coroutine loop):
  ```
  while (goalActive) {
    screenState = observe()         // MediaProjection + OCR + A11y tree
    action = llm.reason(goal, screenState, history)  // LLM inference
    result = gestureEngine.execute(action)           // dispatch gesture
    history.add(screenState, action, result)        // multi-turn memory
    if (result.goalAchieved) break
  }
  ```
- [ ] Implement goal completion detection
- [ ] Implement error recovery (action failed → LLM re-reasons with failure context)

---

## Phase 4 — Memory & Learning

### 4.1 SQLite Memory Store
- [ ] Create `android/core/memory/MemoryStore.kt`
- [ ] Schema:
  ```sql
  CREATE TABLE memory (
    id TEXT PRIMARY KEY,
    app TEXT,
    summary TEXT,
    embedding BLOB,     -- float array (file-based)
    confidence REAL,
    usage_count INTEGER,
    created_at INTEGER
  );
  ```
- [ ] Store successful action traces as memory entries
- [ ] Retrieve relevant memories via embedding similarity before each LLM call
- [ ] Expose via TurboModule: `getMemoryEntries()`, `clearMemory()`

### 4.2 File-Based Embeddings
- [ ] Create `android/core/memory/EmbeddingEngine.kt`
- [ ] Use a tiny embedding model (MiniLM or similar, <50MB) for similarity search
- [ ] Store embeddings as binary blobs in SQLite or flat files
- [ ] Cosine similarity retrieval for top-K relevant memories

### 4.3 LoRA Adapter (On-Device Fine-Tuning)
- [ ] Design "collect-then-train" strategy (from technical document):
  - **During active use:** record (screen state, action, reward) tuples
  - **During charging + idle:** run LoRA fine-tuning on collected data
  - Never run training during active inference (thermal + RAM conflict)
- [ ] Create `android/core/rl/LoraTrainer.kt`
- [ ] Implement thermal guard: check device temperature before training starts
  - Pause if battery temp > 40°C
  - Use `Window.setSustainedPerformanceMode()` during training
- [ ] LoRA rank: r=8, alpha=16 (keeps adapter <10MB)
- [ ] Adapter output path: `filesDir/adapters/lora_latest.bin`
- [ ] Load adapter at inference time via llama.cpp's lora API

### 4.4 Reinforcement Learning Module
- [ ] Create `android/core/rl/PolicyGradient.kt` (REINFORCE algorithm)
- [ ] State: downsampled pixel values + OCR text embedding
- [ ] Action space: tap, swipe-up, swipe-down, swipe-left, swipe-right, type, back
- [ ] Reward signal:
  - Positive: score increase, goal achieved, progress bar advance
  - Negative: "game over", no screen change after action, timeout
- [ ] Policy network: small MLP (2-3 layers, <5MB), NOT the LLM
  - LLM provides language reasoning
  - Policy network handles fine-grained action selection
- [ ] Inverse RL from video (future):
  - OCR on YouTube video frames
  - Learn from human expert action sequences

---

## Phase 5 — Model Delivery Strategy

### 5.1 First-Launch Auto-Download (Current / Phase 1-3)
- [ ] On app start, `ModelManager.kt` checks if GGUF exists in `filesDir/models/`
- [ ] If missing, trigger `ModelDownloadService` (foreground service, persistent notification)
- [ ] Download from: `https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf`
- [ ] Resume support via `Range` headers (important — 870MB can fail mid-download)
- [ ] SHA256 verification after download
- [ ] JS download screen shows progress via TurboModule callback
- [ ] Only proceed to agent after model is verified on disk

### 5.2 Play Asset Delivery — OBB Replacement (Play Store / Phase 5+)
- [ ] Convert app to Android App Bundle (AAB) format — EAS already does this
- [ ] Create on-demand asset pack for model:
  ```
  build.gradle:
  assetPacks = [":model-pack"]
  ```
- [ ] `model-pack/src/main/assets/models/` contains the GGUF
- [ ] In-app: `AssetPackManager.fetch(listOf("model-pack"))` with progress listener
- [ ] Play Store streams asset on first launch (no separate download dialog needed)
- [ ] This is the modern OBB equivalent — works with EAS + Play Store
- [ ] Note: requires Play Store distribution. Sideloaded APKs use Option 5.1

### 5.3 EAS Build Configuration
- [ ] Do NOT bundle GGUF in the app binary (APK limit: 150MB, model: 870MB)
- [ ] `eas.json` build profile should not reference model files
- [ ] Model download handled entirely by Kotlin at runtime
- [ ] Keep `assets/models/` directory empty in repo (add to `.gitignore`)

---

## Phase 6 — Optimization & Thermal Management

### 6.1 Inference Performance
- [ ] Benchmark on M31: target >8 tok/s minimum for agent usability
- [ ] Enable Vulkan GPU offload in llama.cpp (`n_gpu_layers = 32`)
- [ ] Use `NNAPI` delegate via MediaPipe if llama.cpp GPU offload is unstable
- [ ] Cap context at 4096 tokens (not 128K — quadratic memory scaling on M31)
- [ ] Cap per-turn input at 512 tokens
- [ ] Use memory mapping (`use_mmap = true`) to avoid loading full model into RAM

### 6.2 Thermal Management
- [ ] Implement `ThermalManager` listener (API 29+):
  - `THERMAL_STATUS_LIGHT` → throttle screenshot frequency
  - `THERMAL_STATUS_MODERATE` → pause RL training
  - `THERMAL_STATUS_SEVERE` → pause inference, notify user
- [ ] Use `Window.setSustainedPerformanceMode()` during extended sessions
- [ ] Run LoRA training only during charging (register `BatteryManager` receiver)
- [ ] Screen capture frequency: 1-2 FPS for navigation tasks (not 60 FPS)

### 6.3 Memory Footprint Budget (6GB M31)
| Component | RAM Budget |
|-----------|-----------|
| Android OS + system | ~2,000 MB |
| App UI (React Native) | ~150 MB |
| Llama 3.2-1B Q4_K_M | ~1,700 MB |
| Screen buffer (512×512) | ~10 MB |
| ML Kit OCR | ~100 MB |
| RL module + policy net | ~50 MB |
| Memory store + embeddings | ~100 MB |
| **Total** | **~4,110 MB** |
| **Headroom** | **~1,890 MB** |

---

## Phase 7 — Phase 2 Migration (JS → Kotlin thinning)

- [ ] Move agent loop coordination from JS context → Kotlin `AgentLoop.kt`
- [ ] Remove polling from `AgentContext.tsx` — switch to push events via TurboModule callbacks
- [ ] Thin `AgentCoreBridge.ts` to pure pass-through (no local state)
- [ ] Move config management to Kotlin `SharedPreferences` / DataStore
- [ ] JS becomes a display layer only: receives events, renders UI, sends commands

---

## Phase 8 — Phase 3 Migration (Jetpack Compose)

- [ ] Build `android/ui-native/` with Jetpack Compose
- [ ] Mirror all 5 screens: Dashboard, Control, Activity, Modules, Settings
- [ ] Remove React Native dependency from build
- [ ] Remove JS/Metro bundler from the app entirely
- [ ] Full Kotlin stack achieved

---

## Testing Milestones

- [ ] Inference benchmark: `llama.cpp` runs Llama 3.2-1B Q4_K_M at ≥8 tok/s on M31
- [ ] OCR accuracy: ML Kit extracts structured text from typical Android app screenshots
- [ ] Gesture accuracy: agent taps correct element from accessibility node ID
- [ ] Full loop test: agent completes a 3-step task (e.g., open Settings → WiFi → toggle) without human intervention
- [ ] Memory test: agent recalls a previously successful app workflow from SQLite
- [ ] LoRA test: model improves on repeated task after one fine-tune cycle
- [ ] Thermal test: app survives 30 minutes of continuous agent operation without crash
- [ ] RAM test: total RSS ≤ 4.5 GB during active inference session

---

## Current Phase Status

| Phase | Status | Description |
|-------|--------|-------------|
| 0 — Foundation | `[~]` In progress | JS UI shell done, Kotlin structure pending |
| 1 — LLM Integration | `[ ]` Not started | |
| 2 — Perception | `[ ]` Not started | |
| 3 — Action Layer | `[ ]` Not started | |
| 4 — Memory & Learning | `[ ]` Not started | |
| 5 — Model Delivery | `[ ]` Not started | Design done, implementation pending |
| 6 — Optimization | `[ ]` Not started | |
| 7 — Phase 2 JS Thinning | `[ ]` Not started | |
| 8 — Phase 3 Jetpack Compose | `[ ]` Not started | |
