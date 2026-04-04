# ARIA — Full Native Migration Plan
**From:** Expo + React Native + Kotlin/Compose hybrid  
**To:** Pure Kotlin + Jetpack Compose + NDK (no JS, no React Native, no Expo)  
**Rule:** Every feature in the current UI must exist in the new UI. More is allowed. Less is not.

---

## Status Legend
- `[ ]` Not started
- `[~]` In progress
- `[x]` Done
- `[!]` Blocked / needs decision

---

## Reality Check Before Starting

The Compose layer is already 70% built. This is not a rewrite — it is a cleanup + gap fill.

| What exists in Kotlin/Compose already | Lines |
|---|---|
| DashboardScreen.kt | 476 |
| ControlScreen.kt | 469 |
| ModulesScreen.kt | 456 |
| ActivityScreen.kt | 225 |
| SettingsScreen.kt | 265 |
| ARIAComposeApp.kt (nav + shell) | 154 |
| ComposeMainActivity.kt | 54 |
| AgentViewModel.kt | 489 |
| ARIATheme.kt | 139 |
| **Total already done** | **2,727** |

| What must be deleted | Lines |
|---|---|
| All JS/TS screens + components (24 files) | 9,328 |
| AgentCoreModule.kt (RN bridge) | 1,457 |
| AgentCorePackage.kt | 14 |
| RN config files (metro, babel, app.json, eas.json) | ~300 |
| **Total deleted** | **~11,100** |

| What must be written new | Lines (est.) |
|---|---|
| ChatScreen.kt | ~400 |
| TrainScreen.kt | ~350 |
| LabelerScreen.kt | ~600 |
| Gap fills across 4 existing screens | ~400 |
| **Total new code** | **~1,750** |

---

## Phase 0 — Environment Setup
*Do this before touching any code.*

- [ ] Open project in Android Studio / Firebase Studio
- [ ] Create branch: `migration/full-native`
- [ ] Confirm emulator boots (API 34, arm64-v8a or x86_64)
- [ ] Run `./gradlew assembleDebug` from `artifacts/mobile/android/` — confirm it compiles today
- [ ] Launch `ComposeMainActivity` manually via adb to confirm Compose shell works:
  ```bash
  adb shell am start -n com.ariaagent.mobile/.ui.ComposeMainActivity
  ```
- [ ] Note which screens render and which are blank

---

## Phase 1 — Promote Compose as Launcher
*Est: 2–3 hours. Zero new features, just routing.*

### 1.1 AndroidManifest.xml
- [ ] Move `android.intent.action.MAIN` + `android.intent.category.LAUNCHER` intent filter from `MainActivity` to `ComposeMainActivity`
- [ ] Remove `exported="true"` from old `MainActivity` (or delete the activity entry entirely)
- [ ] Keep `ComposeMainActivity` theme as `@style/Theme.ARIAAgent`

### 1.2 MainApplication.kt
- [ ] Delete everything React Native related:
  - Remove `ReactApplication` interface
  - Remove `reactNativeHost` override
  - Remove `reactHost` override
  - Remove all RN/Expo imports
- [ ] Keep: `SoLoader.init(this, OpenSourceMergedSoMapping)` — NDK still needs it
- [ ] Final MainApplication should be ~20 lines: plain `Application` class with SoLoader init

### 1.3 MainActivity.kt
- [ ] Delete the file entirely — ComposeMainActivity replaces it

### Checkpoint: App launches showing Compose Dashboard. All existing Compose screens navigable.

---

## Phase 2 — Gut the React Native Layer
*Est: 3–4 hours. Mostly deletion.*

### 2.1 build.gradle (app-level)
Remove these dependency blocks entirely:
- [ ] `implementation("com.facebook.react:react-android")`
- [ ] `implementation("com.facebook.react:hermes-android")`
- [ ] All `implementation project(':async-storage')` lines
- [ ] All `implementation project(':react-native-*)` lines
- [ ] All `implementation project(':expo-*')` lines
- [ ] Remove `apply plugin: "com.facebook.react"` and the `react { }` block

Keep:
- [ ] Compose BOM + all Compose dependencies
- [ ] NDK / cmake block (llama.cpp still compiles)
- [ ] OkHttp, ML Kit, ONNX Runtime, MediaPipe, Coroutines, DataStore, AppCompat, Material

### 2.2 settings.gradle
- [ ] Remove the entire `autolinkLibrariesFromCommand` block
- [ ] Remove all `include ':async-storage'`, `include ':react-native-*'`, `include ':expo-*'` lines
- [ ] Keep only `:app` include

### 2.3 Delete bridge layer
- [ ] Delete `bridge/AgentCoreModule.kt` (1,457 lines of RN bridge — gone)
- [ ] Delete `bridge/AgentCorePackage.kt`
- [ ] Delete `bridge/` folder

### 2.4 Delete all JS/TS files
- [ ] Delete `app/` folder (all route files)
- [ ] Delete `components/` folder
- [ ] Delete `context/` folder
- [ ] Delete `hooks/` folder
- [ ] Delete `native-bindings/` folder
- [ ] Delete `constants/` folder
- [ ] Delete `index.js`
- [ ] Delete `metro.config.js`
- [ ] Delete `babel.config.js`
- [ ] Delete `app.json`
- [ ] Delete `eas.json`
- [ ] Delete `expo-env.d.ts`
- [ ] Delete `tsconfig.json`
- [ ] Delete `package.json`

### 2.5 Delete RN config in root gradle
- [ ] Remove `reactNativeArchitectures` from `gradle.properties` if present
- [ ] Remove any `hermesEnabled` flag that was RN-specific

### Checkpoint: Project compiles with zero JS. `./gradlew assembleDebug` succeeds. All Compose screens still work.

---

## Phase 3 — Fill Settings Screen Gaps
*Current: 265 lines. Target: ~500 lines. Est: 4–6 hours.*

RN `settings.tsx` has 852 lines. Feature diff against `SettingsScreen.kt`:

### Missing features to add:
- [ ] **Permissions section** — three rows with status + action button each:
  - Accessibility Service: shows granted/not granted, button opens Settings
  - Screen Capture: shows granted/not granted, button requests permission
  - Notifications: shows granted/not granted, button opens Settings
- [ ] **Reset Agent button** — calls `ProgressPersistence.clearProgress()` with confirmation dialog
- [ ] **Clear Memory button** — calls `ExperienceStore.clearAll()` with confirmation dialog
- [ ] **Context window slider** (currently a text field in Compose — RN used a slider)
- [ ] **Temperature slider** (same — 0–100 range, displayed as 0.0–1.0)
- [ ] **GPU layers slider** (0–32 range)
- [ ] **Max tokens slider**
- [ ] **System info row** — shows device model + Android version (new, not in RN)

### Existing features to verify work:
- [~] RL toggle switch
- [~] Save Settings button
- [~] Inference text fields

---

## Phase 4 — Fill Activity / Logs Screen Gaps
*Current: 225 lines. Target: ~350 lines. Est: 3–4 hours.*

RN `logs.tsx` (317 lines) has two tabs. `ActivityScreen.kt` only shows one list.

### Missing features to add:
- [ ] **Tab bar** — "Actions" tab and "Memory" tab (same as RN)
- [ ] **Memory tab** — displays `ExperienceStore` entries with:
  - Summary text
  - App name + timestamp
  - Success/failure indicator
  - Confidence score
- [ ] **Clear memory button** — visible only in memory tab, same confirmation dialog as Settings
- [ ] **Empty state** — different message per tab ("No actions yet" / "Memory is empty")

### Existing features to verify:
- [~] Action log list with timestamps
- [~] Success/failure colour coding
- [~] Tool type icons

---

## Phase 5 — Fill Control Screen Gaps
*Current: 469 lines. Target: ~600 lines. Est: 3–4 hours.*

RN `control.tsx` (816 lines) has more controls. Diff:

### Missing features to add:
- [ ] **Learn-only mode button** — calls `AgentLoop.startLearnOnly()`, separate from START
- [ ] **Game mode card** — shows when `agentState.gameMode != "none"`:
  - Episode count
  - Step count
  - Current score / high score
  - Total reward
  - Last action taken
- [ ] **Task queue list** — shows pending tasks from `TaskQueueManager` with:
  - Goal text
  - Priority badge
  - Enqueued timestamp
  - Swipe-to-delete or X button
- [ ] **App package autocomplete hint** — small label showing detected current app

### Existing features to verify:
- [~] Readiness indicators (model, accessibility, screen capture)
- [~] Goal text field + app package field
- [~] Preset task chips
- [~] START / PAUSE / RESUME / STOP buttons

---

## Phase 6 — Build Chat Screen (NEW)
*Est: 1–2 days. No Compose equivalent exists.*  
Feature source: `chat.tsx` (601 lines)

### UI structure:
- [ ] `LazyColumn` message list that auto-scrolls to bottom on new message
- [ ] User message bubble (right-aligned, primary colour background)
- [ ] AI message bubble (left-aligned, surface colour background)
- [ ] Welcome message shown on first open
- [ ] **Typing indicator** — three animated dots while AI is generating (coroutine-driven)
- [ ] **Context line** — small text at top showing current agent task/app
- [ ] **Text input bar** at bottom:
  - `OutlinedTextField` for message input
  - Send button (disabled when input empty or thinking)
  - Clear conversation button (top right)
- [ ] **Preset prompt chips** — horizontal scrollable row of quick-send tags:
  - "What are you doing?"
  - "Pause and explain"
  - "How is the model performing?"
  - "What did you learn?"
  - "Show memory summary"
- [ ] Messages persist in-memory for session (no DB needed)
- [ ] Calls `LlamaEngine.runInference()` directly — no bridge needed
- [ ] **NEW (beyond RN):** Show token/sec rate beneath each AI response

---

## Phase 7 — Build Train Screen (NEW)
*Est: 1.5 days. No Compose equivalent exists.*  
Feature source: `train.tsx` (692 lines)

### Sections:

**RL Status card:**
- [ ] LoRA version number
- [ ] Latest adapter path
- [ ] Untrained samples count
- [ ] Adam step count
- [ ] Last policy loss value
- [ ] Refresh button

**Run RL Cycle card:**
- [ ] Run button (disabled while running, shows progress indicator)
- [ ] Result display after run: samples used, adapter path, LoRA version, error if failed

**Video Training (IRL) card:**
- [ ] Pick video button — uses Android file picker (`ActivityResultContracts.GetContent`)
- [ ] Selected video name display + clear button
- [ ] Goal text field
- [ ] Target app package field
- [ ] Run IRL button (disabled until video + goal filled)
- [ ] Results display: frames processed, tuples extracted, LLM-assisted count

**Navigate to Labeler:**
- [ ] Button that navigates to LabelerScreen

**NEW (beyond RN):**
- [ ] Auto-schedule RL toggle — triggers RL cycle automatically when untrainedSamples > 50
- [ ] Last trained timestamp display

---

## Phase 8 — Build Labeler Screen (NEW)
*Est: 2–3 days. Most complex screen. No Compose equivalent exists.*  
Feature source: `labeler.tsx` (1,017 lines)

### Core flow:
- [ ] **Capture button** — calls `ScreenObserver` / `AgentCoreModule.captureScreenForLabeling()` directly
- [ ] **Image display** — `AsyncImage` or `Canvas` composable showing captured screenshot
- [ ] **Tap to place pin** — `pointerInput` tap handler on image, converts tap coords to normalised 0–1

### Pin overlay:
- [ ] `Box` overlay on image with positioned pin markers
- [ ] Each pin: small coloured circle + label name text
- [ ] Selected pin: highlighted ring
- [ ] Tap pin to select/deselect

### Toolbar buttons:
- [ ] **Auto-detect** — calls `ObjectDetectorEngine.detect()`, places pins at detected object centres
- [ ] **Enrich All** — calls `LlamaEngine.enrichLabelsWithLLM()`, updates all pin metadata
- [ ] **Save** — calls `ObjectLabelStore.saveLabels()`, shows confirmation, navigates back
- [ ] **Back** — with unsaved-changes warning dialog if labels modified

### Pin editor panel (slides up when pin selected):
- [ ] Name text field
- [ ] Context text field  
- [ ] Element type selector — horizontal chip row: button / text / input / icon / image / container / toggle / link / unknown
- [ ] Delete label button with confirmation
- [ ] Importance score display (0–10, set by LLM enrichment)

### Stats bar:
- [ ] Total labels count
- [ ] Enriched count
- [ ] OCR text detected (collapsed, expandable)

### NEW (beyond RN):
- [ ] Long-press pin to quick-delete (no dialog)
- [ ] Drag pin to reposition (not in RN version)

---

## Phase 9 — Enhanced Features (Beyond Current RN)
*These push the UI beyond parity. Do after all parity work is green.*

- [ ] **Bottom nav animations** — slide transition between tabs (RN had no animation)
- [ ] **Thermal banner** — sticky warning strip on all screens when `thermalLevel == "hot"` or `"critical"` (DashboardScreen has it, extend globally)
- [ ] **Live token streaming** — Chat shows tokens appearing word-by-word via `AgentEventBus` flow, not polled
- [ ] **Model download screen** — full-screen Compose composable with `LinearProgressIndicator`, speed display, cancel button — shown when model not ready (currently ModelDownloadScreen is RN only)
- [ ] **Floating action button** on Dashboard — quick-launch task without going to Control tab
- [ ] **Haptic feedback** — `HapticFeedbackManager` on all button presses
- [ ] **Adaptive dark theme** — system dark mode respected via `isSystemInDarkTheme()`

---

## Phase 10 — Cleanup + Release
- [ ] Delete `checkup/` folder from root (generated artifact, not source)
- [ ] Delete `migration.md` from root (or move to `docs/`)
- [ ] ProGuard rules — remove all React Native keep rules from `proguard-rules.pro`
- [ ] Bump `versionCode` to 2 in `build.gradle`
- [ ] Run `./gradlew assembleRelease` — confirm zero warnings about missing RN classes
- [ ] Install release APK on real device
- [ ] Verify all 8 screens navigate correctly
- [ ] Verify logcat shows zero `ReactNative` or `Expo` tag lines

---

## File Status Table

| File | Action | Phase | Done |
|---|---|---|---|
| `MainActivity.kt` | DELETE | 1 | `[ ]` |
| `MainApplication.kt` | REWRITE (strip RN) | 1 | `[ ]` |
| `AndroidManifest.xml` | MODIFY (swap launcher) | 1 | `[ ]` |
| `build.gradle` | MODIFY (strip RN deps) | 2 | `[ ]` |
| `settings.gradle` | MODIFY (strip RN modules) | 2 | `[ ]` |
| `bridge/AgentCoreModule.kt` | DELETE | 2 | `[ ]` |
| `bridge/AgentCorePackage.kt` | DELETE | 2 | `[ ]` |
| `app/(tabs)/index.tsx` | DELETE | 2 | `[ ]` |
| `app/(tabs)/chat.tsx` | DELETE | 2 | `[ ]` |
| `app/(tabs)/control.tsx` | DELETE | 2 | `[ ]` |
| `app/(tabs)/logs.tsx` | DELETE | 2 | `[ ]` |
| `app/(tabs)/modules.tsx` | DELETE | 2 | `[ ]` |
| `app/(tabs)/settings.tsx` | DELETE | 2 | `[ ]` |
| `app/(tabs)/train.tsx` | DELETE | 2 | `[ ]` |
| `app/_layout.tsx` | DELETE | 2 | `[ ]` |
| `app/labeler.tsx` | DELETE | 2 | `[ ]` |
| `components/*.tsx` (7 files) | DELETE | 2 | `[ ]` |
| `context/AgentContext.tsx` | DELETE | 2 | `[ ]` |
| `hooks/useColors.ts` | DELETE | 2 | `[ ]` |
| `native-bindings/AgentCoreBridge.ts` | DELETE | 2 | `[ ]` |
| `native-bindings/NativeAgentCore.ts` | DELETE | 2 | `[ ]` |
| `constants/colors.ts` | DELETE | 2 | `[ ]` |
| `index.js` | DELETE | 2 | `[ ]` |
| `metro.config.js` | DELETE | 2 | `[ ]` |
| `babel.config.js` | DELETE | 2 | `[ ]` |
| `app.json` | DELETE | 2 | `[ ]` |
| `eas.json` | DELETE | 2 | `[ ]` |
| `package.json` | DELETE | 2 | `[ ]` |
| `expo-env.d.ts` | DELETE | 2 | `[ ]` |
| `tsconfig.json` | DELETE | 2 | `[ ]` |
| `SettingsScreen.kt` | MODIFY (add gaps) | 3 | `[ ]` |
| `ActivityScreen.kt` | MODIFY (add memory tab) | 4 | `[ ]` |
| `ControlScreen.kt` | MODIFY (add learn-only, game stats, queue) | 5 | `[ ]` |
| `ChatScreen.kt` | CREATE NEW | 6 | `[ ]` |
| `TrainScreen.kt` | CREATE NEW | 7 | `[ ]` |
| `LabelerScreen.kt` | CREATE NEW | 8 | `[ ]` |
| `ARIAComposeApp.kt` | MODIFY (add new routes to nav) | 6–8 | `[ ]` |
| `AgentViewModel.kt` | MODIFY (add chat + train methods) | 6–7 | `[ ]` |
| `DashboardScreen.kt` | KEEP | — | `[x]` |
| `ModulesScreen.kt` | KEEP | — | `[x]` |
| `ComposeMainActivity.kt` | KEEP | — | `[x]` |
| `ARIATheme.kt` | KEEP | — | `[x]` |
| All core Kotlin (40 files) | KEEP | — | `[x]` |
| All C++ / JNI | KEEP | — | `[x]` |

---

## Time Estimate

| Phase | Task | Est. Time |
|---|---|---|
| 0 | Environment setup + baseline build | 2–3 hours |
| 1 | Promote Compose as launcher, strip MainApplication | 2–3 hours |
| 2 | Delete RN layer, strip build.gradle + settings.gradle | 3–4 hours |
| 3 | Fill Settings gaps | 4–6 hours |
| 4 | Fill Activity/Logs gaps | 3–4 hours |
| 5 | Fill Control gaps | 3–4 hours |
| 6 | Build Chat screen | 1–2 days |
| 7 | Build Train screen | 1–1.5 days |
| 8 | Build Labeler screen | 2–3 days |
| 9 | Enhanced features | 1+ days |
| 10 | Cleanup + release build | 3–4 hours |
| **Total** | | **7–12 days focused** |

---

## Non-Negotiable Rules
1. No screen in the final app may have fewer features than its RN counterpart.
2. All native services (accessibility, screen capture, LLM, OCR, RL) must remain fully functional — they are not touched during migration.
3. Every phase must compile before starting the next one.
4. The `AgentViewModel` is the single source of truth — no screen calls Kotlin services directly.
5. No placeholder screens — if a screen is not done, it does not appear in the nav graph.
