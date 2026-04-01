# Current Task

> Update this file every time a new task begins.
> Format: WHAT → WHY → HOW → Acceptance criteria → Blockers → Next task

---

## Task: Phase 0 Completion — Kotlin Android Project Structure

**Status:** In progress (`[~]`)
**Priority:** Blocks everything. No Kotlin code can run until the Android project exists.
**Before this:** JS UI shell done (5 screens, bridge stubs, dark theme)
**After this:** Phase 1 (model download service) can begin

---

## WHAT

Create the Android Gradle project inside `android/` with the correct directory structure, Gradle configuration, and New Architecture (TurboModule) setup so that Kotlin code can actually compile and the TurboModule bridge can connect to the JS UI.

Nothing from Phase 1 onwards can be built until this exists. The GGUF can't be downloaded, llama.cpp can't be loaded, no gesture can be dispatched — all of it requires a working Android project.

---

## WHY

### Why Kotlin and not more JS?

From the technical documents: the JS layer is a **temporary UI shell**. JS must never own logic permanently because:
- JS runs on Hermes (JavaScript engine) — single-threaded, not suitable for heavy parallel work
- Kotlin uses Coroutines — true parallel background threads for inference, OCR, RL training
- On-device AI (llama.cpp, ML Kit, MediaProjection, AccessibilityService) has no JS bindings — all Android-native APIs
- Phase 2 and Phase 3 will thin and eventually remove the JS layer entirely

### Why TurboModules (New Architecture)?

Legacy React Native bridge serializes everything to JSON over an async queue — this adds latency for every LLM call and gesture dispatch. JSI (JavaScript Interface) lets the JS engine hold **direct references** to Kotlin/C++ objects, making the bridge near-native speed. This matters when the agent is running a tight observe→reason→act loop.

### Why this Gradle structure?

The monorepo uses pnpm hoisting — all `node_modules` land at the repo root, not inside `artifacts/mobile/`. Android's Gradle autolinking system assumes `node_modules` is in a fixed relative path. We must tell Gradle where to find `react-native` and the codegen output by overriding `reactNativeDir` and `codegenDir` in `settings.gradle`.

---

## HOW

### Step 1 — Directory layout to create

```
android/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/ariaagent/
│   │   │   └── MainApplication.kt
│   │   └── res/
│   ├── build.gradle
│   └── proguard-rules.pro
├── core/
│   ├── ai/              ← llama.cpp JNI, ModelManager, AgentLoop
│   ├── ocr/             ← ML Kit wrapper
│   ├── rl/              ← PolicyNetwork, LoraTrainer, IrlModule, Scheduler
│   └── memory/          ← ExperienceStore, EmbeddingEngine
├── system/
│   ├── accessibility/   ← AgentAccessibilityService
│   ├── screen/          ← ScreenCaptureService (MediaProjection)
│   └── actions/         ← GestureEngine
├── bridge/
│   ├── turbo/           ← AgentCoreModule.kt (TurboModule registration)
│   └── dto/             ← data classes shared between JS and Kotlin
├── ui-native/           ← empty, for Phase 11 Jetpack Compose
├── build.gradle         ← root Gradle config
├── settings.gradle      ← monorepo path overrides (reactNativeDir, codegenDir)
└── gradle.properties    ← newArchEnabled=true, memory settings
```

### Step 2 — `settings.gradle` (critical for pnpm monorepo)

```groovy
// Must point to hoisted node_modules at repo root, not local artifacts/mobile/
def reactNativeDir = new File(["node", "--print", "require.resolve('react-native/package.json')"].execute(null, rootDir).text.trim()).parentFile
// Override autolinking resolution path
```

### Step 3 — `gradle.properties`

```properties
newArchEnabled=true          # enables TurboModules + Fabric
hermesEnabled=true           # Hermes JS engine
android.useAndroidX=true
org.gradle.jvmargs=-Xmx4096m
```

### Step 4 — `AndroidManifest.xml` permissions needed from Day 1

```xml
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
<!-- For Phase 2 -->
<uses-permission android:name="android.permission.BIND_ACCESSIBILITY_SERVICE"/>
```

### Step 5 — `MainApplication.kt` with New Architecture enabled

```kotlin
class MainApplication : Application(), ReactApplication {
    override val reactNativeHost = DefaultReactNativeHost(this) {
        isNewArchEnabled = BuildConfig.IS_NEW_ARCHITECTURE_ENABLED
        isHermesEnabled = BuildConfig.IS_HERMES_ENABLED
        getPackages() = listOf(AgentCorePackage())   // our TurboModule
    }
}
```

### Step 6 — First placeholder TurboModule (so bridge compiles)

```kotlin
// android/bridge/turbo/AgentCoreModule.kt
class AgentCoreModule(ctx: ReactApplicationContext) :
    ReactContextBaseJavaModule(ctx), TurboModule {

    override fun getName() = "AgentCore"

    @ReactMethod
    fun checkModelReady(promise: Promise) {
        promise.resolve(false)   // Phase 1 will replace with real check
    }

    @ReactMethod
    fun getAgentStatus(promise: Promise) {
        val map = WritableNativeMap()
        map.putString("phase", "phase0_kotlin_init")
        map.putBoolean("modelReady", false)
        promise.resolve(map)
    }
}
```

---

## Acceptance Criteria

- [ ] `android/` directory exists with full structure listed above
- [ ] `./gradlew assembleDebug` completes without errors from `android/` root
- [ ] `newArchEnabled=true` is set and codegen runs during build
- [ ] `AgentCoreModule.kt` compiles and is registered in `ReactPackage`
- [ ] Connecting the Expo app to the Android project: `AgentCoreBridge.ts` `checkModelReady()` call reaches Kotlin and returns `false` (not stub)
- [ ] No `node_modules` path errors from Gradle autolinking

---

## Blockers

None currently. This task is unblocked and ready to start.

---

## Important Constraints (Don't Forget These)

1. **JS never calls System Control layer directly.** Only Kotlin calls `AccessibilityService`, `MediaProjection`, `GestureEngine`. JS only calls TurboModule methods.
2. **No logic in JS.** Even simple things like "is the model ready?" must be answered by Kotlin, not computed in JS.
3. **No cloud. Ever.** The model download from HuggingFace is the only network call in the entire app. After that, everything is on-device.
4. **Training only during charging + idle.** Never during active inference. Thermal guard is not optional.
5. **Model is NOT bundled.** It is downloaded at runtime. The APK must stay under 50MB.

---

## The Full Learning Pipeline (Summary — Why This All Matters)

```
Day 1:  Base Llama 3.2-1B loaded (Meta pre-trained, general reasoning)
        No fine-tuning, no RL policy, no personal data yet

First tasks:  Agent explores with base model guidance
              Every (screen, action, result, reward) tuple saved to SQLite
              IRL module watches YouTube if user plays/watches anything

First idle charging:  
              → Policy network: REINFORCE update on accumulated tuples
              → IRL: extract expert sequences from video frames
              → LoRA: fine-tune LLM on successful task traces
              → Edge cases: index unusual situations for future recall

Week 1:  Agent knows this phone's apps, this user's task patterns
         Handles edge cases it's seen before
         Games: policy network gets faster and more accurate

Month 1:  LoRA adapter makes LLM fluent in this user's language and app set
          Policy network competitive at simple mobile games
          Agent suggests actions before user even asks

All on-device. No cloud. No external server. The M31 is the entire AI system.
```

---

## Next Task (after Phase 0 complete)

**Phase 1 — Task 1.2: Model Download Service**

Build `ModelDownloadService.kt` — the foreground service that downloads the 870MB GGUF from HuggingFace with resume support, progress notifications, and SHA256 verification. Wire to JS download screen via TurboModule events.

See `DEVELOPMENT_ROADMAP.md` → Phase 1 → 1.2 Model Download.
