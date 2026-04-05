# Opening ARIA in Firebase Studio / Android Studio

## Quick Start

1. Open Firebase Studio (or Android Studio)
2. **File → Open** → select the `artifacts/mobile/android/` folder
3. When prompted, let Gradle sync — it will detect **native-only mode** automatically
   (no `node_modules` = no React Native plugins loaded, only pure Kotlin + Compose + NDK)
4. Set the Android SDK path if Studio asks (see SDK setup below)
5. Choose the `app` run configuration and press **Run**

---

## SDK Setup

If Gradle sync fails with "SDK location not found", create or edit:

```
artifacts/mobile/android/local.properties
```

Set the path to your Android SDK:

| Platform | Path |
|----------|------|
| Firebase Studio (cloud workstation) | `sdk.dir=/home/user/Android/sdk` |
| macOS | `sdk.dir=/Users/YOUR_NAME/Library/Android/sdk` |
| Linux | `sdk.dir=/home/YOUR_NAME/Android/Sdk` |
| Windows | `sdk.dir=C\:\\Users\\YOUR_NAME\\AppData\\Local\\Android\\Sdk` |

A template is at `local.properties.template` — copy and rename it.

---

## What Syncs in Native-Only Mode

| Component | Status |
|-----------|--------|
| Jetpack Compose UI (all 8 screens) | ✅ Compiles |
| Kotlin core (AI, OCR, RL, Services) | ✅ Compiles |
| NDK / llama.cpp JNI (arm64-v8a) | ✅ Compiles |
| OkHttp, ML Kit, ONNX Runtime, MediaPipe | ✅ Compiles |
| Coil image loading | ✅ Compiles |
| React Native bridge & Expo modules | ⏭ Skipped (no node_modules) |

The skipped components are **already being migrated away** — all 8 screens are
pure Kotlin and do not call the bridge at all.

---

## Emulator Setup

For best compatibility with the Samsung Galaxy M31 target:
- **System image:** Android 14 (API 34), ABI: `arm64-v8a` or `x86_64`
- **RAM:** 4 GB minimum (LLM inference is memory-hungry)
- **Internal storage:** 8 GB+ (GGUF model files are 2–8 GB)

The NDK build only produces `arm64-v8a` libraries. On an `x86_64` emulator the
JNI layer will not load — AI inference features will show "model not loaded" but
all UI navigation, settings, labeling, and training (except actual inference) can
still be tested.

**For full inference testing:** Use a physical `arm64-v8a` device (Galaxy M31 or
similar) via USB or Firebase Test Lab.

---

## Gradle Version

The project uses **Gradle 8.13**. Firebase Studio will auto-download it on first sync.
Gradle wrapper: `gradle/wrapper/gradle-wrapper.properties`

---

## NDK Setup

The project uses NDK version `27.1.12297006` (`r27.1`).

In Android Studio: **Tools → SDK Manager → SDK Tools → NDK (Side by side)**
Install version `27.1.12297006`. Accept all licenses.

Firebase Studio cloud workstations usually have a pre-installed NDK — run:
```bash
ls $ANDROID_HOME/ndk/
```
If `27.1.12297006` is not listed, install via SDK Manager or:
```bash
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "ndk;27.1.12297006"
```

---

## Build Commands (terminal)

```bash
# From artifacts/mobile/android/

# Debug build (native-only, no node required)
./gradlew :app:assembleDebug

# Install on connected device
./gradlew :app:installDebug

# Launch ComposeMainActivity
adb shell am start -n com.ariaagent.mobile/.ui.ComposeMainActivity
```

---

## Migration Status

See `migration.md` (root of repo) for the full migration plan and screen checklist.

Current status:
- Phases 1–7 **complete** — all 8 Compose screens written
- Phase 8 (delete RN layer) — pending emulator verification
- Phase 9 (strip build system) — pending Phase 8
