# ARIA Project — Comprehensive Gap Audit
> Last updated: April 05, 2026  
> Status key: `[ ]` Open · `[~]` In Progress · `[x]` Done  
> Sections: [1 Stub Files](#1-stub-files) · [2 Fake Core Features](#2-fake-core-features-stubs-masquerading-as-real-logic) · [3 ViewModel Data Disconnects](#3-viewmodel--data-layer-disconnects) · [4 Agent Loop Failure Modes](#4-agent-loop-failure-modes) · [5 UI Screen Holes](#5-ui-screens-with-known-holes) · [6 System-Level Gaps](#6-system-level-gaps) · [7 Connection Gaps](#7-connection-gaps-things-that-should-talk-but-dont) · [8 Dashboard Not Wired](#8-web-dashboard-not-connected-to-its-own-backend) · [9 Reality Check Admissions](#9-aria_reality_checkmd--confirmed-but-untracked-gaps) · [10 Migration Debt](#10-legacy--migration-debt) · [11 Manifest & Build](#11-manifest--build-gaps) · [12 Missing Infrastructure](#12-missing-infrastructure) · [Priority](#priority-order-recommended)

---

## 1. Stub Files

Files that exist only as placeholders and actively mislead anyone reading the codebase.

| | File | Problem | Phase Target |
|--|------|---------|-------------|
| `[ ]` | `MainActivity.kt` | No-op migration stub. Still declared in `AndroidManifest.xml` as an activity. Anyone reading the manifest will think it does something. | Phase 8 |
| `[ ]` | `expo/modules/ExpoModulesPackageList.kt` | Kept only to satisfy old React Native package declarations. Dead weight with no purpose in a native build. | Phase 8 |

**Action:** Delete both only after the Phase 8 gate-check confirms all Compose screens are verified and nothing still imports them.

---

## 2. Fake Core Features (Stubs Masquerading as Real Logic)

The most critical gaps. The engine looks wired up but fires blanks.

| | File | What It Claims | What It Actually Does |
|--|------|---------------|-----------------------|
| `[ ]` | `core/ai/LlamaEngine.kt` | On-device LLM inference | Returns a hardcoded JSON string: `{"tool":"Click","node_id":"#1","reason":"stub inference — llama.cpp not compiled"}` |
| `[ ]` | `core/ai/LlamaEngine.kt` → `loadVision()` | Load SmolVLM vision model | Returns a sentinel handle and pre-canned text descriptions. No model is loaded. |
| `[ ]` | `core/rl/LoraTrainer.kt` | Fine-tune model on-device via LoRA | Calls `stubTrainLora()` — writes a metadata-only `.bin` file. No weights are ever updated. |
| `[ ]` | `core/ai/InferenceEngineImpl.kt` | Full inference pipeline with error handling | TODOs at lines 168 and 189: error codes are swallowed silently instead of being surfaced. |
| `[ ]` | `core/rl/PolicyNetwork.kt` | Intelligent action selection | Starts with **random weights** on every fresh install. Agent decisions are essentially random until enough experience accumulates. |
| `[ ]` | `core/perception/ObjectDetectorEngine.kt` | Detect Android UI elements on screen | Uses **EfficientDet-Lite0**, which is trained on COCO categories (people, cars, dogs). It **cannot detect buttons, text fields, or any Android UI element**. Relies entirely on the Accessibility Tree for UI navigation. |
| `[ ]` | `core/rl/IrlModule.kt` → video path | Learn from video using LLM inference | Falls back to a **word-Jaccard heuristic** when LLM is not loaded — loses all coordinate data and only guesses action type from text differences. |

**Why this matters:** The agent loop runs, the UI shows activity, but no real intelligence is happening. A user or developer would not know this without reading deeply.

---

## 3. ViewModel & Data Layer Disconnects

The UI is built and shows these fields. The ViewModel has the state for them. But the underlying engines never actually send the data. Result: the UI always shows zeroes or stale defaults.

| | ViewModel Field | UI That Shows It | Who Should Update It | What Actually Updates It |
|--|----------------|-----------------|---------------------|--------------------------|
| `[ ]` | `_loraTrainingProgress` | `TrainScreen.kt` (training progress bar) | `LoraTrainer.kt` via `reportLoraTrainingProgress()` | **Nothing.** The function exists in the ViewModel but is never called by LoraTrainer. Progress bar always stays empty during training. |
| `[ ]` | `reportLoraTrainingProgress()` | — | Should be called by `LoraTrainer` | **Never invoked.** Defined but disconnected. |
| `[ ]` | `clearLoraTrainingProgress()` | — | Should be called post-training | **Never invoked.** |
| `[ ]` | `learningState.adamStep` | `DashboardScreen`, `ModulesScreen` | `LoraTrainer` via `AgentEventBus` | **Nothing.** The Adam optimizer runs natively but never reports its step count back through the event bus. Always displays `0`. |
| `[ ]` | `learningState.lastPolicyLoss` | `DashboardScreen`, `ModulesScreen` | `LoraTrainer` via `AgentEventBus` | **Nothing.** Same problem — loss value never published. Always displays `0.0`. |
| `[ ]` | `_thermalState` | `DashboardScreen` (warning banners) | `ThermalGuard.kt` via `AgentEventBus` event `"thermal_status_changed"` | **Nothing.** `ThermalGuard.kt` exists but never emits this event to the bus. Thermal warnings in the UI can never fire from real device data. |
| `[ ]` | `ModuleUiState.tokensPerSecond` | `ModulesScreen.kt` | `refreshModuleState()` | **Never set.** Initialized to `0.0` and `refreshModuleState` never populates it. The "last known speed" field always shows zero. |

---

## 4. Agent Loop Failure Modes

Specific ways the Observe → Think → Act cycle breaks, loops forever, or fails silently with no user feedback.

### Silent Failures

| | Location | What Happens | Why It's a Problem |
|--|----------|-------------|-------------------|
| `[x]` | `GestureEngine.kt` `onCancelled` callback | All 5 callbacks (tap, swipe, longPress, tapXY, swipeXY) now log a `Log.w` with the cancelled node/coordinates so failures are visible in logcat. **Fixed.** | Agent can't distinguish "gesture worked but missed the target" from "OS blocked the gesture entirely." Action history is misleading. |
| `[ ]` | `GestureEngine.kt` `executeFromJson` | Malformed or unknown JSON from LLM → `executeFromJson` returns `false`, recorded as "failure," loop continues. | If the LLM consistently produces bad JSON, the agent silently fails every single step until it hits the 50-step limit, with no UI explanation of why. |
| `[ ]` | `AgentAccessibilityService.kt` → `instance` is `null` | When the OS kills the Accessibility Service (common on low-RAM devices like the M31), `getSemanticTree()` returns the string `"(accessibility service not active)"`. The loop is **not aborted** — it continues, feeds this string to the LLM, and tries to act on it. | Agent runs until `MAX_STEPS` burning battery and producing garbage. No hard-stop or user alert when the service dies mid-task. |

### Infinite / Near-Infinite Loop Risks

| | Location | What Happens | Why It's a Problem |
|--|----------|-------------|-------------------|
| `[ ]` | `AgentLoop.kt` — `Wait` tool handling | The LLM outputting `{"tool":"Wait"}` **resets `stuckCount` to 0** (lines 400, 432). | An LLM that hallucinates "Wait" repeatedly will never trigger the stuck-detection abort and will never hit `MAX_STEPS` quickly. The agent loops indefinitely. |
| `[ ]` | `AgentLoop.kt` — screen hash comparison | Stuck detection fires only if the screen hash is **identical**. Any minor UI change (blinking cursor, loading spinner, clock updating) changes the hash and resets `stuckCount`. | Agent can waste all 50 `MAX_STEPS` on effectively the same screen without ever being flagged as stuck. |
| `[ ]` | `AgentLoop.kt` — task chaining (`recordAndChain`) | Automatically starts the next task in `TaskQueueManager` after completion. | If tasks keep being added to the queue (by user or by a buggy script), the agent runs indefinitely with no ceiling. |
| `[ ]` | `AgentLoop.kt` — dead A11y node loop | `GestureEngine.tap` returns `false` if `getNodeById` fails. This is recorded as a failure and the loop continues. | If the LLM keeps targeting a node that no longer exists but is still in its context window, it loops until `MAX_STEPS` with every step silently failing. |

---

## 5. UI Screens With Known Holes

All 11 navigation routes exist. No missing screen files. But several screens have confirmed incomplete sections.

| | Screen | Gap | Severity |
|--|--------|-----|---------|
| `[ ]` | `ControlScreen.kt` | File header describes it as a **"Phase 4 gap-fill over an existing stub."** Real control wiring completeness is unverified. | Medium |
| `[ ]` | `SettingsScreen.kt` line 66 | `TODO (Phase 10)`: **Web Dashboard / Local Monitoring Server section** is entirely absent from the UI. The backend exists but there is no settings UI to configure or open it. | Medium |
| `[ ]` | `ModulesScreen.kt` | Three feature blocks are UI placeholders with no backend: **App Skills** (Phase 15), **Vision Model readiness** (Phase 17), **SAM2/MobileSAM pixel segmentation** (Phase 18). | Low–Medium |
| `[ ]` | `GoalsScreen.kt` — Triggers tab | Explicitly a **placeholder** (line 47). Displays hardcoded "Coming soon" text (line 533). No backing logic for time-based, event-based, or app-based triggers. | Low |
| `[ ]` | `ChatScreen.kt` — Preset Prompt Chips | Hardcoded strings (lines 386–392), not pulled from the agent's memory or a dynamic source. Fine for now but becomes stale as the agent evolves. | Low |
| `[ ]` | `SafetyScreen.kt` — `SENSITIVE_APP_PRESETS` | Hardcoded package names (e.g., `com.chase.sig.android`) for convenience blocklists. Not maintained or synced with any real source. | Low |
| `[ ]` | `GoalsScreen.kt` — `GOAL_TEMPLATES` | Hardcoded list of preset tasks (lines 50–63). Not driven by anything dynamic. | Low |

---

## 6. System-Level Gaps

| | File | Gap | Risk |
|--|------|-----|------|
| `[x]` | `AgentAccessibilityService.kt` | `onInterrupt()` now sets `isActive = false` and clears `instance`. **Fixed.** | Medium — may leave the agent in a bad state after interruption |
| `[x]` | `core/ocr/ObjectLabelStore.kt` | `onUpgrade()` now drops and recreates the table. **Fixed.** | High — data corruption on update |
| `[x]` | `core/memory/ExperienceStore.kt` | `onUpgrade()` now drops both tables and recreates. **Fixed.** | High — data corruption on update |
| `[ ]` | `AgentLoop.kt` Phase 14–19 markers | Stuck Detection, Task Plan Decomposition, Sustained Performance Mode, and Vision integration are all partially stubbed with phase markers but not fully integrated into the live loop. | High — agent is incomplete by design |
| `[ ]` | `ThermalGuard.kt` | Guard logic exists but is **not hooked into `AgentLoop`** as a hard stop (Phase 14 target per `ComposeMainActivity`). The agent can overheat the device mid-task. | High — device safety |

---

## 7. Connection Gaps (Things That Should Talk but Don't)

| | Gap | Impact |
|--|-----|--------|
| `[ ]` | `LlamaEngine` ↔ `llama.cpp` JNI | JNI functions are declared and C++ implementations exist and match. But the native library (`libllama-jni.so`) is **not compiled** without an explicit NDK build step. Without the `.so`, all inference stubs out. | Entire AI pipeline non-functional |
| `[ ]` | Vision C++ code ↔ Kotlin side | Complete C++ multimodal inference code exists in `llama_jni.cpp`. But `LlamaEngine.kt` has **no public methods** to call it, and no UI feature uses it. It is dead code. | Vision feature entirely inaccessible |
| `[ ]` | `LoraTrainer` ↔ `ExperienceStore` | Training should pull from stored experiences. The stub bypasses the store entirely — experiences accumulate but are never used for learning. | Learning loop is an illusion |
| `[ ]` | `LoraTrainer` ↔ `AgentViewModel` | Training progress events are never published through `AgentEventBus`. The ViewModel has the state; the UI has the widget; the engine never sends the signal. | Training progress bar always blank |
| `[x]` | `ThermalGuard` ↔ `AgentEventBus` | `ThermalGuard.updateLevel()` now emits `"thermal_status_changed"` with `level`, `inferenceSafe`, `trainingSafe`, `emergency`. **Fixed.** | Thermal warnings never appear in UI |
| `[ ]` | `AgentLoop` ↔ `TaskDecomposer` | `TaskDecomposer.kt` exists and is wired, but Phase 14 markers in `AgentLoop` show the decomposed sub-task execution loop is not yet integrated. | Agent cannot break complex goals into steps |
| `[x]` | `LocalDeviceServer` ↔ `aria-dashboard` frontend | Dashboard now has a **Live tab** that polls all 8 endpoints every 2 seconds. Device IP/port are configurable and persisted to localStorage. **Fixed.** | Dashboard shows fake data |

---

## 8. Web Dashboard Not Connected to Its Own Backend

This is a self-contained gap worth calling out specifically.

**What exists:**
- `LocalDeviceServer.kt` — a fully implemented lightweight HTTP server running on the Android device at port `8765`, with these live endpoints:
  - `GET /aria/status` — agent operational state
  - `GET /aria/thermal` — device thermal levels
  - `GET /aria/rl` — RL metrics (Adam steps, policy loss)
  - `GET /aria/lora` — LoRA adapter history
  - `GET /aria/memory` — embedding store stats
  - `GET /aria/activity` — recent action logs
  - `GET /aria/modules` — per-module readiness
  - `GET /health` — health check
- `LocalSnapshotStore.kt` — feeds live data to the server from `AgentViewModel`
- CORS headers set to `*` so a browser can call it

**What's missing:**
| | Item | Status |
|--|------|--------|
| `[x]` | `artifacts/aria-dashboard/src/pages/Dashboard.tsx` | New **Live tab** added. Polls all 8 endpoints every 2 seconds. Shows status, thermal, RL metrics, modules, and activity log. **Fixed.** |
| `[x]` | No environment config for device IP/port | Live tab has an IP/port input field that persists to `localStorage`. **Fixed.** |
| `[ ]` | No entry point in `SettingsScreen` to start/view the server | User cannot start or connect to the monitoring server from within the app. Still open. |

---

## 9. `ARIA_REALITY_CHECK.md` — Confirmed but Untracked Gaps

The project's own honesty doc admits these. They are tracked here so they don't get lost.

| | Gap | Where Admitted |
|--|-----|---------------|
| `[ ]` | LLM inference only works if NDK build + 870 MB model download are both complete. Default behavior is stub mode. | `ARIA_REALITY_CHECK.md` |
| `[ ]` | Accessibility Service + MediaProjection require **manual user permission steps** before anything works. High friction, no in-app guidance. | `ARIA_REALITY_CHECK.md` |
| `[ ]` | "8–15 tok/s" and "1700 MB RSS" are **hardcoded estimates** in the C++ code, not live measurements from this build. | `ARIA_REALITY_CHECK.md` |
| `[ ]` | Policy Network starts with **random weights**. First N agent episodes are essentially random. | `ARIA_REALITY_CHECK.md` |
| `[ ]` | `EfficientDet-Lite0` cannot detect Android UI elements. The object detector is practically decorative for the agent's core use case. | `ARIA_REALITY_CHECK.md` |
| `[ ]` | IRL video training falls back to word-Jaccard heuristic without LLM — loses all coordinates, only guesses action type. | `ARIA_REALITY_CHECK.md` |
| `[ ]` | Phases 5 (`ChatScreen`), 6 (`TrainScreen`), 7 (`LabelerScreen`) are marked `[~] WRITTEN` but **not verified** on emulator or device. | `migration.md` |
| `[ ]` | `migration.md` Phase headers mark some items `✅ DONE` while the Reality Check table for the same items says `[~] written — needs emulator verify`. The two documents contradict each other. | `migration.md` vs `ARIA_REALITY_CHECK.md` |

---

## 10. Legacy / Migration Debt

| | Item | Notes |
|--|------|-------|
| `[ ]` | `artifacts/mobile/` (React Native screens) | Still present as "specs." Not running, but adds noise and can confuse contributors. Remove once all Kotlin counterparts are verified. | 
| `[ ]` | Phases 8 and 9 | **Not started.** Phase 8 = delete all `.tsx` files. Phase 9 = strip Expo/RN from the build system entirely. Gate-check prerequisites not yet met. |
| `[ ]` | `migration.md` phase tracking | Phases 1–19 tracked in a flat doc with no automated gate — easy to mark complete prematurely (and it has already happened). |
| `[ ]` | `ActivityScreen.kt` line 42 comment | References `logs.tsx` (legacy RN file) as "should not be deleted until this screen is verified." Formal verification has never been signed off. |
| `[ ]` | `ModulesScreen.kt` in migration doc | Listed as `[~] written` in the Reality Check table but has no dedicated Phase entry for "filling its gaps," unlike every other screen. Falls through the cracks. |

---

## 11. Manifest & Build Gaps

| | Item | Gap |
|--|------|-----|
| `[ ]` | `WAKE_LOCK` permission | Declared in `AndroidManifest.xml` (line 20) "to keep CPU awake during model download and training." However, **no Kotlin code acquires a WakeLock via `PowerManager`**. The permission declaration is orphaned. |
| `[ ]` | ONNX Runtime via reflection | `Sam2Engine.kt` and `EmbeddingEngine.kt` use `Class.forName("ai.onnxruntime.OrtEnvironment")` to access ONNX Runtime at runtime instead of importing it directly. If the library version changes or ProGuard strips it, this fails **silently at runtime** with a `ClassNotFoundException`, not a compile error. |
| `[ ]` | No NDK build step in CI | The CMakeLists.txt and JNI bridge are correct, but there is no script or CI job that actually triggers `./gradlew assembleDebug` with NDK. The `.so` is never built automatically. |

---

## 12. Missing Infrastructure

| | Item | Why It Matters |
|--|------|---------------|
| `[ ]` | No CI / automated build pipeline | Stub code can ship undetected. NDK is never compiled. Phase gates are never enforced. |
| `[ ]` | No unit or integration tests | `OcrEngine.kt` mentions a "ML Kit stub for unit tests" but no test files exist. No way to catch regressions. |
| `[ ]` | No crash / error reporting | Agent runs as a foreground service. Silent failures (stub returns, empty `onUpgrade`, swallowed error codes, empty `onCancelled`) have zero observability in production. |
| `[ ]` | No device IP discovery for dashboard | `LocalDeviceServer` runs on the phone, but the web dashboard has no mechanism to find out what IP address or port to connect to. Needs a config screen or mDNS discovery. |
| `[ ]` | No in-app guide for required permissions | Accessibility Service and MediaProjection require manual setup in Android settings. There is no in-app walkthrough that confirms the user did it correctly before the agent starts trying to act. |

---

## Priority Order (Recommended)

Items are ranked by impact and safety risk.

| # | Item | Status | Why First |
|---|------|--------|-----------|
| 1 | **Build the NDK / JNI library** | `[ ]` | Nothing real works until `libllama-jni.so` compiles. All AI gaps depend on this. |
| 2 | **Fix both `onUpgrade()` stubs** (`ObjectLabelStore`, `ExperienceStore`) | `[x]` **Done** | Silent data corruption on every app update. |
| 3 | **Hook `ThermalGuard` into `AgentLoop`** as hard abort | `[x]` **Done** | AgentLoop already had the 10s pause + break check; EventBus emission from ThermalGuard was the missing piece (fixed in session 2). |
| 4 | **Add hard-abort when Accessibility Service is null** | `[x]` **Done** | Guard added at top of every step iteration in AgentLoop: checks `AgentAccessibilityService.isActive`, calls `recordAndChain` and breaks immediately if dead. |
| 5 | **Fix empty `onCancelled` in GestureEngine** | `[x]` **Done** | All 5 gesture callbacks now log OS cancellations. |
| 6 | **Wire `ThermalGuard` → `AgentEventBus`** | `[x]` **Done** | Thermal warnings in the UI now fire from real data. |
| 7 | **Wire `LoraTrainer` → `reportLoraTrainingProgress()`** | `[x]` **Done** | `runRlCycle()` in AgentViewModel now emits progress at 10% (loading_data), 25% (training), and 100%/0% (complete/failed). Error path also emits "failed". |
| 8 | **Wire `Dashboard.tsx` to `LocalDeviceServer`** | `[x]` **Done** | Live tab added with real polling, IP config, and all 8 endpoints wired. |
| 9 | **Wire `LoraTrainer` → `ExperienceStore`** | `[ ]` | Learning loop accumulates data that is never used for training. |
| 10 | **Expose vision C++ code to Kotlin** | `[ ]` | Vision inference C++ exists and matches JNI, but is completely inaccessible from Kotlin. |
| 11 | **Fix `onInterrupt()` in `AgentAccessibilityService`** | `[x]` **Done** | Service now clears state properly when interrupted. |
| 12 | **Fix `WAKE_LOCK` orphan** | `[ ]` | Either acquire it in code or remove the permission declaration. |
| 13 | **Replace ONNX reflection calls with direct imports** | `[ ]` | Silent `ClassNotFoundException` risk on version changes or ProGuard. |
| 14 | **Verify Phases 5, 6, 7 on emulator** | `[ ]` | Three screens are "written but not verified." Gate Phase 8 on this. |
| 15 | **Reconcile `migration.md` vs `ARIA_REALITY_CHECK.md`** | `[ ]` | The two documents contradict each other on what is "done." |
| 16 | **Complete `AgentLoop` Phase 14 integration** | `[ ]` | Stuck detection + task decomposition + sustained performance mode. |
| 17 | **Add Web Dashboard entry point in SettingsScreen** | `[ ]` | Phase 10 TODO — the monitoring UI section is completely absent. |
| 18 | **Build Triggers feature in GoalsScreen** | `[ ]` | Tab is a placeholder with "coming soon" text and no backend. |
| 19 | **Phase 8 cleanup** — delete `MainActivity`, `ExpoModulesPackageList`, `artifacts/mobile/` | `[ ]` | Remove dead weight once verification gates pass. |
| 20 | **Add CI, NDK build step, and test gates** | `[ ]` | Prevent gaps from reopening silently. |
