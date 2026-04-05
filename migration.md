# ARIA — Full Native Migration Plan
**From:** Expo + React Native + Kotlin/Compose hybrid  
**To:** Pure Kotlin + Jetpack Compose + NDK (no JS, no React Native, no Expo)

---

> # ⛔ STOP — READ THIS BEFORE TOUCHING A SINGLE FILE ⛔
>
> ## THE ONLY PERMITTED ORDER IS: MIGRATE → VERIFY → DELETE
>
> **NEVER delete a React Native / JS file until its Kotlin/Compose replacement:**  
> **1. Is fully written**  
> **2. Has been compiled successfully**  
> **3. Has been verified on the emulator screen-by-screen**  
>
> ### Why this order is mandatory and non-negotiable:
>
> Every `.tsx` and `.ts` file in the React Native layer is the **living specification**
> for what the Compose replacement must contain. Buttons, inputs, sections, edge cases,
> API calls, state — all of it is documented by the RN code itself.
>
> **If you delete the RN file before the Compose screen is finished:**
> - The specification is gone
> - There is no way to know what was missed
> - Features will silently disappear with no error, no warning, no way to recover
> - The only record left is git history, which takes extra work to read
>
> ### The danger scenario this plan is designed to prevent:
>
> > Agent A deletes all JS files thinking "we don't need these anymore."  
> > Agent B is assigned to build TrainScreen.kt.  
> > Agent B has no reference. Guesses. Misses IRL video training, misses the RL cycle
> > result display, misses the auto-schedule toggle.  
> > Nobody notices until a user tries to train the model and the button doesn't exist.
>
> **This is not a hypothetical. This is exactly what happens.**
>
> ---
>
> ### The rule, stated as simply as possible:
>
> ```
> IF the Compose screen is not done → the RN file stays. Full stop.
> IF the Compose screen is done and verified → only then may the RN file be deleted.
> ```
>
> Deletion is Phase 8. It comes after Phases 1–7 are all green.  
> There is no shortcut. There is no exception.

---

## Status Legend
- `[ ]` Not started
- `[~]` In progress
- `[x]` Done — compiled + verified on emulator
- `[!]` Blocked / needs decision
- `[🔒]` Locked — do not touch until the status column for its replacement shows `[x]`

---

## The Golden Sequence (read this every morning before you work)

```
Step 1:  Read the RN file top-to-bottom. List every feature.
Step 2:  Build the Compose equivalent. Match every feature. Add more if possible.
Step 3:  Compile. Fix errors.
Step 4:  Run on emulator. Tap every button. Verify every section renders.
Step 5:  Mark the Compose file [x] in the table below.
Step 6:  Only now: delete the RN file and mark it deleted in the table.
Step 7:  Move to the next screen. Repeat.
```

**If you are tempted to skip to Step 6 — don't. The RN file is your spec. Once it is gone, you are coding blind.**

---

## Reality Check

The Compose layer is already 70% built. This is not a rewrite — it is a gap fill + cleanup.

| What exists in Kotlin/Compose already | Lines | Status |
|---|---|---|
| DashboardScreen.kt | 476 | `[x]` exists |
| ControlScreen.kt | 469 | `[~]` exists, has gaps |
| ModulesScreen.kt | 456 | `[~]` exists, has gaps |
| ActivityScreen.kt | 225 | `[~]` exists, has gaps |
| SettingsScreen.kt | 265 | `[~]` exists, has gaps |
| ARIAComposeApp.kt (nav shell) | 186 | `[~]` updated — 7 tabs + labeler route added |
| ComposeMainActivity.kt | 54 | `[x]` exists |
| AgentViewModel.kt | 1095 | `[~]` updated — Chat/Train/Labeler methods added |
| ARIATheme.kt | 139 | `[x]` exists |
| ChatScreen.kt | 491 | `[~]` written — needs emulator verify |
| TrainScreen.kt | 545 | `[~]` written — needs emulator verify |
| LabelerScreen.kt | 641 | `[~]` written — needs emulator verify |

| React Native files — their job right now | Status |
|---|---|
| All `.tsx` / `.ts` files | `[🔒]` Living spec — do not delete until replacement is `[x]` |

---

## Labour Cost Analysis
*Reference analysis for scoping and planning. Updated to reflect current state.*

The most complex logic — AI inference, OCR, screen capture, accessibility, RL — is already
in Kotlin. The cost is almost entirely in the UI layer and wiring.

### 1. UI Reconstruction — High effort
Over 20 screens and components written in TypeScript/React Native (DashboardScreen.tsx,
ControlScreen.tsx, MetricCard.tsx, etc.) need Jetpack Compose equivalents.

**Current state:** 5 of 8 screens already exist in Compose. 3 must be created from scratch
(Chat, Train, Labeler). 4 existing screens have feature gaps to fill.

React hooks → Compose State, navigation → NavHost, FlatList → LazyColumn:
all require manual recreation of the visual layer but the logic is already defined.

### 2. Eliminating the Bridge — Medium effort
`AgentCoreBridge.ts` ↔ `AgentCoreModule.kt` is the JS↔Kotlin communication layer.
In a pure native app, this bridge is deleted entirely.

**Benefit:** No more data serialisation between languages. Compose screens call
`AgentViewModel` which calls Kotlin services directly. The code actually gets simpler.

**Current state:** Bridge is still compiling but no longer the launcher. Deletion is Phase 8,
after all screens are verified.

### 3. Preserving the Core — Low effort (already done)
The heavy lifting is already in Kotlin or C++. These are untouched during migration:

| Component | File(s) | Status |
|---|---|---|
| LLM inference | `LlamaEngine.kt`, `llama_jni.cpp` | Keep as-is |
| Model management | `ModelManager.kt` | Keep as-is |
| Accessibility | `AgentAccessibilityService.kt` | Keep as-is |
| Screen capture | `ScreenCaptureService.kt` | Keep as-is |
| Reinforcement learning | `LoraTrainer.kt`, `PolicyNetwork.kt` | Keep as-is |
| OCR | `OcrEngine.kt` | Keep as-is |
| Object detection | `ObjectDetectorEngine.kt` | Keep as-is |

### 4. Architecture Migration — Medium effort (mostly done)
State management currently split between `AgentContext.tsx` (JS) and `AgentViewModel.kt` (Kotlin).

After migration, `AgentViewModel` is the **single source of truth** for all UI state.
It already subscribes to `AgentEventBus` — no polling, no bridge events.

**Tools considered:**
- **Hilt** (dependency injection) — optional; current singleton pattern works for this app's scale
- **Room** (structured DB) — optional; `ExperienceStore.kt` uses SQLite directly and works well

### Labour Cost Summary

| Component | Current Status | Action Required | Effort |
|---|---|---|---|
| UI / Screens | 5 of 8 in Compose | Fill 4 gaps + create 3 new screens | High |
| Bridge removal | Bridge exists, not launcher | Delete in Phase 8 after screens verified | Medium |
| Native core (AI, OCR, RL, Services) | 100% Kotlin/C++ | No changes — plug and play | Low |
| Build system | Expo/Metro still in gradle | Strip in Phase 9 after Phase 8 | Medium |
| State management | AgentViewModel 80% complete | Add 3 new screen methods | Low |

### The Verdict
Since the most complex logic (AI, OCR, RL, System Services) is already in Kotlin,
the head start is massive. The remaining cost is almost entirely UI.

Solo developer estimate: **2–3 focused weeks** for UI reconstruction and wiring.
Performance gains for an inference-heavy app like ARIA make this unambiguously worthwhile.

---

## Phase 0 — Environment Setup
*Before touching any code.*

> **Firebase Studio / Android Studio import guide:** `artifacts/mobile/FIREBASE_STUDIO.md`  
> The build system is dual-mode — it auto-detects whether `node_modules` is present.  
> Firebase Studio (no node_modules) → native-only mode. Everything syncs and compiles.  
> pnpm / EAS (node_modules present) → hybrid mode. RN bridge + Expo modules included.

- [x] **Firebase Studio setup** — `settings.gradle`, `build.gradle`, and `app/build.gradle` updated for dual-mode (native-only vs hybrid). `local.properties` and `local.properties.template` created. `.gitignore` updated.
- [ ] Open `artifacts/mobile/android/` in Firebase Studio / Android Studio
- [ ] Copy `local.properties.template` → `local.properties`, set correct `sdk.dir`
- [ ] Gradle sync — expect "NATIVE-ONLY mode" in Build output if no `node_modules`
- [ ] Create branch: `migration/full-native`
- [ ] Confirm emulator boots (API 34, arm64-v8a or x86_64)
- [ ] Run `./gradlew assembleDebug` from `artifacts/mobile/android/` — confirm it compiles
- [ ] Launch `ComposeMainActivity` via adb:
  ```bash
  adb shell am start -n com.ariaagent.mobile/.ui.ComposeMainActivity
  ```
- [ ] Open each existing Compose screen, note what renders vs. what is blank
- [ ] **Do not delete or modify any file in this phase**

---

## Phase 1 — Promote Compose as Launcher ✅ DONE
*Est: 2–3 hours. Zero deletions. Only AndroidManifest + MainApplication change.*

> **No RN files are deleted in this phase.**  
> The RN layer still compiles and exists as reference. Only the launch entry point changes.

### 1.1 AndroidManifest.xml
- [x] Move `android.intent.action.MAIN` + `android.intent.category.LAUNCHER` from `MainActivity` to `ComposeMainActivity`
- [x] Remove the launcher intent filter from `MainActivity` (the activity tag stays for now)
- [x] Keep `ComposeMainActivity` theme as `@style/Theme.ARIAAgent`

### 1.2 MainApplication.kt
- [x] Remove `ReactApplication` interface
- [x] Remove `reactNativeHost` + `reactHost` overrides
- [x] Remove all Expo/RN imports
- [x] Keep: `SoLoader.init(this, OpenSourceMergedSoMapping)` — NDK still needs it
- [x] Result: ~20-line plain `Application` subclass

### 1.3 MainActivity.kt
- [x] Do NOT delete yet — it is still referenced in the manifest
- [x] Strip out the React Activity body, leave a no-op stub so it compiles
- [x] Deletion happens in Phase 8

### Checkpoint
> **Phase 1 complete.** `ComposeMainActivity` is now the launcher.  
> `MainApplication` is RN-free. `MainActivity` is a no-op stub.  
> `./gradlew assembleDebug` should pass. No RN file has been deleted.

---

## Phase 2 — Fill Settings Screen Gaps ✅ DONE
*Est: 4–6 hours.*

> **Reference file (DO NOT DELETE): `app/(tabs)/settings.tsx` — 852 lines**  
> Settings.tsx remains until Phase 8 gate check.

All features matched in the new `SettingsScreen.kt`:

- [x] Model path — editable `OutlinedTextField` (was read-only, now matches RN)
- [x] Quantization — chip selector (Q4_K_M / Q4_0 / IQ2_S / Q5_K_M)
- [x] Context window — chip selector (512 / 1024 / 2048 / 4096)
- [x] GPU layers — chip selector (0=CPU / 8 / 16 / 24 / 32)
- [x] Temperature — preset buttons (0.1 / 0.3 / 0.5 / 0.7 / 0.9) with live display
- [x] RL enabled — toggle switch
- [x] LoRA adapter path — editable `OutlinedTextField` (was read-only, now matches RN)
- [x] Save Settings button with success flash
- [x] **Permissions section** — fully implemented:
  - [x] Accessibility service: granted indicator + "Open Accessibility Settings" button
  - [x] Notifications: granted indicator + "Open Notification Settings" button
  - [x] Screen Capture: granted indicator, ON-DEMAND badge (no button needed)
- [x] **Clear Memory button** — calls `vm.clearMemory()` with confirmation dialog
- [x] **Reset Agent button** — calls `vm.resetAgent()` with confirmation dialog
- [x] System info row (device model, Android API, package, ABI) — new beyond RN

Also added to `AgentViewModel.kt`:
- [x] `MemoryEntry` data class
- [x] `_memoryEntries` StateFlow
- [x] `refreshMemoryEntries()` — loads from ExperienceStore
- [x] `clearMemory()` — clears ExperienceStore + resets state
- [x] `resetAgent()` — full reset: experience + progress + skills + queue

Also added to `ARIATheme.kt`:
- [x] `ARIAColors.SurfaceVariant` alias
- [x] `ARIAColors.Destructive` alias

### Checkpoint
> `SettingsScreen.kt` feature-complete. Verify on emulator before marking `[x]`.  
> `settings.tsx` stays until Phase 8.

---

## Phase 3 — Fill Activity / Logs Screen Gaps ✅ DONE
*Est: 3–4 hours.*

> **Reference file (DO NOT DELETE): `app/(tabs)/logs.tsx` — 317 lines**  
> logs.tsx stays until Phase 8 gate check.

All features matched in the new `ActivityScreen.kt`:

- [x] **Tab bar** — "Actions" | "Memory" with count badges
- [x] Actions tab: `LazyColumn` with action log entries
- [x] Each action row: tool icon, node ID, app name (short), timestamp, reward signal, left-border colour (green/red)
- [x] **Memory tab** — fully implemented:
  - [x] `LazyColumn` of `ExperienceStore` entries (most recent 200)
  - [x] Each row: screen summary, app name, timestamp, confidence %, edge-case badge
  - [x] Violet left border on all memory rows
- [x] Clear memory button — top right, visible only in memory tab with entries, confirmation dialog
- [x] Empty state per tab — different icon + message per tab

Features added beyond RN:
- [x] Live "THINKING…" token stream card during inference (above the list, both tabs)
- [x] Count badge on each tab showing number of entries

### Checkpoint
> `ActivityScreen.kt` feature-complete. Verify on emulator before marking `[x]`.  
> `logs.tsx` stays until Phase 8.

---

## Phase 4 — Fill Control Screen Gaps ✅ DONE
*Est: 3–4 hours.*

> **Reference file (DO NOT DELETE): `app/(tabs)/control.tsx` — 817 lines**  
> control.tsx stays until Phase 8 gate check.

All features matched in the updated `ControlScreen.kt`:

- [x] Readiness indicators: model downloaded, model loaded, accessibility, screen capture
- [x] Goal text field + target app field (disabled while agent running)
- [x] Preset goal chips (tap to fill goal field)
- [x] START / PAUSE / RESUME / STOP buttons
- [x] Task queue list — `QueuedTaskRow` with goal, app (short name), timestamp, priority badge, remove button
- [x] **Learn-only mode toggle** — Switch routes to `vm.startLearnOnly()`, button label becomes "START LEARNING", accent border activates
- [x] **Chained task notification banner** — shows when `chainedTask != null`, dismiss button calls `dismissChainNotification()`
- [x] **LLM Load Gate card** — accent card with CPU icon shown when `!moduleState.modelLoaded`; tap calls `vm.loadModel()`
- [x] **Active task display** — green success box showing `agentState.currentTask` when not blank
- [x] **Separate queue-goal + queue-app text fields** — independent from main goal; cleared after enqueue
- [x] **Teach the Agent / Object Labeler** entry point — nav card at bottom; calls `onNavigateToLabeler()` lambda (wired up in Phase 7)
- [x] Status dot in header matching agent status colour

Also added to `AgentViewModel.kt`:
- [x] `fun loadModel()` — reads stored config, calls `LlamaEngine.load()`, refreshes state
- [x] `fun startLearnOnly(goal, appPackage)` — routes to `AgentForegroundService.startLearnOnly()`

Also updated `ARIACard` in `DashboardScreen.kt`:
- [x] Added optional `modifier: Modifier` and `containerColor: Color` parameters (backward-compatible defaults)

Note: "Game mode stats card" seen in the old plan is NOT in `control.tsx` — there is no game stats card in the RN spec. Dropped.

### Checkpoint
> `ControlScreen.kt` feature-complete. Verify on emulator before marking `[x]`.  
> `control.tsx` stays until Phase 8.

---

## Phase 5 — Build Chat Screen ✅ WRITTEN
*ChatScreen.kt created (491 lines). Needs emulator verification.*

> **Reference file (DO NOT DELETE): `app/(tabs)/chat.tsx` — 601 lines**

- [x] `LazyColumn` message list, auto-scrolls to bottom on new message
- [x] User bubble — right-aligned, primary colour background
- [x] AI bubble — left-aligned, surface colour background, avatar icon
- [x] System message bubble — centred, muted text
- [x] Welcome message on first open
- [x] **Typing indicator** — three animated dots (InfiniteTransition, staggered 200ms delay)
- [x] Context tag bar at top — Agent State / Memory / Task Queue / App Skills chips
- [x] LLM status pill in header (ON/OFF with colour dot)
- [x] Text input bar at bottom:
  - [x] `OutlinedTextField` for message text
  - [x] Send button — disabled when input empty or AI thinking
  - [x] `CircularProgressIndicator` on send button while thinking
  - [x] Clear conversation button (top right, with confirmation dialog)
- [x] Preset prompt chips — horizontal scrollable `LazyRow` (5 presets)
- [x] Inference via `AgentViewModel.sendChatMessage()` → `ChatContextBuilder.build()` → `LlamaEngine.infer()` — no bridge
- [x] **NEW beyond RN:** Token/sec rate shown beneath each AI response
- [x] `ChatScreen` route added to `ARIAComposeApp.kt` (tab 3 of 7)
- [x] Chat ViewModel methods: `sendChatMessage()`, `clearChat()`, `chatMessages`, `chatThinking`

### Checkpoint
> `ChatScreen.kt` written. Emulator verify pending → mark `[x]` then `chat.tsx` eligible for Phase 8 deletion.

---

## Phase 6 — Build Train Screen ✅ WRITTEN
*TrainScreen.kt created (545 lines). Needs emulator verification.*

> **Reference file (DO NOT DELETE): `app/(tabs)/train.tsx` — 692 lines**

**RL Status card:**
- [x] LoRA version, latest adapter path, untrained sample count
- [x] Adam step count, last policy loss value
- [x] Refresh button (top right)
- [x] Last trained timestamp (formatted as "X min ago / X hr ago")

**Run RL Cycle card:**
- [x] Run button — disabled while running, shows `CircularProgressIndicator`
- [x] Result card after completion: samples used, adapter path, LoRA version
- [x] Error display when RL fails

**Video Training (IRL) card:**
- [x] Pick video — `ActivityResultContracts.GetContent("video/*")`
- [x] Selected video name display + clear (×) button
- [x] Goal text field
- [x] Target app package field (optional)
- [x] Run IRL button — disabled until video + goal filled
- [x] Results: frames processed, tuples extracted, LLM-assisted count
- [x] content:// URI → temp file path (`resolveContentUri()` in ViewModel)

**Navigate to Labeler:**
- [x] Full nav card with description, chevron, Label icon

**NEW beyond RN:**
- [x] Auto-schedule RL toggle — triggers cycle when `untrainedSamples > 50`
- [x] `TrainScreen` route added to `ARIAComposeApp.kt` (tab 5 of 7)
- [x] Train ViewModel methods: `runRlCycle()`, `processIrlVideo()`, `setAutoScheduleRl()`, `refreshLearningStatus()`

### Checkpoint
> `TrainScreen.kt` written. Emulator verify pending → mark `[x]` then `train.tsx` eligible for Phase 8 deletion.

---

## Phase 7 — Build Labeler Screen ✅ WRITTEN
*LabelerScreen.kt created (641 lines). Needs emulator verification.*

> **Reference file (DO NOT DELETE): `app/labeler.tsx` — 1,017 lines**

**Capture flow:**
- [x] Capture button → `ScreenCaptureService.captureLatest()` → JPEG saved to cacheDir
- [x] `OcrEngine.run(bitmap)` → OCR text extracted
- [x] `AgentAccessibilityService.getSemanticTree()` → a11y tree extracted
- [x] Image display via `AsyncImage` (Coil 2.7.0) at full width
- [x] `pointerInput detectTapGestures` → tap converts to normalised 0.0–1.0 coords

**Pin overlay:**
- [x] `Box` offset absolutely over image with pin markers (normalised coords × boxSize)
- [x] Each pin: circle + first letter of label name
- [x] Colour coding: selected=cyan, enriched=green, default=violet
- [x] Selected pin: white border ring
- [x] Tap pin to select / deselect
- [x] **NEW:** Long-press pin → immediate delete confirmation
- [x] **NEW:** Drag pin → reposition (`detectDragGestures`, normalised clamp 0–1)

**Header:**
- [x] Back button with unsaved-changes warning dialog
- [x] `app.package.short · N pins` subtitle
- [x] Capture button with `CircularProgressIndicator` while capturing

**Toolbar:**
- [x] Auto-detect → `ObjectDetectorEngine.detectFromPath()` → pins at detected centres
- [x] Enrich All → per-pin `LlamaEngine.infer()` with JSON metadata prompt
- [x] Save → `ObjectLabelStore.saveAll()` → navigates back automatically

**Pin editor panel (visible when pin selected):**
- [x] Name `OutlinedTextField`
- [x] Context `OutlinedTextField`
- [x] Element type `LazyRow` chip row (all 9 types from `ElementType` enum)
- [x] Importance score chip row (1–10, interactive pre-enrichment, read-only after)
- [x] Enriched metadata display (meaning, interactionHint)
- [x] Delete button → confirmation dialog

**Stats bar:**
- [x] Total labels / enriched count
- [x] OCR text preview — collapsed, tap to expand

**Error handling:**
- [x] Error banner auto-dismisses; shown for: capture failure, detect failure, enrich failure

**Wiring:**
- [x] `LabelerScreen` route added to `ARIAComposeApp.kt` (full-screen, no bottom nav)
- [x] `onNavigateToLabeler` lambda wired from `ControlScreen` and `TrainScreen`
- [x] Labeler ViewModel methods in `AgentViewModel.kt`:
  `captureScreenForLabeling()`, `addLabelerPin()`, `updateLabelerLabel()`,
  `deleteLabelerLabel()`, `autoDetectLabelerPins()`, `enrichAllLabelerPins()`,
  `saveLabelerLabels()`, `clearLabelerCapture()`, `dismissLabelerError()`

### Checkpoint
> `LabelerScreen.kt` written. Emulator verify pending → mark `[x]` then `labeler.tsx` eligible for Phase 8 deletion.

---

## Phase 8 — Delete the React Native Layer
*Est: 3–4 hours.*

> # ⛔ GATE CHECK — DO NOT START THIS PHASE UNTIL ALL OF THESE ARE `[x]` ⛔
>
> Before deleting a single file, confirm every item:
>
> - [ ] `SettingsScreen.kt` — verified on emulator `[x]`
> - [ ] `ActivityScreen.kt` — verified on emulator `[x]`
> - [ ] `ControlScreen.kt` — verified on emulator `[x]`
> - [ ] `ChatScreen.kt` — verified on emulator `[x]`
> - [ ] `TrainScreen.kt` — verified on emulator `[x]`
> - [ ] `LabelerScreen.kt` — verified on emulator `[x]`
> - [ ] `DashboardScreen.kt` — verified on emulator `[x]`
> - [ ] `ModulesScreen.kt` — verified on emulator `[x]`
>
> **If any box above is not checked, stop. Go back. Finish the screen. Then come here.**  
> **Partial deletion is not allowed. Either all screens are done, or nothing gets deleted.**

---

Once every box above is `[x]`, delete in this exact order:

### 8.1 Bridge layer (Kotlin — safe to delete, no reference material lost)
- [ ] Delete `bridge/AgentCoreModule.kt`
- [ ] Delete `bridge/AgentCorePackage.kt`
- [ ] Delete `bridge/` folder

### 8.2 JS/TS screens (your spec is now fully implemented — these are redundant)
- [ ] Delete `app/(tabs)/chat.tsx`
- [ ] Delete `app/(tabs)/control.tsx`
- [ ] Delete `app/(tabs)/logs.tsx`
- [ ] Delete `app/(tabs)/modules.tsx`
- [ ] Delete `app/(tabs)/settings.tsx`
- [ ] Delete `app/(tabs)/train.tsx`
- [ ] Delete `app/(tabs)/index.tsx`
- [ ] Delete `app/(tabs)/_layout.tsx`
- [ ] Delete `app/_layout.tsx`
- [ ] Delete `app/labeler.tsx`
- [ ] Delete `app/+not-found.tsx`
- [ ] Delete `app/` folder

### 8.3 JS/TS components + helpers
- [ ] Delete `components/ErrorBoundary.tsx`
- [ ] Delete `components/ErrorFallback.tsx`
- [ ] Delete `components/FloatingChatBubble.tsx`
- [ ] Delete `components/KeyboardAwareScrollViewCompat.tsx`
- [ ] Delete `components/MetricCard.tsx`
- [ ] Delete `components/ModelDownloadScreen.tsx`
- [ ] Delete `components/ModuleRow.tsx`
- [ ] Delete `components/SectionHeader.tsx`
- [ ] Delete `components/StatusDot.tsx`
- [ ] Delete `components/` folder
- [ ] Delete `context/AgentContext.tsx`
- [ ] Delete `context/` folder
- [ ] Delete `hooks/useColors.ts`
- [ ] Delete `hooks/` folder
- [ ] Delete `native-bindings/AgentCoreBridge.ts`
- [ ] Delete `native-bindings/NativeAgentCore.ts`
- [ ] Delete `native-bindings/` folder
- [ ] Delete `constants/colors.ts`
- [ ] Delete `constants/` folder

### 8.4 Entry point + config files
- [ ] Delete `index.js`
- [ ] Delete `metro.config.js`
- [ ] Delete `babel.config.js`
- [ ] Delete `app.json`
- [ ] Delete `eas.json`
- [ ] Delete `expo-env.d.ts`
- [ ] Delete `tsconfig.json`
- [ ] Delete `package.json`
- [ ] Delete `scripts/eas-pre-install.sh`
- [ ] Delete `scripts/ensure-gradle-plugin.js`
- [ ] Delete `scripts/build.js`

### 8.5 Old Kotlin entry point
- [ ] Delete `MainActivity.kt` (replaced by `ComposeMainActivity` in Phase 1)

### Checkpoint
> `./gradlew assembleDebug` passes with zero errors.  
> Zero `ReactNative` or `Expo` references in logcat on launch.  
> All 8 screens still navigable.

---

## Phase 9 — Strip Build System
*Est: 3–4 hours. Do after Phase 8 compiles clean.*

> Only do this after the delete phase is complete. Stripping RN from build.gradle  
> while RN files still exist will break the build with no benefit.

### 9.1 `build.gradle` (app-level) — remove:
- [ ] `apply plugin: "com.facebook.react"` and the entire `react { }` block
- [ ] `implementation("com.facebook.react:react-android")`
- [ ] `implementation("com.facebook.react:hermes-android")`
- [ ] All `implementation project(':async-storage')` lines
- [ ] All `implementation project(':react-native-*')` lines
- [ ] All `implementation project(':expo-*')` lines

Keep: Compose BOM, NDK/cmake, OkHttp, ML Kit, ONNX Runtime, MediaPipe, Coroutines, DataStore, AppCompat, Material

### 9.2 `settings.gradle` — remove:
- [ ] All `include ':async-storage'` lines
- [ ] All `include ':react-native-*'` lines
- [ ] All `include ':expo-*'` lines
- [ ] Keep only `:app`

### 9.3 `gradle.properties` — remove:
- [ ] `reactNativeArchitectures` (if present)
- [ ] Any Hermes / New Architecture flags

### 9.4 `proguard-rules.pro` — remove:
- [ ] All React Native keep rules

### Checkpoint
> `./gradlew assembleRelease` passes clean. Zero RN/Expo references in build output.

---

## Phase 10 — Enhanced Features
*Do after Phases 8–9 are green. These go beyond current RN parity.*

- [ ] **Model download screen in Compose** — full-screen `LinearProgressIndicator` with speed + cancel (currently no Compose version exists)
- [ ] **Thermal banner on all screens** — extend the Dashboard thermal banner globally via `AgentViewModel` state
- [ ] **Live token streaming in Chat** — coroutine `Flow` from `AgentEventBus`, not polling
- [ ] **Floating action button on Dashboard** — quick-launch a task without switching tabs
- [ ] **Haptic feedback** — `HapticFeedbackManager` on all primary action buttons
- [ ] **Bottom nav slide animation** — tab transitions using `AnimatedContent`
- [ ] **Adaptive dark/light theme** — `isSystemInDarkTheme()` in `ARIATheme.kt`

---

## Phase 11 — Release
- [ ] Bump `versionCode` to 2 in `build.gradle`
- [ ] Run `./gradlew assembleRelease`
- [ ] Install APK on real device
- [ ] Verify all 8 screens navigate correctly
- [ ] Verify logcat shows zero `ReactNative` / `Expo` tag lines
- [ ] Tag git commit: `v2.0-native`

---

## Complete File Tracking Table

> **Column guide:**  
> `Compose replacement` — what replaces this file in Kotlin  
> `Replace done [x]` — Compose file is complete and verified  
> `Safe to delete` — only `yes` when replacement column is `[x]`  

| RN File | Lines | Compose Replacement | Replace Done | Safe to Delete |
|---|---|---|---|---|
| `app/(tabs)/index.tsx` | 385 | `DashboardScreen.kt` (exists) | `[x]` | Phase 8 |
| `app/(tabs)/settings.tsx` | 852 | `SettingsScreen.kt` (gap fill Phase 2) | `[ ]` | Phase 8 |
| `app/(tabs)/logs.tsx` | 317 | `ActivityScreen.kt` (gap fill Phase 3) | `[ ]` | Phase 8 |
| `app/(tabs)/control.tsx` | 816 | `ControlScreen.kt` (gap fill Phase 4) | `[ ]` | Phase 8 |
| `app/(tabs)/chat.tsx` | 601 | `ChatScreen.kt` (create Phase 5) | `[ ]` | Phase 8 |
| `app/(tabs)/train.tsx` | 692 | `TrainScreen.kt` (create Phase 6) | `[ ]` | Phase 8 |
| `app/labeler.tsx` | 1017 | `LabelerScreen.kt` (create Phase 7) | `[ ]` | Phase 8 |
| `app/(tabs)/modules.tsx` | 650 | `ModulesScreen.kt` (exists) | `[x]` | Phase 8 |
| `app/_layout.tsx` | 109 | `ARIAComposeApp.kt` (exists) | `[x]` | Phase 8 |
| `context/AgentContext.tsx` | — | `AgentViewModel.kt` (exists) | `[x]` | Phase 8 |
| `native-bindings/AgentCoreBridge.ts` | — | Direct Kotlin calls via ViewModel | `[x]` | Phase 8 |
| All other JS/TS files | — | No replacement needed | `[x]` | Phase 8 |
| `bridge/AgentCoreModule.kt` | 1457 | Not needed — ViewModel calls Kotlin directly | `[x]` | Phase 8 |
| `bridge/AgentCorePackage.kt` | 14 | Not needed | `[x]` | Phase 8 |
| `MainActivity.kt` | 37 | `ComposeMainActivity.kt` (exists) | `[x]` | Phase 8 |

---

## Time Estimate

| Phase | Task | Est. Time |
|---|---|---|
| 0 | Environment + baseline build | 2–3 h |
| 1 | Promote Compose as launcher | 2–3 h |
| 2 | Fill Settings gaps | 4–6 h |
| 3 | Fill Activity/Logs gaps | 3–4 h |
| 4 | Fill Control gaps | 3–4 h |
| 5 | Build Chat screen | 1–2 days |
| 6 | Build Train screen | 1–1.5 days |
| 7 | Build Labeler screen | 2–3 days |
| 8 | Delete RN layer (all at once, all gates green) | 3–4 h |
| 9 | Strip build system | 3–4 h |
| 10 | Enhanced features | 1+ days |
| 11 | Release build + tag | 3–4 h |
| **Total** | | **8–13 days focused** |

---

## Non-Negotiable Rules

1. **MIGRATE FIRST. DELETE LAST.** No RN file is deleted until its Compose replacement is `[x]`.
2. **Phase 8 is atomic.** Either all screens are done and deletion happens all at once, or nothing gets deleted. There is no "partial cleanup."
3. **No screen launches with missing features.** If a screen is not complete, it is not added to the nav graph. Incomplete = invisible.
4. **The RN file is the spec.** If you are unsure what a screen should do, open the `.tsx` file. It tells you exactly.
5. **Every phase must compile before the next begins.** Broken builds do not move forward.
6. **`AgentViewModel` is the only bridge.** No Compose screen calls a Kotlin service directly. All calls go through `AgentViewModel`.
7. **All native services are untouched.** Accessibility, screen capture, LLM, OCR, RL — none of these are modified during migration.
