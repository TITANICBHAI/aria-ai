# ARIA Project — Gap Audit
> Last updated: April 05, 2026
> Status key: `[ ]` Open · `[~]` In Progress · `[x]` Done

---

## 1. Stub Files That Should Not Exist (or Be Replaced)

| # | File | Problem | Phase Target |
|---|------|---------|-------------|
| `[ ]` | `android/app/src/main/kotlin/com/ariaagent/mobile/MainActivity.kt` | No-op migration stub. Still listed in `AndroidManifest.xml` but is not the real launcher. Will silently confuse anyone reading the manifest. | Phase 8 |
| `[ ]` | `android/app/src/main/kotlin/expo/modules/ExpoModulesPackageList.kt` | Kept only to satisfy package declarations from the old RN layer. Purely dead weight. | Phase 8 |

**What to do:** Delete both after confirming Phase 8 checklist is clear and nothing still imports them.

---

## 2. Unimplemented Core Features (Stubs Masquerading as Real Logic)

These are the most critical gaps — the engine is wired up but fires blanks.

| # | File | What it claims to do | What it actually does |
|---|------|---------------------|-----------------------|
| `[ ]` | `core/ai/LlamaEngine.kt` | Run on-device LLM inference | Returns a hardcoded JSON string: `{"tool":"Click","node_id":"#1","reason":"stub inference — llama.cpp not compiled"}` |
| `[ ]` | `core/ai/LlamaEngine.kt` → `loadVision()` | Load SmolVLM vision model | Returns a sentinel handle + pre-canned descriptions. No real model loaded. |
| `[ ]` | `core/rl/LoraTrainer.kt` | Fine-tune model on-device via LoRA | Calls `stubTrainLora()` — writes a metadata-only `.bin` file. No weights updated. |
| `[ ]` | `core/ai/InferenceEngineImpl.kt` | Full inference pipeline with error handling | Has TODOs at lines 168 and 189 for better error code handling — currently swallowed silently. |

**Why this matters:** The agent loop runs, the UI shows activity, but no real intelligence is happening. A user or developer would not know this unless they read deeply.

---

## 3. UI Screens With Known Holes

All 11 navigation routes exist as files — no missing screens. But several are partial:

| # | Screen | Gap |
|---|--------|-----|
| `[ ]` | `ControlScreen.kt` | Described in its own header as a "Phase 4 gap-fill over an existing stub." Real control wiring may be incomplete. |
| `[ ]` | `SettingsScreen.kt` | Large TODO at line 66: **"Phase 10 — Web Dashboard / Local Monitoring Server section"** is entirely missing from the UI. The section exists in the migration plan but there is no UI for it. |
| `[ ]` | `ModulesScreen.kt` | Three placeholders for future capability that have no UI or backend yet: App Skills (Phase 15), Vision Model readiness (Phase 17), SAM2/MobileSAM pixel segmentation (Phase 18). |

---

## 4. System-Level Gaps

| # | File | Gap |
|---|------|-----|
| `[ ]` | `system/accessibility/AgentAccessibilityService.kt` | `onInterrupt()` is empty. This is called by Android when the accessibility service is interrupted — doing nothing here means the agent doesn't clean up or re-arm itself. |
| `[ ]` | `core/ocr/ObjectLabelStore.kt` | `onUpgrade()` database method is a stub — schema migrations will silently fail on app updates. |
| `[ ]` | `core/memory/ExperienceStore.kt` | Same issue — `onUpgrade()` is empty, meaning any schema change wipes or corrupts experience memory without warning. |
| `[ ]` | `system/screen/ScreenCaptureService.kt` (implied) | `AgentLoop.kt` has Phase 14-19 markers for Stuck Detection and Task Plan Decomposition — the perception → action feedback loop is incomplete. |

---

## 5. Connection Gaps (Things That Should Talk to Each Other But Don't)

| # | Gap | Impact |
|---|-----|--------|
| `[ ]` | `LlamaEngine` ↔ `llama.cpp` JNI bridge is **not compiled or linked** | The entire inference pipeline is disconnected from the native library. The `.so` file (`libllama-jni.so`) must be built via NDK before any real model can run. |
| `[ ]` | `LoraTrainer` ↔ `ExperienceStore` | Training should pull from stored experiences, but the stub bypasses this — experiences accumulate but are never used for learning. |
| `[ ]` | `AgentLoop` ↔ `TaskDecomposer` | `TaskDecomposer.kt` exists and is wired, but the Phase 14 markers in `AgentLoop` show the decomposed sub-task execution loop is not yet integrated. |
| `[ ]` | `ThermalGuard` ↔ `AgentLoop` | Thermal throttling is referenced in `ComposeMainActivity.kt` as a Phase 14 integration — the guard exists but is not hooked into the run loop, so the agent can overheat the device. |
| `[ ]` | `SettingsScreen` ↔ Web Dashboard (Phase 10) | A local monitoring server is planned but has no backend service, no port binding, and no UI entry point. |

---

## 6. Legacy / Migration Debt

| # | Item | Notes |
|---|------|-------|
| `[ ]` | `artifacts/mobile/` (React Native screens) | Still present in the repo as "specs." They are not running, but they add noise and could confuse contributors. Should be removed once all Kotlin counterparts are verified complete. |
| `[ ]` | `migration.md` Phase tracking | Phases 1–19 are tracked in a flat doc but there is no automated check to confirm a phase is truly done (e.g., no CI step, no test gate). Easy to mark complete prematurely. |
| `[ ]` | `ActivityScreen.kt` line 42 comment | References `logs.tsx` (legacy RN file) as something that "should not be deleted until this screen is verified." This verification has not been formally signed off. |

---

## 7. Missing Infrastructure

| # | Item | Why It Matters |
|---|------|---------------|
| `[ ]` | No CI / build pipeline | No automated checks mean stub code can ship undetected. No NDK build step means `libllama-jni.so` is never compiled. |
| `[ ]` | No unit or integration tests | `OcrEngine.kt` mentions running without ML Kit for unit tests, but no test files exist in the explored structure. |
| `[ ]` | No crash/error reporting hook | Agent runs as a foreground service — silent failures (stub returns, empty `onUpgrade`, swallowed error codes) have no observability. |

---

## Priority Order (Recommended)

1. **Build the JNI bridge** — nothing real works until `libllama-jni.so` compiles. All other AI gaps depend on this.
2. **Fix `onUpgrade()` stubs** — data corruption risk on every app update.
3. **Hook `ThermalGuard` into `AgentLoop`** — device safety concern.
4. **Wire `LoraTrainer` → `ExperienceStore`** — without this, the learning loop is an illusion.
5. **Complete `AgentLoop` Phase 14 integration** — stuck detection + task decomposition.
6. **Clean up Phase 8 stubs** (`MainActivity`, `ExpoModulesPackageList`).
7. **Add monitoring/Web Dashboard** (Phase 10 SettingsScreen gap).
8. **Remove legacy RN artifacts** once Kotlin counterparts are verified.
9. **Add CI and test gates** to prevent gaps from reopening.
