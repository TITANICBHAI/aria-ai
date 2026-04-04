# ARIA — Build and Install Guide

> For developers: how to build the APK from source and what each build component does.

---

## Build Requirements

| Tool | Version |
|---|---|
| Android Studio | Hedgehog (2023.1.1) or newer |
| Android NDK | r27c |
| CMake | 3.22.1 |
| Gradle | 8.13 |
| JDK | 17 |
| Node.js | 24+ |
| pnpm | 9+ |
| EAS CLI | 10.0.0+ |

---

## What Gets Compiled

ARIA has three build layers:

### 1. Native C++ Layer (llama.cpp via NDK)
- **Source:** `artifacts/mobile/android/app/src/main/cpp/`
- **Output:** `libllama-jni.so` (~30–50 MB stripped, arm64-v8a)
- **What it does:** Runs the Llama 3.2-1B model for on-device AI inference.
- **llama.cpp** is cloned automatically during the EAS build via the pre-install hook.

### 2. Kotlin Layer
- **Source:** `artifacts/mobile/android/app/src/main/kotlin/`
- **What it does:** All agent logic — screen capture, OCR, RL, memory, the agent loop.

### 3. React Native / JS Layer
- **Source:** `artifacts/mobile/app/`, `artifacts/mobile/components/`
- **What it does:** The UI shell only. No logic lives here.

---

## Building with EAS (Recommended)

EAS (Expo Application Services) handles the full build including the NDK compilation of llama.cpp.

```bash
cd artifacts/mobile
eas build --profile development --platform android
```

**Build profiles:**

| Profile | Output | Use for |
|---|---|---|
| `development` | Debug APK | Testing on device |
| `preview` | Release APK | Internal distribution |
| `production` | AAB (App Bundle) | Play Store |

---

## What the Pre-Install Hook Does

Before every EAS build, `scripts/eas-pre-install.sh` runs automatically and:

1. **Clones llama.cpp** if not already present — pinned to a specific commit for reproducible builds.
2. **Enforces Gradle 8.13** — updates the Gradle wrapper if it is on an older version.

You do not need to run this manually — EAS triggers it via the `eas-build-pre-install` hook in `package.json`.

---

## APK Size Explained

A common question: "The APK is 71 MB but llama.cpp source is 130 MB — is llama.cpp included?"

**Yes, it is.** The 130 MB is the *source code* size. After compilation, the stripped `.so` binary is 30–50 MB. The final APK includes:

| Component | Approx. size |
|---|---|
| `libllama-jni.so` (compiled llama.cpp) | ~35–50 MB |
| `libreactnativejni.so` (React Native) | ~8 MB |
| `libhermes.so` (JS engine) | ~4 MB |
| JS bundle + assets | ~5–8 MB |
| Kotlin classes + resources | ~3–5 MB |
| **Total** | **~60–80 MB** |

The AI model file (~870 MB) is **not in the APK**. It is downloaded separately at first launch.

---

## Verifying llama.cpp Is in the APK

To confirm `libllama-jni.so` is present in the built APK:

```bash
unzip -l app-debug.apk | grep libllama
```

You should see:
```
lib/arm64-v8a/libllama-jni.so
```

If this line is missing, the NDK build failed. Check the Gradle log for CMake errors.

---

## Local Build (Without EAS)

```bash
# 1. Clone llama.cpp manually
cd artifacts/mobile/android/app/src/main/cpp
git clone --depth=1 --branch b4870 https://github.com/ggerganov/llama.cpp.git llama.cpp

# 2. Install JS dependencies
cd artifacts/mobile
pnpm install

# 3. Build APK via Gradle
cd android
./gradlew assembleDebug
```

The APK will be at `android/app/build/outputs/apk/debug/app-debug.apk`.

---

## Disabling llama.cpp (Stub Mode)

If you want to build and test the UI without compiling llama.cpp (much faster builds):

1. In `build.gradle`, comment out the `externalNativeBuild` block.
2. Build normally.

The app will run in stub mode — all AI responses will be pre-canned stubs. All UI, navigation, and module screens work normally. Only real inference is unavailable.
