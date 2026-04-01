# ARIA Agent — Development Roadmap

> Derived fully from both technical documents.
> Tick boxes as tasks complete. Each section maps to a real implementation step.
> Device: Samsung Galaxy M31 · Exynos 9611 · 6GB LPDDR4X RAM · No cloud. Ever.

---

## Legend
- `[ ]` Not started
- `[~]` In progress  
- `[x]` Complete

---

## How The Full System Works (Read This First)

The agent is NOT trained from scratch. It starts with a **pre-trained base model** (Llama 3.2-1B Q4_K_M — already trained by Meta on internet-scale data). That model provides reasoning and language ability out of the box.

What IS built from zero is:
- The agent's **knowledge of your specific phone, apps, and tasks**
- The **RL policy** for game-playing and app navigation
- The **LoRA adapters** that fine-tune the base LLM on your usage over time

### The Learning Pipeline (this is the core loop)

```
┌─────────────────────────────────────────────────────────────────┐
│                    STARTING FROM ZERO                           │
│  Base Llama 3.2-1B (Meta pre-trained) loaded into device       │
│  No task data. No RL policy. No LoRA adapters. Yet.            │
└─────────────────┬───────────────────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────────────────┐
│  STEP 1 — OBSERVE                                               │
│  MediaProjection → screenshot (512×512)                         │
│  ML Kit OCR → white-space structured text                       │
│  AccessibilityService → UI node tree → semantic IDs             │
│  Fused: "[#1] Button:Play [#2] EditText:Search [#3] Icon:Menu"  │
└─────────────────┬───────────────────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────────────────┐
│  STEP 2 — REASON (Llama 3.2-1B, ~10-15 tok/s)                 │
│  Prompt: goal + screen summary + history + available tools      │
│  Output: {"tool":"Click","node_id":"#1","reason":"Play starts"} │
└─────────────────┬───────────────────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────────────────┐
│  STEP 3 — ACT                                                   │
│  GestureEngine resolves node_id → X,Y coordinates              │
│  AccessibilityService.dispatchGesture() → physical touch event  │
│  Wait for screen change → verify success/failure                │
└─────────────────┬───────────────────────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────────────────┐
│  STEP 4 — COLLECT DATA (always, every loop)                     │
│  Record: (screen_state, action_taken, result, reward)           │
│  Store as experience tuple in SQLite                            │
│  Tag: app name, task type, success/failure, edge case flag      │
└─────────────────┬───────────────────────────────────────────────┘
                  │
         ┌────────┴─────────┐
         │ During active use │     During charging + idle
         │ COLLECT ONLY      │──────────────┐
         └───────────────────┘              │
                                            ▼
                          ┌─────────────────────────────────────┐
                          │  STEP 5 — PROCESS (offline/charging) │
                          │                                       │
                          │  RL Processing (REINFORCE):           │
                          │  (state,action,reward) tuples →       │
                          │  policy gradient update →             │
                          │  optimized action-value pairs         │
                          │                                       │
                          │  IRL Processing (from YouTube):       │
                          │  OCR video frames → human sequences → │
                          │  extract expert (state→action) pairs →│
                          │  pre-training data for policy         │
                          │                                       │
                          │  LoRA Fine-Tuning:                    │
                          │  successful traces → adapter weights   │
                          │  r=8, alpha=16, <10MB output          │
                          └──────────────┬──────────────────────┘
                                         │
                                         ▼
                          ┌──────────────────────────────────────┐
                          │  STEP 6 — OPTIMIZED VALUES           │
                          │  Updated LoRA adapter loaded         │
                          │  Updated policy network weights      │
                          │  Edge cases stored in memory DB      │
                          └──────────────┬───────────────────────┘
                                         │
                                         ▼
                          ┌──────────────────────────────────────┐
                          │  BACK TO STEP 1 — NEXT TASK          │
                          │  Agent is now smarter than before    │
                          │  Generates better data this time     │
                          │  Remembers edge cases from SQLite    │
                          └──────────────────────────────────────┘
```

### Bootstrap Reality (Starting From Zero — The Hard Part)

**Problem:** On Day 1, there is no RL data, no policy network, no LoRA adapter, no task traces.

**Solution:**
1. **Base Llama 3.2-1B handles Day 1.** Meta's pre-training gives it general reasoning. It can already read a screen summary and suggest actions — imperfectly, but functionally.
2. **Exploration mode.** The RL policy starts in random-then-guided mode: try random valid actions, record the results. This collects the first experience data even without knowing what's good.
3. **IRL bootstrapping.** Before any personal usage data exists, the agent can watch YouTube videos of app usage via OCR on frames — extracting human expert (state→action) sequences as initial pre-training data for the policy network.
4. **First 10-50 tasks.** The base LLM guides these with no LoRA. Experience tuples accumulate in SQLite.
5. **First training cycle.** After enough data, the first LoRA adapter is computed during idle charging. The policy network gets its first update.
6. **Flywheel begins.** Each task generates better data. Each training cycle makes the agent more accurate. Edge cases get stored and recalled.

### Core Feasibility Strategy — "Collect-then-Train"

> The feasibility of this entire system hinges on the **collect-then-train** strategy:
> separate data collection (during active use) from model training (during idle charging).
>
> - **Active use:** agent collects experience tuples (screen_state, action, reward). No training.
> - **Idle + charging:** training runs — RL policy update, LoRA fine-tune, IRL processing.
>
> This overcomes the Exynos 9611's performance limits. Training and inference never compete for RAM
> or CPU at the same time. Without this separation, sustained performance is not feasible on 6GB hardware.

---

## Phase 0 — Foundation (Monorepo + JS UI Shell)

### 0.1 Monorepo Structure

#### Architectural Ownership Rule
> Applications depend on packages. Packages NEVER depend on applications.
> This maintains a predictable, acyclic dependency graph and clear ownership boundaries.

#### Canonical Directory Layout (from technical spec)
| Directory | Content Type | Technical Purpose |
|-----------|--------------|-------------------|
| `apps/mobile-agent/` | React Native (TS/Kotlin) | Primary Android app — bridge, manifest, entry point |
| `apps/web-dashboard/` | React / Next.js | Local web UI for monitoring agent logs and RL metrics |
| `packages/brain/` | Kotlin / C++ | Core AI logic, LLM inference wrappers, JNI implementations |
| `packages/learning/` | Kotlin / DL4J | RL modules (REINFORCE policy gradient) + LoRA fine-tuning logic |
| `packages/ui-core/` | TypeScript / React | Shared UI components used across mobile app and web dashboard |
| `packages/shared-utils/` | TypeScript / JS | Common utility functions for data formatting and configuration |

> **Current implementation note:** The project uses `artifacts/mobile/` (mobile app) and `artifacts/api-server/` (API) in place of the `apps/` prefix. The `packages/` split is implemented as `android/core/` (brain) and `android/core/rl/` (learning) inside the mobile artifact. This structure maps to the canonical spec above.

#### Implementation Checklist
- [x] Create pnpm workspace monorepo
- [x] Configure `pnpm-workspace.yaml` with `artifacts/*`, `lib/*`, `scripts`
- [ ] Add `packages/ui-core/` — shared UI component library (used by both mobile + web dashboard)
- [ ] Add `packages/shared-utils/` — common data formatting and configuration utilities
- [ ] Add `apps/web-dashboard/` — local Next.js monitoring interface for logs and RL metrics (see Phase 0.4)
- [ ] Add `shared/schemas/` for JS ↔ Kotlin data contracts

#### Dependency Resolution and Hoisting Mechanics
> **Root problem:** React Native's Metro bundler assumes a flat project structure where all dependencies
> are in a local `node_modules/`. In a monorepo, pnpm hoists shared deps to the repo root — which
> Metro and Gradle cannot find without explicit path adjustments. Without this, Kotlin native module
> autolinking silently fails and produces mysterious compilation errors.

- [x] Set `node-linker=hoisted` in `.npmrc` — forces pnpm to hoist all deps to root (Metro-compatible flat layout; prevents autolinking failures in Kotlin native modules)
- [x] Update `settings.gradle` → point `reactNativeDir` to workspace root (Gradle resolves React Native from hoisted root, not local `node_modules/`)
- [x] Update `build.gradle` → resolve `codegenDir` from hoisted root (TurboModule codegen finds its source from the correct hoisted location)

#### Kotlin Android Project Structure
- [x] Create full `android/` Kotlin project structure:
  ```
  android/
  ├── core/
  │   ├── ai/           ← llama.cpp JNI, model manager, agent loop  [maps to packages/brain/]
  │   ├── ocr/          ← ML Kit OCR wrapper                        [maps to packages/brain/]
  │   ├── rl/           ← policy network (DL4J), LoRA trainer, IRL  [maps to packages/learning/]
  │   └── memory/       ← SQLite experience store, embeddings
  ├── system/
  │   ├── accessibility/ ← AgentAccessibilityService
  │   ├── screen/        ← MediaProjection capture service
  │   └── actions/       ← GestureEngine
  ├── bridge/
  │   ├── turbo/         ← TurboModules (JSI)
  │   └── dto/           ← data contracts
  └── ui-native/         ← future Jetpack Compose (Phase 11)
  ```
- [x] Add `models/llama/` and `models/adapters/` dirs (empty, in `.gitignore`)

### 0.2 React Native New Architecture — JSI Bridge

#### Why JSI Instead of the Legacy Bridge
> The legacy React Native bridge serialized all JS↔Kotlin communication as **async JSON messages**.
> This introduced non-trivial latency — every call crossed a thread boundary via a serialization queue.
> For an AI agent, this meant every `runInference()` or `captureScreen()` call had measurable overhead
> before it even reached Kotlin.
>
> **JSI (JavaScript Interface)** eliminates this by allowing the Hermes JS engine to hold **direct
> references to Kotlin/C++ host objects**. There is no serialization. There is no async queue.
> The call is synchronous at the C++ layer — near-native performance.
>
> For the agent specifically: TurboModules via JSI means when the JS UI dispatches a task to the
> Kotlin brain, it executes with minimal overhead. Kotlin then uses Coroutines to manage threading
> internally (`Dispatchers.Default` for inference, never blocking the main UI thread).

- [x] `newArchEnabled: true` in `app.json`
- [x] `hermesEnabled=true` in `gradle.properties`
- [x] JSI bridge confirmed active via `DefaultNewArchitectureEntryPoint.load()` in MainApplication
- [x] Kotlin Coroutines manage high-performance threading — inference on `Dispatchers.Default`, never blocking main thread
- [ ] Verify TurboModule codegen runs at Gradle build time (needs EAS build or local SDK)

### 0.3 JS UI Shell (Phase 1 — complete)
- [x] Dashboard screen (status, metrics, module health)
- [x] Control screen (goal input, presets, start/pause/stop)
- [x] Activity screen (action log + memory browser)
- [x] Modules screen (per-module deep status)
- [x] Settings screen (model config, RL toggle, LoRA path)
- [x] `AgentCoreBridge.ts` — TurboModule contract with Phase 1 stubs
- [x] `AgentContext.tsx` — centralized bridge state + polling
- [x] Dark space theme (`#0a0f1e` / `#00d4ff` / `#7c3aed`)

### 0.4 Web Dashboard (`apps/web-dashboard/`) — Future
> A local (not cloud) web-based interface for monitoring agent internals. Shares `packages/ui-core/`
> components with the mobile app. Runs on the same device or local network — no external server.

- [ ] Scaffold Next.js app at `apps/web-dashboard/` (or `artifacts/web-dashboard/`)
- [ ] Real-time agent log viewer — streams from SQLite experience store
- [ ] RL metrics dashboard — episodes run, reward history, policy loss curve
- [ ] LoRA adapter version tracker — shows each adapter version, training date, success rate delta
- [ ] Edge case browser — lists stored edge cases with screen patterns and resolutions
- [ ] Memory store explorer — embeddings count, DB size, top retrieved memories
- [ ] Connects to same SQLite DB as Kotlin brain (read-only, on-device access)
- [ ] Consumes `packages/ui-core/` shared components

---

## Phase 1 — LLM: The Reasoning Engine

> **Role of LLM:** Language reasoning only. It reads screen summaries and decides what action to take.
> It does NOT play games (too slow). It does NOT do RL. That's the policy network's job.
> It DOES improve over time via LoRA adapters computed from successful task traces.

### 1.1 Model Choice — Decided

**Model:** `Llama-3.2-1B-Instruct-Q4_K_M.gguf`
- Source: `bartowski/Llama-3.2-1B-Instruct-GGUF` on HuggingFace
- Disk: ~870 MB · RSS RAM: ~1,500–1,900 MB · Speed: ~8–15 tok/s on M31

#### Llama 3.2-1B Internal Architecture
- **Type:** Decoder-only transformer · 1.23 billion parameters
- **Attention:** Grouped-Query Attention (GQA) — reduces memory bandwidth for key/value tensors, improving inference scalability on low-memory hardware. Critical for M31 where bandwidth is the bottleneck.
- **Distillation:** Distilled from Llama 3.1 8B and 70B. Retains significant reasoning performance of larger models despite the 1B parameter count.
- **Context window:** 128,000 tokens (theoretical max) — but hardware-limited to 4,096 on M31. Memory usage grows **quadratically** with sequence length; 128K would OOM the device. 4,096 is the hard cap.
- **Vision-to-Action:** Multimodal training (vision + text) gives the model the ability to analyze screenshots and UI images and output structured actions. This is the decisive advantage over logic-only models.
- **Multilinguality:** 128-language support — useful for reading foreign-language app UIs.

#### Available RAM on M31
- Total: 6 GB LPDDR4X
- After Android OS + system services: **~2.5–3.5 GB available** for application use
- This is the hard budget. LLM + OCR + screen buffers + policy network must all fit here.

#### Quantization Comparison
| Level | Disk (MB) | RSS RAM (MB) | Speed on M31 | Decision |
|-------|-----------|--------------|--------------|----------|
| BF16 (16-bit) | ~2,358 | ~3,185 | < 2 tok/s | ❌ OOM — no headroom for OCR + screen |
| **Q4_K_M (4-bit)** | **~870** | **~1,500–1,900** | **~10–15 tok/s** | **✅ Chosen** |
| IQ2_S (2-bit) | ~581 | ~900–1,100 | ~20+ tok/s | ❌ Low IFEval score → navigation fails |

> **IFEval (Instruction Following Evaluation):** the standard benchmark for whether a model reliably
> follows structured instructions. IQ2_S quantization degrades IFEval scores significantly — the model
> can no longer reliably produce valid JSON tool calls, making autonomous navigation unreliable.
> Q4_K_M is the minimum quantization level that maintains acceptable IFEval performance.

#### Llama 3.2-1B vs Phi-3 Mini — Decision
| Factor | Llama 3.2-1B | Phi-3 Mini (3.8B) |
|--------|--------------|-------------------|
| Parameters | 1.23B | 3.8B |
| RAM (Q4) | ~1,500–1,900 MB | ~3,000+ MB (too heavy with vision services) |
| Vision/screen tasks | ✅ Trained on multimodal, Vision-to-Action capable | ❌ No vision training |
| Reasoning/math/code | Good | ✅ Outperforms models twice its size |
| Best for this agent | ✅ Screen reading, UI navigation, accessibility | Logic/math sub-tasks only |

> **Conclusion:** Llama 3.2-1B is the definitive choice for the reasoning engine. Phi-3 Mini could
> theoretically handle pure-logic sub-tasks, but its RAM footprint makes it incompatible with
> simultaneous vision services on 6GB hardware. Larger Llama variants (3B+) are also ruled out.

### 1.2 Model Download (First Launch)
- [x] `android/core/ai/ModelManager.kt` — checks if GGUF exists + size > 800MB
- [x] `android/core/ai/ModelDownloadService.kt` — foreground service:
  - URL: `https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf`
  - OkHttp with `Range` header → resumes partial downloads
  - Persistent notification: "Downloading AI brain... 45% (392 MB / 870 MB)"
  - SHA256 verification on completion
  - Emits progress events → JS via TurboModule `DeviceEventEmitter`
- [x] JS download screen (shown on first launch if model missing):
  - Progress bar + MB/total + estimated time
  - "Downloads once — 870 MB needed for on-device AI. No cloud ever."
  - Wired to `model_download_progress` and `model_download_complete` events
- [ ] EAS build: do NOT bundle GGUF (APK limit 150 MB vs model 870 MB)
- [ ] Play Asset Delivery (future Play Store): on-demand asset pack via `AssetPackManager`

### 1.3 Inference Framework Decision — llama.cpp vs MediaPipe

> Two frameworks were evaluated for running Llama 3.2-1B on Android. This decision is recorded here
> permanently because switching frameworks later would require significant JNI and Gradle rework.

| Factor | llama.cpp (via JNI/NDK) | MediaPipe LLM / LiteRT-LM |
|--------|------------------------|---------------------------|
| Integration | C++ via JNI + NDK CMake | Gradle dependency (`tasks-genai`) |
| Memory control | ✅ `use_mmap=true`, manual context | Managed by MediaPipe runtime |
| GPU offload | ✅ Vulkan (`LLAMA_VULKAN=ON`) | ✅ GPU + NNAPI acceleration |
| LoRA support | ✅ llama.cpp LoRA API (our path) | `LlmInferenceOptions.setLoraConfig()` (Gemma-2, Phi-2 only) |
| Multimodal | Manual (feed OCR+tree as text) | ✅ `setEnableVisionModality(true)` native |
| GGUF format | ✅ Native format | ✅ LiteRT-LM supports GGUF |
| Control level | ✅ Fine-grained (our choice) | Higher-level, less customizable |

**Decision: llama.cpp via JNI.**
Reasons: `use_mmap` is essential for the M31 RAM budget. Vulkan offload is available. LoRA fine-tuning via llama.cpp API aligns with our on-device training pipeline. MediaPipe's LoRA support is limited to Google's own models (Gemma-2, Phi-2) — not Llama 3.2.

> **MediaPipe/LiteRT-LM is noted for future consideration** if Vision-to-Action (native multimodal)
> becomes a priority and Llama 3.2's multimodal path via llama.cpp proves too complex to wire.

#### Key MediaPipe APIs (documented for reference)
- `com.google.mediapipe:tasks-genai` — Gradle dependency
- `LlmInference.createFromOptions(context, options)` — load model
- `LlmInferenceSession.LlmInferenceSessionOptions.setEnableVisionModality(true)` — multimodal input
- `LlmInferenceOptions.setLoraConfig(loraPath)` — load LoRA adapter (Gemma-2 / Phi-2 only)
- `LiteRT-LM` — Google's newer runtime; GGUF-compatible; Kotlin-native; GPU/NPU via NNAPI

---

### 1.4 llama.cpp JNI Integration
- [ ] Add llama.cpp as NDK submodule: `android/core/ai/llama.cpp/`
- [ ] `CMakeLists.txt`:
  - ARM NEON SIMD enabled (automatic on Cortex-A73) — ARM NEON provides hardware-accelerated matrix multiplication on mobile CPUs; this is the primary compute path for inference on the A73 cores
  - Vulkan GPU offload: `LLAMA_VULKAN=ON` (Mali-G72 MP3 support)
  - `LLAMA_METAL=OFF`, `LLAMA_CUBLAS=OFF`
- [ ] `LlamaJNI.kt`:
  - `loadModel(path, contextSize=4096, nGpuLayers=32): Long`
  - `createContext(modelHandle): Long`
  - `runInference(ctx, prompt, maxTokens, tokenCallback): String`
  - `freeModel(handle)`
- [ ] `use_mmap = true` (memory mapping — avoids loading full model into RAM at once)
- [ ] `n_ctx = 4096` (practical M31 limit; 128K is quadratic memory growth = OOM)
- [ ] Inference on `Dispatchers.Default` Coroutine (never block main thread)

### 1.4 TurboModule Bridge for LLM
- [x] `android/bridge/turbo/AgentCoreModule.kt`:
  ```kotlin
  @ReactMethod fun checkModelReady(promise: Promise)
  @ReactMethod fun startModelDownload(promise: Promise)
  @ReactMethod fun loadModel(path: String, promise: Promise)
  @ReactMethod fun runInference(prompt: String, maxTokens: Int, promise: Promise)
  @ReactMethod fun getAgentStatus(promise: Promise)
  ```
- [x] Register in `ReactPackage` → `ReactNativeHost`
- [~] Replace stubs in `AgentCoreBridge.ts` with real `NativeModules.AgentCore.*` calls (real on Android; web stubs remain for Expo web preview)
- [ ] TypeScript codegen spec file: `NativeAgentCore.ts`

### 1.5 Prompt Design
- [ ] System prompt:
  ```
  You are an autonomous Android agent. You see a structured screen summary.
  Decide the next single action. Always respond in JSON only.
  Tools: Click(node_id), Swipe(dir, node_id), Type(node_id, text), Scroll(dir), Back()
  Example: {"tool":"Click","node_id":"#3","reason":"Search bar to enter query"}
  ```
- [ ] Cap input: 512 tokens per turn
- [ ] Multi-turn window: keep last N turns within 4096 total token budget
- [ ] Inject relevant memories (top-3 similar past traces) before each inference call

---

## Phase 2 — Perception (Eyes of the Agent)

> **Role:** The agent must see and understand the screen before it can reason or act.
> Two parallel pipelines: visual (MediaProjection + OCR) and structural (Accessibility tree).
> Both fuse into one semantic map fed to the LLM.

### 2.1 Screen Capture (MediaProjection)
- [x] `android/system/screen/ScreenCaptureService.kt` (foreground service)
- [x] `MediaProjectionManager.createScreenCaptureIntent()` → user consent → token
- [x] Create `VirtualDisplay` from token → project onto `ImageReader` Surface
- [x] Downsample to **512×512** before any processing:
  - Reduces Mali-G72 memory bandwidth pressure
  - Sufficient resolution for OCR and UI detection
  - Full resolution would exhaust Exynos 9611 memory bandwidth
- [x] Capture rate: 1-2 FPS for navigation tasks (not continuous — too hot)
- [x] TurboModule: `captureScreen(): String` → file path to JPEG

### 2.2 OCR Engine (ML Kit Text Recognition)
- [x] Add `com.google.mlkit:text-recognition` Gradle dependency (free, fully on-device)
- [x] `android/core/ocr/OcrEngine.kt`:
  - Input: `Bitmap` (512×512)
  - Process: `TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)`
  - Output: **white-space structured text** (spatial layout preserved)
- [x] White-space layout algorithm:
  - Get bounding box of each text block
  - Sort by Y coordinate (top to bottom)
  - Group blocks at similar Y into rows
  - Within each row, sort by X → reconstruct as text line
  - Result: "Price $9.99    [Add to Cart]" (position-aware, not just word bag)
- [x] Run on background Coroutine thread
- [x] TurboModule: `runOcr(imagePath): String`

### 2.3 Accessibility Tree Parser
- [x] `android/system/accessibility/AgentAccessibilityService.kt`
  - Extends `AccessibilityService`
  - Manifest: `BIND_ACCESSIBILITY_SERVICE` permission + XML config
- [x] Node traversal:
  - Only interactable nodes (`isClickable`, `isScrollable`, `isEditable`, `isFocusable`)
  - Assign IDs: `[#1]`, `[#2]`, etc. (persistent within one screen state)
  - Record: class name, content description, text, bounds
- [x] LLM-friendly output:
  ```
  [#1] Button: "Play" at center
  [#2] EditText: "Search..." at top
  [#3] ImageButton: "Settings" at top-right
  [#4] ListView: scrollable (12 items)
  ```
- [x] TurboModule: `getAccessibilityTree(): String`

### 2.4 Semantic Fusion
- [x] `android/core/ai/ScreenObserver.kt`
- [x] Merge OCR spatial text + accessibility tree:
  - Cross-reference: OCR label "Price $9.99" near accessibility node → annotate node
  - Elements only visible in OCR (icons, images without text): keep in output
  - Elements only in accessibility tree (hidden/off-screen): skip
- [x] Single fused string → LLM input
- [x] TurboModule: `observeScreen(): String` (captures + OCR + tree → fused in one call)

---

## Phase 3 — Action Layer (Hands of the Agent)

### 3.1 Gesture Engine
- [x] `android/system/actions/GestureEngine.kt`
- [x] `tap(nodeId)` — Sequential clicks (navigating through settings menus):
  - Resolve nodeId → `AccessibilityNodeInfo` → `getBoundsInScreen(rect)` → center
  - `dispatchGesture(GestureDescription { path(Point(x,y), 0, 50ms) })`
- [x] `swipe(direction, nodeId)` — Scrolling through long documents or gesture-based games:
  - Compute start/end from node bounds + direction
  - `StrokeDescription` path over 300ms (natural timing)
- [x] `typeText(nodeId, text)` — Entering code into an IDE, drafting messages in a communication app:
  - `performAction(ACTION_SET_TEXT, bundle.putCharSequence(...))`
- [x] `scroll(direction, nodeId)`:
  - `performAction(ACTION_SCROLL_FORWARD / ACTION_SCROLL_BACKWARD)`
- [x] `longPress(nodeId)` — Triggering context menus in third-party apps:
  - Same as tap but 800ms stroke duration
- [x] `back()`:
  - `performGlobalAction(GLOBAL_ACTION_BACK)`
- [x] TurboModule: `executeAction(actionJson): Boolean`

### 3.2 Verification Loop and Multi-Turn Memory

> The autonomous loop is NOT a one-way command sequence. It is a **feedback-driven cycle**.
> Every action's result — success, failure, or transition to a new screen state — is fed back into
> the LLM's context for the next turn. This is **multi-turn memory**: the agent knows which moves
> worked and can recover from errors such as a button that didn't respond or a pop-up that blocked its path.

- [x] After each action: listen for `AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED`
- [x] Timeout: 3 seconds (if no change → action failed)
- [x] Capture new screen state
- [x] Compute reward signal:
  - Success: screen changed as expected → +1
  - Partial: screen changed but unexpected state → 0
  - Failure: no change or error dialog appeared → -1
- [x] Feed (result, reward, new_screen_state) back into LLM context (multi-turn memory) and SQLite
- [x] Error recovery types handled:
  - Button did not respond → re-observe screen, retry with different node ID
  - Pop-up blocked path → detect overlay, dismiss it, resume original task
  - Wrong app opened → `back()` until correct screen, restate goal

### 3.3 Autonomous Agent Loop
- [x] `android/core/ai/AgentLoop.kt` (long-running background Coroutine):
  ```kotlin
  while (goal.isActive) {
      screenState = screenObserver.observe()          // Phase 2
      relevantMemory = memoryStore.retrieve(screenState) // top-3 similar past
      action = llm.reason(goal, screenState, history, relevantMemory)
      result = gestureEngine.execute(action)          // Phase 3
      reward = result.computeReward()
      experienceTuple = ExperienceTuple(screenState, action, result, reward)
      memoryStore.save(experienceTuple)               // Phase 4
      history.addTurn(screenState, action, result)
      if (result.goalAchieved || history.tooLong()) break
  }
  ```
- [x] Goal completion detection (screen contains target text / accessibility node)
- [x] Error recovery: if 3 consecutive failures → re-observe + re-reason with failure context
- [x] History pruning: keep within 4096 token budget

---

## Phase 4 — Data Collection (The Raw Material)

> This is the foundation of everything. The agent cannot improve without data.
> Data = (screen_state, action, result, reward) tuples. Every agent loop produces them.

### 4.1 Experience Store (SQLite)
- [x] `android/core/memory/ExperienceStore.kt`
- [x] Schema:
  ```sql
  CREATE TABLE experience (
    id TEXT PRIMARY KEY,
    timestamp INTEGER,
    app_package TEXT,
    task_type TEXT,          -- 'navigation', 'game', 'coding', 'web'
    screen_summary TEXT,     -- fused OCR + a11y text (the observation)
    action_json TEXT,        -- {"tool":"Click","node_id":"#3"}
    result TEXT,             -- 'success' / 'failure' / 'partial'
    reward REAL,             -- -1.0 to +1.0
    is_edge_case INTEGER,    -- 1 if unusual/unexpected situation
    edge_case_notes TEXT,    -- what was unusual
    session_id TEXT
  );

  CREATE TABLE edge_cases (
    id TEXT PRIMARY KEY,
    screen_pattern TEXT,     -- what the screen looked like
    resolution TEXT,         -- what eventually worked
    app_package TEXT,
    recall_count INTEGER,    -- how often this was retrieved
    last_seen INTEGER
  );
  ```
- [x] TurboModule: `getExperienceStats(): {totalTuples, byApp, successRate}`
- [x] TurboModule: `getEdgeCases(): EdgeCase[]`

### 4.2 Edge Case Detection & Memory
- [x] Flag an experience as edge case when:
  - Action failed 2+ times before succeeding
  - Screen state didn't match any seen before
  - Reward was negative but task eventually succeeded with different approach
- [x] Store resolution path (what finally worked) in `edge_cases` table
- [x] Before each LLM call: query `edge_cases` for matching screen pattern
- [x] Inject matching edge case into LLM prompt: "Note: This screen previously required X instead of Y"
- [x] TurboModule: `getEdgeCases(): EdgeCase[]`

### 4.3 Embedding Engine (for Memory Retrieval)
- [x] `android/core/memory/EmbeddingEngine.kt`
- [x] Tiny embedding model (MiniLM L6-v2, ~22MB ONNX) for similarity search — migrated from TFLite to ONNX Runtime 1.19.2
- [x] Embed each screen_summary at storage time
- [x] Store embedding as BLOB in SQLite
- [x] At retrieval time: embed current screen, cosine similarity → top-3 most similar past experiences
- [x] Inject top-3 into LLM context before inference

---

## Phase 5 — RL/IRL Processing (Turning Data into Intelligence)

> This is the "second brain." Runs ONLY during idle + charging. Never during active inference.
> Takes raw experience tuples → produces optimized action values → policy network weights update.

### 5.1 Reinforcement Learning — Policy Network
> **Framework:** DeepLearning4J (DL4J) — Kotlin/JVM-native deep learning library. Chosen because it runs
> fully on-device within the JVM (no Python runtime needed), integrates directly with Kotlin coroutines,
> and supports the REINFORCE policy gradient algorithm on Android. Lives in `packages/learning/` per spec.

- [x] `android/core/rl/PolicyNetwork.kt` — small MLP (3 layers, ~5MB) built with DL4J:
  ```
  Input:  screen embedding (128-dim) + goal embedding (128-dim)
  Hidden: 256 → 128 neurons
  Output: action probabilities (7 actions: tap, swipe×4, type, scroll, back)
  ```
- [x] This is NOT the LLM. The LLM is for reasoning. The policy net is for fast game/app action selection.

#### RL Component Mapping (Android Agent Implementation)
| RL Component | Android Agent Implementation |
|--------------|------------------------------|
| Agent | Kotlin-based policy network (`PolicyNetwork.kt`) |
| Environment | Android OS + current foreground application |
| State | Downsampled RGB pixel values (224×224) + OCR text summaries |
| Action | Localized touch, swipe, and lift events via `dispatchGesture()` |
| Reward | Success notifications or changes in UI state (e.g. progress bar advance, score increase) |

- [x] Training algorithm: REINFORCE (policy gradient) via DL4J:
  ```kotlin
  // For each experience tuple in batch:
  loss = -log(policy(action|state)) * reward
  // Accumulate gradients, update weights via DL4J optimizer
  ```
- [x] Training trigger: idle + charging detected → start `PolicyTrainer` background job
- [x] Thermal guard: battery temp > 40°C → pause training immediately
- [x] Save updated policy weights to `filesDir/rl/policy_latest.bin`
- [x] Load updated policy at next agent session start
- [x] Optimized values produced: P(action | screen_state) → agent uses this to bias action selection
- [x] `adamStep` and `lastPolicyLoss` exposed to JS for live monitoring in Modules screen

### 5.2 Inverse Reinforcement Learning (IRL) — Learning from YouTube
- [x] `android/core/rl/IrlModule.kt`
- [x] Process: user watches a YouTube tutorial → agent runs in background:
  1. MediaProjection captures video frames at 0.5 FPS
  2. ML Kit OCR extracts text from each frame (identifies app, menus, text)
  3. ObjectLabelStore provides human-annotated element names for known UI elements
  4. LlamaEngine (when loaded) reasons about screen deltas and infers action in structured JSON
  5. Heuristic Jaccard word-diff fallback when LLM not available
  6. Stores as (state_before, inferred_action, state_after) → marks as IRL data; tracks `llmAssistedCount`
- [x] IRL data is labelled as `task_type='irl_expert'` in experience store
- [x] Used as pre-training data for policy network before any personal usage data exists
- [x] This solves the bootstrap problem: agent learns from watching humans before doing anything itself
- [x] TurboModule: `startIrlCapture(videoDescription): void` and `processIrlVideo(path, goal, pkg)`

### 5.3 LoRA Fine-Tuning (LLM Improvement)

#### How LoRA Works (Parameter-Efficient Fine-Tuning)
> Traditional fine-tuning updates ALL parameters of the model — for a 1B model this requires massive
> compute and memory. LoRA (Low-Rank Adaptation) instead **freezes the base model weights** and
> inserts small, trainable rank-decomposition matrices into each transformer layer.
>
> **Mathematical representation:**
> ```
> W = W₀ + BA
> ```
> Where:
> - `W₀` = frozen pre-trained weight matrix (unchanged)
> - `B` and `A` = low-rank matrices being trained (the LoRA adapter)
> - rank `r=8` means A is (d × 8) and B is (8 × d) — far fewer parameters than W₀ (d × d)
>
> Only `A` and `B` are updated during training — typically **less than 1% of total model parameters**.
> This reduces VRAM requirements to approximately **1/8th of full fine-tuning**.
> Output adapter size: < 10 MB. The base model (870 MB) is never modified.

- [x] `android/core/rl/LoraTrainer.kt`
- [x] Input: successful experience traces from SQLite (result='success', reward > 0)
- [x] Convert traces to fine-tuning format:
  ```json
  {"instruction": "Screen: [#1] Button:Play [#2]...", "response": "{\"tool\":\"Click\",\"node_id\":\"#1\"}"}
  ```
- [x] LoRA config: rank r=8, alpha=16 → adapter size <10MB (< 1% of base model params)
- [x] Training: on `Dispatchers.Default`, chunked to avoid thermal spike
- [x] Output: `filesDir/adapters/lora_v{N}.bin` (versioned)
- [x] Load new adapter via llama.cpp LoRA API at next agent session
- [x] The LLM is now better at this phone's specific apps, UI patterns, and the user's task style

### 5.4 Optimized Value Output (What Training Produces)
After each training cycle, the agent has:
- [x] **Updated policy weights** → better action selection probabilities for seen screen types
- [x] **Updated LoRA adapter** → LLM better at reasoning about this phone's specific apps
- [x] **Edge case store populated** → agent handles unusual situations that previously caused failures
- [x] **Embedding index updated** → faster, more relevant memory retrieval

These are the "optimized values" — they make the NEXT task loop smarter than the last.

---

## Phase 6 — Game Playing (RL Agent, Not LLM)

> Games are too fast for Llama (10-15 tok/s). A separate policy network handles games.
> The LLM's role in games: parse game state via OCR + accessibility, suggest strategy in natural language.
> The policy network's role: execute fast actions (tap, swipe) based on pixel state.

### 6.1 Game RL Agent
- [ ] Detect when foreground app is a game (check if accessibility tree has game-specific elements)
- [ ] Switch from LLM-guided to policy-network-guided loop for games
- [ ] Game loop:
  ```kotlin
  while (gameActive) {
    pixelState = captureScreen().downsample(224, 224).toFloatArray()
    ocrText = ocr.run(screen)
    action = policyNetwork.predict(pixelState + ocrText.embedding)
    gestureEngine.execute(action)
    reward = detectScoreChange() or detectGameOver()
    experience.save(pixelState, action, reward, task_type='game')
  }
  ```
- [ ] Score detection: OCR on known score regions → compare before/after action
- [ ] Game over detection: look for "Game Over", "Try Again", "Retry" via OCR
- [ ] Reward: +score_delta, -1 for game over, +5 for high score milestone

### 6.2 IRL from Game Videos (YouTube)
- [ ] Watch a YouTube video of someone playing the target game
- [ ] IRL module extracts: frame-by-frame game state + inferred tap/swipe between frames
- [ ] Bootstraps policy network before agent plays even one round itself
- [ ] Solves cold-start for games: agent arrives at a new game already knowing basic strategies

---

## Phase 7 — Continuous Learning Scheduler

> The scheduler decides when to collect, when to train, and when to load updated weights.
> This is what makes the agent get better over time without any user involvement.

- [x] `android/core/rl/LearningScheduler.kt`
- [x] Events that trigger training:
  - Device plugged in to charger
  - Screen off > 10 minutes
  - Experience store has > 50 new tuples since last training
- [x] Events that pause/cancel training:
  - Battery temperature > 40°C (via ThermalGuard.isTrainingSafe())
  - User unlocks screen
  - Available RAM drops below 1GB
  - Unplug from charger
- [x] Training sequence order:
  1. Policy network update (REINFORCE on new game/app experience)
  2. IRL processing (if new video frames captured)
  3. LoRA training (if enough successful LLM traces accumulated)
  4. Edge case index rebuild
  5. Embedding index update
- [x] Notify JS on completion: `learning_cycle_complete` event → update "Last trained" in UI

---

## Phase 8 — Optimization & Thermal Management

### 8.0 Exynos 9611 Hardware Profile (Critical Constraints)

> Understanding the target chip is essential to every performance decision in this project.
> All inference speed estimates, RAM budgets, and thermal rules are derived from this profile.

| Property | Value | Implication |
|----------|-------|-------------|
| Process node | 10nm | Moderate efficiency — thermal throttling expected under sustained load |
| CPU config | big.LITTLE: 4× Cortex-A73 @ 2.3 GHz + 4× Cortex-A53 @ 1.7 GHz | A73 cores handle inference; A53 cores handle background tasks |
| GPU | Mali-G72 MP3 | Vulkan supported (limited) — used for LLM layer offload |
| NPU | ❌ None | No dedicated neural processing unit. All AI runs on CPU/GPU only. This is a critical constraint — no NNAPI acceleration path available unlike Snapdragon 888+ devices. |
| RAM | 6 GB LPDDR4X | Shared between OS, LLM, OCR, RL, screen buffers — every MB counts |
| Inference speed | ~8–12 tok/s (Q4_K_M) | Sufficient for autonomous agent. Not conversational speed. Agent can afford 2–5 seconds of "thinking time" per action — a human working through a UI does the same. |
| vs flagship | ~50–70 tok/s on Snapdragon 8 Elite | M31 is 5–8× slower, but the task requirement (one action per screen) makes this workable |

> **No NPU means:** MediaPipe's NNAPI acceleration path is unavailable. llama.cpp with Vulkan offload
> is the correct path. CPU inference on A73 cores with ARM NEON SIMD is the primary compute method.

### 8.1 RAM Budget (6GB M31 — Must Stay Under 4.5GB Total)
| Component | Budget |
|-----------|--------|
| Android OS + system services | ~2,000 MB |
| React Native shell | ~150 MB |
| Llama 3.2-1B Q4_K_M (loaded) | ~1,700 MB |
| Screen buffer (512×512 JPEG) | ~10 MB |
| ML Kit OCR engine | ~100 MB |
| Policy network (MLP) | ~5 MB |
| MiniLM embedding model | ~25 MB |
| SQLite + experience store | ~50 MB |
| LoRA adapter (active) | ~10 MB |
| **Total** | **~4,050 MB** |
| **Headroom** | **~1,950 MB** |

### 8.2 Thermal Rules
- [ ] `ThermalManager` listener (API 29+):
  - `THERMAL_STATUS_LIGHT`: throttle screen capture to 0.5 FPS
  - `THERMAL_STATUS_MODERATE`: pause RL training job
  - `THERMAL_STATUS_SEVERE`: pause all inference, show "Cooling down" in UI
- [ ] `Window.setSustainedPerformanceMode()` during extended sessions
- [ ] Screen capture: 1-2 FPS (navigation), 0.5 FPS (IRL video), 0 during training

### 8.3 Inference Performance
- [ ] Benchmark: target ≥8 tok/s on M31 (sufficient for agent, not conversational)
- [ ] Vulkan GPU offload: `n_gpu_layers = 32` in llama.cpp config
- [ ] Fallback to CPU if Vulkan init fails (Exynos 9611 Vulkan support is limited)
- [ ] `use_mmap = true` — model stays on disk, pages loaded on demand
- [ ] Context: hard cap 4096 tokens (not 128K — quadratic memory at longer contexts)

---

## Phase 9 — Model Delivery

### 9.1 First-Launch Download (Works for All Distribution)
- [x] `ModelManager.kt`: check `filesDir/models/` for GGUF + size validation
- [x] `ModelDownloadService.kt`: foreground service + OkHttp + Range header resume
- [x] JS download screen: shown before main tabs if model missing
- [x] SHA256 verification after complete download
- [x] `assets/models/` directory empty in repo (add `*.gguf` to `.gitignore`)

### 9.2 Play Asset Delivery (Play Store Distribution — Future)
- [ ] AAB format (EAS already builds AAB)
- [ ] Create on-demand asset pack `model-pack/`
- [ ] `AssetPackManager.fetch(listOf("model-pack"))` + progress listener
- [ ] This is the modern replacement for OBB files — Play Store streams the 870MB pack on first launch
- [ ] Requires Google Play distribution (sideloaded APKs use 9.1)

### 9.3 EAS Build Rules
- [ ] Never include GGUF in EAS build (APK hard limit: 150MB)
- [ ] Never include policy weights or LoRA adapters in build (they're generated on-device)
- [ ] App binary stays under 50MB
- [ ] All AI assets are runtime-only: downloaded or generated on the device

---

## Phase 10 — Phase 2: JS Thinning (Future)

- [ ] Move agent loop from JS context polling → Kotlin push events via TurboModule callbacks
- [ ] Remove all state from `AgentContext.tsx` — JS only renders what Kotlin pushes
- [ ] Remove `AgentCoreBridge.ts` stubs → all calls are real
- [ ] Config moves to Kotlin `DataStore`
- [ ] JS = rendering layer only

---

## Phase 11 — Phase 3: Full Kotlin + Jetpack Compose (Future)

- [ ] Build `android/ui-native/` screens in Jetpack Compose
  - Dashboard, Control, Activity, Modules, Settings
- [ ] Remove React Native from build
- [ ] Remove Metro bundler
- [ ] Pure Kotlin app

---

## Future Ideas — Not Yet Scheduled

> Ideas captured here for future planning. None of these are active tasks.
> Promote to a numbered Phase when ready to implement.

---

### Idea: Object Labeler Tool

**Concept:** A modal UI screen where the user can manually annotate objects on a captured screen. Each annotation gives the LLM richer context for reasoning about that screen.

**Why it matters:** The current perception pipeline (OCR + accessibility tree) extracts what exists on screen but loses semantic meaning — it knows a node is a "Button" but not that it's "the button that starts a payment flow." The object labeler lets humans inject that domain knowledge directly, making the LLM far more accurate on specific apps and workflows.

**How it would work:**
1. User opens the Object Labeler modal from the Control screen
2. The modal captures a screenshot (via MediaProjection bridge)
3. User taps anywhere on the screenshot image to place an annotation pin
4. Each pin has:
   - **Name** — user-assigned label (e.g. "Checkout Button")
   - **Context** — description of purpose (e.g. "Triggers the payment flow")
   - **Element Type** — button / text / input / icon / image / container / toggle / link
   - **LLM-generated fields** (via `runInference()`):
     - `meaning` — what this element means in the broader app context
     - `interactionHint` — how the agent should use this element
     - `reasoningContext` — a hint injected into the LLM prompt when this element is visible
     - `importanceScore` — 0–10 priority for agent attention
   - **Additional custom fields** — user can add arbitrary key/value pairs
5. "Auto-detect" button runs MediaPipe object detection + ML Kit OCR on the image, auto-placing pins at detected elements with OCR text pre-filled
6. "Enrich All" button sends all pins to Llama for bulk field generation
7. Individual pin "Generate" button enriches a single pin
8. Annotations are saved to SQLite and injected into the LLM's context whenever the matching screen is observed during agent operation

**Tech stack:**
- UI: React Native modal with `Pressable` + `onLayout` for normalized pin coordinates
- Screenshot capture: `captureScreenForLabeling()` → Kotlin MediaProjection bridge
- OCR at point: `runOcrAtPoint(imageUri, normX, normY)` → ML Kit on Kotlin side
- Object detection: `detectObjectsInImage(imageUri)` → MediaPipe on Kotlin side
- LLM enrichment: `enrichLabelsWithLLM(labels, screenContext)` → Llama 3.2-1B bridge
- Persistence: new `object_labels` table in SQLite, keyed by app package + screen hash
- Retrieval: during agent loop, query stored labels for current screen → inject into LLM prompt

**New bridge methods needed (Kotlin side):**
```kotlin
@ReactMethod fun captureScreenForLabeling(promise: Promise)
@ReactMethod fun runOcrAtPoint(imageUri: String, normX: Double, normY: Double, promise: Promise)
@ReactMethod fun detectObjectsInImage(imageUri: String, promise: Promise)
@ReactMethod fun enrichLabelsWithLLM(labelsJson: String, screenContext: String, promise: Promise)
@ReactMethod fun saveObjectLabels(appPackage: String, screenHash: String, labelsJson: String, promise: Promise)
@ReactMethod fun getObjectLabels(appPackage: String, screenHash: String, promise: Promise)
```

**New SQLite table needed:**
```sql
CREATE TABLE object_labels (
  id TEXT PRIMARY KEY,
  app_package TEXT,
  screen_hash TEXT,         -- hash of the OCR+a11y output for this screen
  name TEXT,
  context TEXT,
  x REAL,                   -- 0–1 normalized
  y REAL,
  element_type TEXT,
  meaning TEXT,
  interaction_hint TEXT,
  reasoning_context TEXT,
  importance_score INTEGER,
  additional_fields TEXT,   -- JSON blob
  created_at INTEGER,
  updated_at INTEGER
);
CREATE INDEX idx_labels_screen ON object_labels(app_package, screen_hash);
```

**When to implement:** After Phase 2 (Perception) and Phase 4 (Data Collection) are complete, since it depends on MediaProjection, ML Kit, and SQLite being live on-device.

---

## Testing Milestones

- [ ] **T1 — Model loads**: Llama 3.2-1B Q4_K_M runs at ≥8 tok/s on M31 without OOM
- [ ] **T2 — Screen reads**: OCR + accessibility tree fused output is parseable by LLM
- [ ] **T3 — Gesture works**: agent taps correct element from node ID (3/3 attempts)
- [ ] **T4 — 3-step task**: open Settings → WiFi → toggle, without human intervention
- [ ] **T5 — Data flows**: experience tuples appear in SQLite after each agent loop
- [ ] **T6 — RL trains**: policy network weights change after first training cycle
- [ ] **T7 — LoRA loads**: updated LoRA adapter loads at next session without crash
- [ ] **T8 — Edge case recalled**: agent retrieves and uses a previously stored edge case
- [ ] **T9 — IRL works**: OCR extracts action sequence from a 30-second YouTube capture
- [ ] **T10 — Thermal holds**: 30 minutes continuous inference without thermal shutdown
- [ ] **T11 — RAM holds**: total RSS ≤ 4.5GB during active inference

---

## Current Phase Status

| Phase | Status | Description |
|-------|--------|-------------|
| 0 — Foundation | `[x]` | JS UI shell done. Full android/ project created. Bridge wired. Download screen added. Permissions section in Settings with live status + deep-links added. |
| 1 — LLM: Reasoning Engine | `[~]` | LlamaEngine JNI written. LoRA adapter loading wired. updateConfig bug fixed (modelPath + loraAdapterPath now persist to SharedPreferences). Needs EAS build + llama.cpp NDK submodule. |
| 2 — Perception | `[x]` | ScreenObserver.kt (OCR + a11y fusion). ScreenCaptureService + OcrEngine + AgentAccessibilityService all implemented. |
| 3 — Action Layer | `[x]` | AgentLoop.kt (Observe→Reason→Act→Store). GestureEngine (tap/swipe/type/scroll/longPress/back). Thermal pause, multi-turn memory, error recovery all implemented. |
| 4 — Data Collection | `[x]` | ExperienceStore (SQLite) complete. EmbeddingEngine migrated to ONNX Runtime 1.19.2 (MiniLM-L6-v2, 384-dim). Hash fallback when model downloading. |
| 5 — RL/IRL Processing | `[x]` | PolicyNetwork (real REINFORCE + Adam, adamStep/lastPolicyLoss exposed to JS). IrlModule upgraded to 3-stage pipeline (OCR + ObjectLabelStore + LlamaEngine reasoning). LoraTrainer + LearningScheduler all wired. |
| 6 — Game Playing | `[ ]` | Game-specific RL loop + score detection |
| 7 — Learning Scheduler | `[x]` | LearningScheduler: ThermalGuard.isTrainingSafe() check, charging/screen-off triggers, training sequence order, learning_cycle_complete event. |
| 8 — Optimization | `[x]` | ThermalGuard.kt complete: ThermalManager API 29 listener + battery temp fallback. SAFE/LIGHT/MODERATE/SEVERE/CRITICAL levels. AgentLoop pauses on SEVERE+. LearningScheduler skips on MODERATE+. |
| 9 — Model Delivery | `[~]` | ModelDownloadService + ModelManager + JS download screen complete. Play Asset Delivery (Play Store path) pending. |
| 10 — JS Thinning | `[ ]` | Push events, remove state from JS |
| 11 — Jetpack Compose | `[ ]` | Full Kotlin UI replaces React Native |
