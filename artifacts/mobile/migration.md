# ARIA — Expo → Pure Kotlin Migration Plan

**Target device:** Samsung Galaxy M31, Exynos 9611, arm64-v8a  
**Goal:** Delete all React Native / Expo JS code. Kotlin + Jetpack Compose + NDK only.

---

## Status

| Phase | Description | Status |
|-------|-------------|--------|
| 0 | Environment setup | ✅ DONE |
| 1 | Promote ComposeMainActivity as launcher | ✅ DONE |
| 2 | SettingsScreen.kt — fill all gaps | ✅ DONE |
| 3 | ActivityScreen.kt — fill all gaps | ✅ DONE |
| 4 | ControlScreen.kt — fill all gaps | ✅ DONE |
| 5 | ChatScreen.kt — on-device LLM chat | ✅ DONE |
| 6 | TrainScreen.kt — RL cycle + IRL video | ✅ DONE |
| 7 | LabelerScreen.kt — screenshot annotation | ✅ DONE |
| 7b | ARIAComposeApp.kt — wire Chat/Train tabs + Labeler route | ✅ DONE |
| 8 | Delete React Native layer | 🔒 GATE: verify all 8 screens on emulator |
| 9 | Strip build system — remove Expo/RN entries from build.gradle | 🔒 GATE: Phase 8 |

---

## Screen Inventory

### Bottom-nav tabs (7)
| Route | File | Lines | Status |
|-------|------|-------|--------|
| `dashboard` | DashboardScreen.kt | 480 | ✅ verified |
| `control` | ControlScreen.kt | 824 | ✅ verified |
| `chat` | ChatScreen.kt | 491 | ✅ Phase 5 done |
| `activity` | ActivityScreen.kt | 530 | ✅ verified |
| `train` | TrainScreen.kt | 545 | ✅ Phase 6 done |
| `modules` | ModulesScreen.kt | 456 | ✅ verified |
| `settings` | SettingsScreen.kt | 794 | ✅ verified |

### Full-screen routes (no bottom nav)
| Route | File | Lines | Status |
|-------|------|-------|--------|
| `labeler` | LabelerScreen.kt | 641 | ✅ Phase 7 done |

---

## Architecture Notes

- **Zero bridge calls** in Phases 5–7 screens. All goes through AgentViewModel.
- **AgentViewModel.kt** extended in sessions:
  - Chat: `sendChatMessage()`, `clearChat()`, `chatMessages`, `chatThinking`
  - Train: `runRlCycle()`, `processIrlVideo()`, `setAutoScheduleRl()`, `refreshLearningStatus()`
  - Labeler: `captureScreenForLabeling()`, `addLabelerPin()`, `updateLabelerLabel()`, `deleteLabelerLabel()`, `autoDetectLabelerPins()`, `enrichAllLabelerPins()`, `saveLabelerLabels()`, `clearLabelerCapture()`
  - Helpers: `buildLabelEnrichPrompt()`, `parseLabelEnrichOutput()`, `resolveContentUri()`
- **Coil 2.7.0** added to `app/build.gradle` for `AsyncImage` in LabelerScreen.
- **LabelerScreen** navigation: not a tab — pushed from ControlScreen and TrainScreen via `onNavigateToLabeler` lambda.
- **Critical rule:** Never delete `*.tsx`/`*.ts` files until all 8 screens are verified on device.

---

## React Native files — DO NOT DELETE YET 🔒

All `.tsx` / `.ts` files under `app/` are locked until Phase 8 gate is cleared.

| File | Kotlin Replacement | Verified |
|------|--------------------|---------|
| app/chat.tsx (601L) | ChatScreen.kt | ☐ |
| app/train.tsx (692L) | TrainScreen.kt | ☐ |
| app/labeler.tsx (1017L) | LabelerScreen.kt | ☐ |
| app/(tabs)/index.tsx | DashboardScreen.kt | ☐ |
| app/(tabs)/control.tsx | ControlScreen.kt | ☐ |
| app/(tabs)/activity.tsx | ActivityScreen.kt | ☐ |
| app/(tabs)/modules.tsx | ModulesScreen.kt | ☐ |
| app/settings.tsx | SettingsScreen.kt | ☐ |

---

## Immediate Next Steps

1. Build on device / emulator: `./gradlew :app:assembleDebug`
2. Install and smoke-test all 8 screens
3. Tick verified checkboxes above
4. Phase 8: delete RN layer (rm app/*.tsx, app/(tabs)/*.tsx, node_modules/, package.json)
5. Phase 9: strip Expo entries from build.gradle + settings.gradle
