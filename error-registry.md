# ARIA AI — Error Registry

> **Last updated:** 2026-04-03  
> **Scanned:** TypeScript compile · Android build config · Kotlin native modules · Expo/RN dependency audit · iOS code paths · Resource files · Workspace config

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
| ✅ Fixed | ⚠️ Open (manual step only) | ⚙️ Auto-fix on native build |
|---------|--------------------------|----------------------------|
| E01 E02 E05 E06 E07 E08 E09 | E03 | E10a E10b |

---

## Error Table

| ID | Sev | Type | Location | Description | Status |
|----|-----|------|----------|-------------|--------|
| E01 | 🟠 HIGH | TypeScript | `artifacts/mockup-sandbox/src/components/ui/calendar.tsx:132` | TS2322 React 19 ref callback type incompatibility (`VoidOrUndefinedOnly`). | ✅ FIXED — cast to `RefCallback<HTMLDivElement>` |
| E02 | 🟠 HIGH | TypeScript | `artifacts/mockup-sandbox/src/components/ui/spinner.tsx:7` | TS2322 conflicting `@types/react` copies; ref spread onto Lucide icon. | ✅ FIXED — simplified Spinner to `{ className?: string }` |
| E03 | 🔴 CRITICAL | Native Build | `artifacts/mobile/android/app/src/main/cpp/llama.cpp/` | **llama.cpp submodule missing.** `CMakeLists.txt` calls `add_subdirectory(llama.cpp)` but directory doesn't exist. Android native JNI build fails. JS/Expo Go unaffected (LlamaEngine auto-stubs). | ⚠️ OPEN — `git submodule add https://github.com/ggerganov/llama.cpp …/llama.cpp` (~400 MB, manual step when building for device) |
| E04 | 🟡 MEDIUM | iOS-only API | `artifacts/mobile/app/(tabs)/_layout.tsx` | `expo-router/unstable-native-tabs` — unstable iOS-18+ experimental API. Subsumed by E06. | ✅ FIXED via E06 |
| E05 | 🟢 LOW | Workspace | `pnpm-workspace.yaml` | `lib/*` and `lib/integrations/*` point to empty directory — pnpm install warnings. | ✅ FIXED — removed stale globs |
| E06 | 🟡 MEDIUM | iOS-only Code | `artifacts/mobile/app/(tabs)/_layout.tsx` | Entire `NativeTabLayout` block uses iOS-18+ APIs (`NativeTabs`, `Icon`, `Label`, `SymbolView`, `isLiquidGlassAvailable`). `isIOS` conditionals throughout `ClassicTabLayout`. All dead code on Android. | ✅ FIXED — rewrote to pure Android tab layout; removed all iOS branches and dead imports |
| E07 | 🟢 LOW | iOS-only Code | `artifacts/mobile/app/(tabs)/chat.tsx:262` | `behavior={Platform.OS === "ios" ? "padding" : "height"}` — dead iOS branch, both arms produce same result on Android. | ✅ FIXED — hardcoded `behavior="height"`, removed redundant offset prop |
| E08 | 🟢 LOW | App Config | `artifacts/mobile/app.json` | `"ios": { "supportsTablet": false }` stale section. `"android": {}` missing `package` field. | ✅ FIXED — removed `ios` section, added `"package": "com.ariaagent.mobile"` |
| E09 | 🟡 MEDIUM | Dead Packages | `artifacts/mobile/package.json` | `expo-symbols@~1.0.8` (SF Symbols, iOS-only) and `expo-glass-effect@~0.1.4` (Liquid Glass, iOS-only) installed but fully unused on Android. | ✅ FIXED — removed both packages |
| E10a | ⚙️ AUTO | Kotlin Stub | `…/core/ai/LlamaEngine.kt` | `jniAvailable=false` when `libllama-jni.so` absent. Returns hardcoded stub JSON. Catches `UnsatisfiedLinkError` in `companion object init`. | ⚙️ AUTO-FIXES when E03 resolved and native build compiles the `.so` |
| E10b | ⚙️ AUTO | Kotlin Stub | `…/core/rl/LoraTrainer.kt` | `jniTrainingAvailable=false`; `stubTrainLora()` writes metadata-only `.bin` so versioning/hot-reload paths work without real training. | ⚙️ AUTO-FIXES when E03 resolved |

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

## E03 — When Ready to Build for Device

```bash
cd artifacts/mobile/android/app/src/main/cpp
git submodule add https://github.com/ggerganov/llama.cpp llama.cpp
git submodule update --init --recursive
eas build --profile development --platform android
```

Once compiled, **E10a and E10b auto-resolve** — both `LlamaEngine` and `LoraTrainer`
detect `libllama-jni.so` on startup and switch from stub → real JNI automatically.

---

## Notes

- `org.json.JSONArray/JSONObject` in Kotlin — **not errors**, standard Android SDK class in `android.jar`
- `ComposeMainActivity` registered in `AndroidManifest.xml` but not set as launcher — **intentional**, secondary activity for Jetpack Compose native UI testing
- `@tanstack/react-query` polyfills (`@stardazed/streams-text-encoding`, `@ungap/structured-clone`) — **not dead code**, required for React Native Web fetch compatibility
