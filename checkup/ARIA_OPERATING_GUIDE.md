# ARIA Agent — Operating Guide

> **Target device:** Samsung Galaxy M31 · Exynos 9611 · 6 GB RAM · Android 11+  
> **Model:** Llama 3.2-1B-Instruct · Q4_K_M quantization · ~870 MB on disk  
> **Architecture:** React Native (Expo) UI · All logic in pure Kotlin · JNI (llama.cpp)

---

## Table of Contents

1. [What ARIA Is](#1-what-aria-is)
2. [First-Time Setup](#2-first-time-setup)
3. [UI Screens — Complete Walkthrough](#3-ui-screens--complete-walkthrough)
4. [How to Run ARIA — General Mode](#4-how-to-run-aria--general-mode)
5. [How to Run ARIA — Gaming Mode](#5-how-to-run-aria--gaming-mode)
6. [How to Run ARIA — Learn-Only Mode](#6-how-to-run-aria--learn-only-mode)
7. [Task Queue & Chaining](#7-task-queue--chaining)
8. [Object Labeler — Teaching ARIA Your Screens](#8-object-labeler--teaching-aria-your-screens)
9. [Training ARIA — RL & IRL](#9-training-aria--rl--irl)
10. [All Modules Explained](#10-all-modules-explained)
11. [Thermal & Operating Conditions](#11-thermal--operating-conditions)
12. [Web Dashboard](#12-web-dashboard)
13. [Settings Reference](#13-settings-reference)
14. [Troubleshooting](#14-troubleshooting)

---

## 1. What ARIA Is

ARIA (Autonomous Reactive Intelligence Agent) is a fully on-device AI agent for Android. It watches your screen, thinks with a local language model, and taps/swipes/types on your behalf to complete goals you give it — with zero cloud or internet dependency.

**Core loop (runs entirely in Kotlin, never in JS):**

```
OBSERVE screen → RETRIEVE memory → REASON with LLM → PARSE action → ACT → EVALUATE → STORE → repeat
```

Every step happens in Kotlin. The React Native UI is display-only — it shows status and lets you send commands, but all intelligence runs natively.

---

## 2. First-Time Setup

### Step 1 — Grant Permissions

Open the app. Go to the **Settings tab** (gear icon). You will see three permission cards:

| Permission | Why It's Required |
|------------|-------------------|
| **Accessibility Service** | Lets ARIA read UI element names, positions, and states without screen capture |
| **Screen Capture** | Lets ARIA see apps that block the accessibility tree (games, browsers, Flutter apps) |
| **Notifications** | Shows foreground service notification while ARIA is running |

Tap each **"Open Settings"** button. Enable ARIA in the respective Android system screen. Return to the app — each card turns green when granted.

> **Without Accessibility**, ARIA cannot see or interact with any app.  
> **Without Screen Capture**, ARIA is blind inside games and custom-rendered apps.

### Step 2 — Download the Model

Go to the **Modules tab** → LLM card → tap **"Download Model"**. The app downloads `Llama-3.2-1B-Instruct-Q4_K_M.gguf` (~870 MB) over HTTPS. A progress bar shows percent and speed. The file is saved to internal storage — no SD card or external permission needed.

If you already have the model file on the device, the download is skipped automatically.

### Step 3 — Load the Model into RAM

After download, tap **"Load Model"** (or go to the **Control tab** and tap the Load button). LlamaEngine maps the GGUF file into RAM using `mmap` — this takes 3–8 seconds on the M31. When done, the LLM card shows a green dot.

### Step 4 — MiniLM Embedding Model

The first time you run the agent, it automatically downloads `minilm-l6-v2.onnx` (~23 MB) in the background. This enables semantic memory — ARIA can retrieve past experiences similar to the current screen. No action needed from you.

---

## 3. UI Screens — Complete Walkthrough

The app has **7 tabs** at the bottom and one overlay screen (Labeler).

### Tab 1 — Dashboard (Home)

**What you see:**
- **Status banner** — large coloured badge: `Agent Idle` / `Agent Running` / `Agent Paused` / `Error`
- **Status dot** — live pulsing indicator matching the banner
- **Metric cards** — Token rate (tok/s), RAM used (MB), Session uptime, Actions performed, Success rate (%)
- **Module rows** — quick-glance health of LLM, OCR, RL, Memory, Accessibility, Screen Capture
- **Thermal banner** — appears in yellow/red if device is getting hot
- Pull-to-refresh updates all values

**What to do here:**
- Check at a glance that all modules are green before starting a task
- Monitor performance during a running session
- Spot thermal warnings early and navigate away from training

---

### Tab 2 — Control

**What you see:**
- **Goal input** — text field where you type what you want ARIA to do
- **Preset buttons** — 5 example goals (YouTube trending, Gmail summary, Maps coffee, DND, Chrome news)
- **Learn-Only toggle** — when ON, ARIA observes and records but does not tap
- **Start / Pause / Stop buttons**
- **Load Model button** — if LLM is not yet loaded
- **Task Queue section** — pending chained tasks, with swipe-to-delete
- **Queue a Task section** — add goals with app package and priority

**How to use it:**
1. Type a goal (e.g. `"Find the top post on Reddit"`) or tap a preset
2. Optionally toggle **Learn-Only** if you want ARIA to watch without acting
3. Tap **Start** — haptic feedback confirms the command is sent
4. The status banner on the Dashboard tab updates in real time
5. Tap **Pause** to freeze mid-task (ARIA holds its current state)
6. Tap **Stop** to cancel and return to idle

---

### Tab 3 — Chat

**What you see:**
- Chat bubbles — your messages on the right (blue), ARIA responses on the left (dark surface)
- System messages — small centered grey text for status events
- Text input at the bottom + Send button

**What you can do:**
- Ask ARIA anything in plain text: `"What app are you in?"`, `"What's your last action?"`
- Give a goal via chat: `"Open Spotify and play my liked songs"`
- Ask about its memory: `"Have you done this before?"`

**How it works internally:**  
Your message goes through `ChatContextBuilder.kt` which assembles a full prompt including: current agent state, task queue contents, recent app skills, and progress context. This single Kotlin call replaces 4+ separate bridge calls. The LLM generates a response streamed token-by-token — you see characters appearing live.

---

### Tab 4 — Logs

**What you see:**
Two sub-tabs: **Actions** and **Memory**

**Actions tab:**
- Every action ARIA has taken: tap, swipe, type, scroll, intent, observe
- Each row shows: action type icon, description, app package, timestamp, success (green left border) or failure (red left border), reward signal
- Filter buttons: All / Success / Failure

**Memory tab:**
- Embedding entries stored in SQLite from past sessions
- Shows screen context, stored action, reward value, app package
- These are the tuples used to train RL and to retrieve similar past experiences

---

### Tab 5 — Modules

**What you see — one card per subsystem:**

| Card | Shows |
|------|-------|
| **LLM** | Model name, quantization, context length, tok/s, RAM MB, loaded state |
| **OCR** | ML Kit engine version, ready state |
| **RL / Learning** | LoRA version, adapter loaded, untrained samples, Adam step, last loss |
| **Memory** | Embedding count, DB size on disk, ready state |
| **Accessibility** | Granted / active state |
| **Screen Capture** | Granted / active state |
| **App Skills** | List of per-app knowledge profiles ARIA has built |

**Actions available:**
- Download or load model from LLM card
- Download MiniLM from Memory card
- Tap any App Skill row to see the JSON knowledge profile

---

### Tab 6 — Train

**What you see — three sections:**

**IRL Video Training:**
- Pick a video from your gallery (a screen recording of you doing a task)
- Set the goal label (e.g. `"Find Trending page"`) and target app package
- Tap **Start IRL Training** — ARIA processes each frame with OCR + accessibility to extract (state → action) tuples and stores them as training data

**RL Cycle:**
- Shows current untrained sample count, LoRA version, policy readiness
- Tap **Run RL Cycle** to trigger on-device REINFORCE training now (normally this runs automatically when idle + charging)
- After training: new LoRA version number, new adapter path, samples used

**Object Labeler shortcut:**
- Shortcut card to jump to the full Labeler screen

---

### Tab 7 — Settings

**What you see:**

**Permissions section:** Accessibility, Screen Capture, Notifications — each with status badge and Open Settings button.

**Model Config:**
| Setting | Options | Effect |
|---------|---------|--------|
| Quantization | Q4_K_M, Q4_0, IQ2_S, Q5_K_M | Q4_K_M is best balance for M31 |
| Context Window | 512 / 1024 / 2048 / 4096 | Larger = more memory, slower |
| Max tokens/turn | Slider | How many tokens per inference call |
| Temperature ×100 | Slider (0–200) | Higher = more creative / less stable |
| GPU layers | 0 / 8 / 16 / 24 / 32 | 32 = full Vulkan offload on Mali-G72 |
| RL enabled | Toggle | Enables experience collection and training |
| LoRA adapter path | Text | Path to active fine-tuned adapter |

**Local Server:**
- Start/Stop the HTTP server (`http://{device-ip}:8765`)
- Shows device IP and full server URL
- Copy URL button — paste into the web dashboard to monitor remotely

Tap **Save Config** after any change. Config persists across restarts.

---

### Overlay Screen — Object Labeler

Accessible from the Train tab shortcut or from the Modules → App Skills section.

**What you see:**
- Full-screen screenshot of the current foreground app
- Annotation pins placed over UI elements
- Editor panel below: Name, Context description, Element type selector

**Step-by-step workflow:**
1. Tap **Capture Screen** — grabs a screenshot of whatever app is in the foreground
2. Tap **Auto-detect** (optional) — runs the object detector model to auto-place pins on visible UI elements
3. Tap anywhere on the image to place a pin manually
4. Fill in: **Name** (e.g. `"Play button"`), **Context** (e.g. `"Starts video playback"`), **Element type**
5. Tap **Enrich All** — the LLM reads every pin and generates: interaction hints, reasoning context, semantic description. This takes ~30 seconds
6. Tap **Save** — all pins are stored in SQLite, keyed by app package + screen hash

**Effect on ARIA:** From now on, whenever ARIA sees this screen, it injects all your labels directly into its system prompt. It knows the names, purposes, and interactions of every element you annotated — dramatically improving task success on that screen.

---

## 4. How to Run ARIA — General Mode

This is the standard autonomous mode for everyday app tasks.

**Before starting:**
- LLM loaded (green dot on Modules tab)
- Accessibility granted
- Screen Capture granted
- Device not thermally throttled (no thermal banner on Dashboard)

**Running a task:**

1. Go to **Control tab**
2. Type your goal or tap a preset. Goals work best when they are:
   - Specific: `"Open YouTube, go to Trending, play the first video"` ✓
   - Not too open-ended: `"Do something useful"` ✗
3. Leave **Learn-Only** OFF for full autonomous mode
4. Tap **Start**

**What happens internally (every ~3–10 seconds per step):**

```
OBSERVE:  ScreenObserver reads accessibility tree + runs OCR on screenshot
RETRIEVE: EmbeddingEngine searches memory for similar past screens
REASON:   Prompt assembled → LlamaEngine.infer() → 80–200 tokens generated
PARSE:    AgentLoop extracts JSON action { "tool": "tap", "nodeId": "#4" }
ACT:      GestureEngine injects accessibility gesture or coordinate tap
EVALUATE: Screen settles → reward assigned (success +1.0, failure -0.5)
STORE:    ExperienceTuple saved to SQLite via ExperienceStore
EMIT:     JS bridge updated: action log, status, token rate
```

**Stopping gracefully:** Tap **Stop** on the Control tab. ARIA finishes its current action, stores the result, then halts.

**Max steps safety cap:** If ARIA hasn't declared success after 50 steps (default), it stops automatically to prevent infinite loops.

---

## 5. How to Run ARIA — Gaming Mode

Gaming mode activates automatically when ARIA detects you are in a game app.

### How detection works — `GameDetector.kt`

When AgentLoop observes a screen, it checks:
1. Is the accessibility tree empty or nearly empty? (Games render directly to GL/Vulkan surface)
2. Is the app in the known game package list?
3. Is the screen render rate high? (Games run at 30–60 FPS, accessibility events are sparse)

If two or more conditions are true → **GameDetector** flags the session as a game.

### What changes in game mode

| Normal Mode | Gaming Mode |
|-------------|-------------|
| LLM reasons each step (~5 sec/step) | **PolicyNetwork** selects actions instantly (<1ms) |
| Accessibility tree used for UI elements | Pure coordinate taps + pixel verification |
| ExperienceStore records (state→action) pairs | Game episode reward accumulated across the full episode |
| LoRA adapter enriches LLM | REINFORCE updates PolicyNetwork weights after each episode |

### PolicyNetwork — the game brain

A lightweight 3-layer MLP (Multi-Layer Perceptron):
- **Input:** 256 floats = 128-dim screen embedding + 128-dim goal embedding
- **Hidden:** 256 → 128 neurons with ReLU activation
- **Output:** 7 action probabilities (softmax) → pick highest

**7 actions in game mode:**
| ID | Action |
|----|--------|
| 0 | Tap at center / detected target |
| 1 | Swipe up |
| 2 | Swipe down |
| 3 | Swipe right |
| 4 | Swipe left |
| 5 | Type text |
| 6 | Back button |

### PixelVerifier

In games, after each tap ARIA checks a grid of pixels around the tap target before and after the gesture. If pixels changed → the tap registered → positive signal. If no change → negative signal. This gives reward without needing OCR or accessibility.

### Game Loop — `GameLoop.kt`

Runs at configurable FPS (default: 1 frame/sec for power efficiency). Each tick:
1. Capture bitmap
2. Run EmbeddingEngine to get 128-dim screen vector
3. PolicyNetwork selects action
4. GestureEngine executes action
5. Wait for next frame
6. PixelVerifier computes reward
7. Accumulate (state, action, reward) for REINFORCE update at episode end

When you navigate away from the game (screen changes to a non-game app), GameLoop stops automatically and GameDetector resets.

---

## 6. How to Run ARIA — Learn-Only Mode

Enable the **Learn-Only** toggle on the Control tab before starting.

In Learn-Only mode:
- ARIA observes every screen normally (Observe → Retrieve → Reason)
- It **does not execute any gesture** — GestureEngine is bypassed
- Every action it *would* have taken is stored as an experience tuple with a neutral reward
- The LLM still runs inference so you get token rate data

**Use this when you want to:**
- Teach ARIA a new workflow by doing it yourself while ARIA watches
- Build up experience data without risk of ARIA tapping the wrong thing
- Run alongside a sensitive task (banking, work email) where you stay in control

After a learn-only session, the collected tuples appear in the **Logs → Memory** tab and count towards the next RL cycle.

---

## 7. Task Queue & Chaining

ARIA can execute multiple goals sequentially without any user interaction between them.

**How to queue a task from the Control tab:**
1. Scroll down to the **Queue a Task** section
2. Enter the goal, optionally the target app package, and priority (0 = highest)
3. Tap **Enqueue**

The task appears in the **Task Queue** list above. You can see its goal, app, and priority. Swipe left to delete individual tasks. Tap **Clear All** to empty the queue.

**When the current task finishes** (success, failure, or max-steps hit), AgentLoop automatically calls `TaskQueueManager.dequeue()` and starts the next task. A notification banner appears briefly on the Control tab: `"Starting next task: [goal]"`.

**Priority:** Tasks with lower priority number run first. Within the same priority, tasks run in the order they were enqueued (FIFO).

**Persistence:** The queue is saved to `filesDir/aria_task_queue.json` — it survives app kills, reboots, and Android LMK (low memory killer) events.

---

## 8. Object Labeler — Teaching ARIA Your Screens

The Object Labeler is your most powerful tool for improving ARIA on apps you use frequently.

**The flow:**

```
Capture screenshot → Place pins → [Auto-detect] → Edit pins → Enrich with LLM → Save
```

**What "Enrich All" does:**  
For each pin you placed, the local LLM generates:
- `interaction_hint` — what happens when you tap this element
- `reasoning_context` — why this element matters for agent goals
- A semantic re-description of the element

**What ARIA does with your labels:**  
Every time AgentLoop observes a screen with saved labels, `ObjectLabelStore` looks up labels by `(appPackage + screenHash)`. Matching labels are injected into the system prompt at inference time:

```
[SCREEN LABELS]
#1 "Play button" — starts video. Tap to begin playback. Relevant when goal involves watching content.
#2 "Search bar" — navigates to search. Tap then type query. Use when content discovery needed.
```

This gives ARIA human-level understanding of your most-used screens before it even tries a goal.

---

## 9. Training ARIA — RL & IRL

ARIA learns from two sources and trains in two ways.

### Training Data Sources

| Source | What It Is | Quality |
|--------|-----------|---------|
| **ExperienceStore** | (state, action) pairs from agent operation — successful steps | Medium |
| **ObjectLabelStore** | Human-annotated screen labels, LLM-enriched | High — counts 3× in training |
| **IRL Video** | Screen recordings of you doing tasks — frames extracted automatically | Medium-High |

### Automatic Training — LearningScheduler

Training runs automatically when:
- Device is **charging** (plugged in)
- Thermal level is **LIGHT or lower** (cool enough)
- Agent is **idle** (not running a task)

You don't need to do anything. LearningScheduler wakes up every 30 minutes to check these conditions. If all three are met, it runs an RL cycle and saves the new adapter.

### Manual Training — Train Tab

**IRL Video Training:**
1. Record yourself doing a task on your phone (Android screen recorder, built-in or third-party)
2. Open Train tab → IRL Video section
3. Tap **Pick Video** → select your recording
4. Enter the goal label and app package
5. Tap **Start IRL Training**
6. Wait — ARIA processes every frame: OCR + accessibility → extracts actions → stores tuples. Shows progress.
7. Result card shows frames processed, tuples extracted, LLM-assisted count

**RL Cycle:**
1. Open Train tab → RL Cycle section
2. Check "Untrained samples" — should be at least 50 for useful training
3. Tap **Run RL Cycle**
4. Wait ~2–5 minutes. Shows new LoRA version, samples used, success
5. The new adapter is loaded automatically for future inference

### What RL Training Actually Does

**For the LLM (LoRA):**  
LoraTrainer takes successful (state, action) pairs and runs gradient descent on two low-rank matrices (B and A, rank=4). These are added to the frozen Llama 3.2-1B weights: `W = W₀ + BA`. The resulting adapter (`adapter_vN.bin`, ~15 MB) is loaded by LlamaEngine for future sessions.

**For the Policy Network:**  
PolicyNetwork runs REINFORCE — a policy gradient method. For each (state, action, reward) tuple from a game episode, it computes the gradient `G_t × ∇ log π(a|s)` and applies an Adam step. The updated weights go to `rl/policy_latest.bin`.

---

## 10. All Modules Explained

### AgentLoop — The Central Brain
**File:** `core/agent/AgentLoop.kt`  
The main coroutine loop. Runs Observe → Retrieve → Reason → Parse → Act → Evaluate → Store on every step. Stops on `{"tool":"Done"}` from LLM, on `stop()` call, or after 50 steps. Emits events to JS for live UI updates.

### LlamaEngine — On-Device LLM
**File:** `core/ai/LlamaEngine.kt`  
JNI wrapper around llama.cpp. Uses `mmap=true` to keep RAM at ~1.7 GB instead of 2.5 GB. Runs 4 threads on Cortex-A73 big cores. Streams tokens via callback. Falls back to a stub JSON response if the native `.so` is not compiled yet (dev mode).

### ScreenObserver — Perception Fusion
**File:** `core/perception/ScreenObserver.kt`  
Combines accessibility tree, ML Kit OCR, and screen bitmap into one text summary. Assigns stable `#N` IDs to each element so the LLM can reference them by number. Falls back to pure bitmap when the accessibility tree is empty.

### OcrEngine — Text on Screen
**File:** `core/ocr/OcrEngine.kt`  
ML Kit Text Recognition v2. Runs on-device, no network. Extracts all visible text from a bitmap. Used both in ScreenObserver (for normal apps) and in IrlModule (for video frame processing).

### GestureEngine — Physical Actions
**File:** `system/actions/GestureEngine.kt`  
Dispatches accessibility gestures (tap, swipe, scroll, type) by injecting them into the Android input system via `AccessibilityService.dispatchGesture()`. For coordinate-based taps (games), uses `performGlobalAction()`. Supports: tap, swipe-up/down/left/right, type text, scroll, send intent, press back.

### AgentAccessibilityService — Eyes on the UI
**File:** `system/accessibility/AgentAccessibilityService.kt`  
Always-on Android accessibility service. Provides the UI element tree to ScreenObserver and the gesture injection pipeline to GestureEngine. Runs in a foreground service — visible in the notification bar.

### ScreenCaptureService — Camera for Games
**File:** `system/ScreenCaptureService.kt`  
Android MediaProjection-based screen capture. Required for games and other apps that block the accessibility tree. Captures ~1–2 FPS in normal mode, throttles to 0.5 FPS when thermal level is LIGHT or above.

### EmbeddingEngine — Semantic Memory Search
**File:** `core/memory/EmbeddingEngine.kt`  
Runs MiniLM-L6-v2 (ONNX, ~23 MB) to convert screen observations into 128-dimensional vectors. Used to find similar past experiences (cosine similarity search in ExperienceStore). Falls back to a hash-based approximation if ONNX model is not downloaded yet.

### ExperienceStore — Long-Term Memory
**File:** `core/memory/ExperienceStore.kt`  
SQLite database of (screen_summary, action_json, reward, embedding) tuples. Survives restarts. Searched by embedding similarity every step to retrieve relevant past experiences. Training data source for RL cycles.

### ObjectLabelStore — Screen Knowledge
**File:** `core/memory/ObjectLabelStore.kt`  
SQLite database of human-annotated UI element labels, keyed by `(appPackage, screenHash)`. Queried by AgentLoop every step. Matching labels are injected into the LLM prompt as expert screen context.

### PolicyNetwork — Game Action Selector
**File:** `core/rl/PolicyNetwork.kt`  
3-layer MLP (256→256→128→7). Selects game actions in <1ms without LLM. Trained by REINFORCE + Adam. Uses NEON SIMD math via JNI when available, Kotlin scalar math otherwise.

### LoraTrainer — Fine-Tuning Engine
**File:** `core/rl/LoraTrainer.kt`  
On-device LoRA fine-tuning. Rank-4 matrices added to LLaMA weight layers. Combines ExperienceStore tuples (1× weight) and ObjectLabelStore annotations (3× weight) into a single JSONL dataset. Saves adapter to `filesDir/lora/adapter_vN.bin`. Stubs to a metadata-only file in dev mode.

### LearningScheduler — Training Gatekeeper
**File:** `core/rl/LearningScheduler.kt`  
Checks charging status, thermal level, and agent idle state every 30 minutes. Only triggers training when all three are safe. Also runs battery temperature polling fallback for API < 29 devices.

### LlmRewardEnricher — Smarter Rewards
**File:** `core/rl/LlmRewardEnricher.kt`  
After an action, if the raw pixel/accessibility reward is ambiguous, asks the LLM: "Did this action advance the goal?". The LLM's answer (yes/partial/no) replaces the raw reward signal. Makes RL training higher quality.

### ThermalGuard — Heat Protection
**File:** `core/system/ThermalGuard.kt`  
Monitors `PowerManager.ThermalStatus` (API 29+) or battery temperature (API < 29). Five levels: SAFE → LIGHT → MODERATE → SEVERE → CRITICAL. At each level, different subsystems are throttled or stopped (see Section 11).

### TaskQueueManager — Multi-Goal Chaining
**File:** `core/agent/TaskQueueManager.kt`  
JSON-file-backed FIFO queue with priority support. Persists across restarts. AgentLoop auto-dequeues and starts the next task when the current one completes.

### AppSkillRegistry — Per-App Knowledge
**File:** `core/agent/AppSkillRegistry.kt`  
Stores a JSON knowledge profile for each app ARIA has operated. Includes known UI patterns, common failure modes, and successful action sequences. Loaded into the prompt when ARIA opens an app it has visited before.

### ProgressPersistence — Self-Referential Memory
**File:** `core/persistence/ProgressPersistence.kt`  
Two files: `progress.txt` (append-only action log) and `goals.json` (sub-task completion state). ARIA reads these at the start of every task to know what it already tried. Survives Android LMK kills. Prevents infinite retry loops.

### ChatContextBuilder — Efficient Bridge
**File:** `core/ai/ChatContextBuilder.kt`  
Assembles the full chat prompt in Kotlin in a single bridge call: agent state + memory + task queue + app skills + progress context. Reduces JS→Kotlin bridge round-trips from 4+ to 1.

### GameDetector — Game Recognition
**File:** `core/perception/GameDetector.kt`  
Detects game apps by checking: empty accessibility tree, known game packages, high frame rate patterns. Triggers GameLoop mode when confirmed.

### GameLoop — Game Execution Engine
**File:** `core/rl/GameLoop.kt`  
Runs at 1 FPS by default. Captures bitmap → embeds → PolicyNetwork selects action → GestureEngine acts → PixelVerifier rewards → accumulate episode → REINFORCE update at episode end.

### PixelVerifier — Game Reward Signal
**File:** `core/system/PixelVerifier.kt`  
Samples a grid of pixels before and after a game action. Pixel change rate = reward signal. Prevents reward hacking by checking specific regions relevant to the game goal.

### SustainedPerformanceManager — Throttle Control
**File:** `core/system/SustainedPerformanceManager.kt`  
Calls `Window.setSustainedPerformanceMode()` during training to lower max CPU clock. This reduces heat generation during LoRA training at the cost of some throughput.

### ModelManager — Model Lifecycle
**File:** `core/ai/ModelManager.kt`  
Manages model path, quantization config, and loading state. Decides whether the model file exists, needs download, or is ready to load. Interface between Settings UI and LlamaEngine.

### ModelBootstrap & ModelDownloadService
**Files:** `core/ai/ModelBootstrap.kt`, `services/ModelDownloadService.kt`  
ModelBootstrap runs on first launch to check model readiness. ModelDownloadService handles HTTPS download of the GGUF file with progress reporting via DeviceEventEmitter to JS.

### LocalDeviceServer — Web Dashboard Backend
**File:** `core/monitoring/LocalDeviceServer.kt`  
Starts a lightweight HTTP server on port 8765. Serves `/aria/status`, `/aria/thermal`, `/aria/rl`, `/aria/lora`, `/aria/memory`, `/aria/activity`, `/aria/modules` as JSON. The web dashboard connects to this server over local Wi-Fi.

### MonitoringPusher — Event Bridge to Server
**File:** `core/monitoring/MonitoringPusher.kt`  
Subscribes to AgentEventBus and updates LocalSnapshotStore on every significant event. The server reads snapshots to serve live data to the web dashboard.

### PromptBuilder — LLM Prompt Assembly
**File:** `core/ai/PromptBuilder.kt`  
Constructs the system prompt for each inference call. Includes: current goal, screen observation, retrieved memories, object labels, progress context, app skill profile. Keeps total tokens within the configured context window.

### IrlModule — Video Learning
**File:** `core/rl/IrlModule.kt`  
Extracts training frames from a video file. For each frame: runs OCR + accessibility to understand the screen state; computes what action occurred (by comparing consecutive frames); stores the (state, action) tuple. LLM enriches ambiguous transitions.

---

## 11. Thermal & Operating Conditions

The M31's Exynos 9611 has no NPU and heats significantly under sustained AI load. ThermalGuard protects the device with five levels:

| Level | Temperature | What ARIA Does |
|-------|-------------|----------------|
| **SAFE** | < 35°C | Full speed — all operations permitted |
| **LIGHT** | 35–37°C | Screen capture throttled to 0.5 FPS |
| **MODERATE** | 37–40°C | RL training paused; capture stays at 0.5 FPS |
| **SEVERE** | 40–42°C | LLM inference paused; user notified via banner |
| **CRITICAL** | > 42°C | Everything stopped; agent returns to idle |

**Thermal banner on Dashboard:** Shows at LIGHT or above. Shows exact level and what is paused.

**Best practice:**
- Run heavy tasks (IRL training, RL cycles) only when the phone is plugged in
- If the Dashboard shows SEVERE, let the phone cool for 5–10 minutes before continuing
- The S-Mode power profiles (Sustained Performance Mode) are engaged during training to prevent aggressive thermal spikes

**Battery conditions:**
- **Charging + idle + cool:** LearningScheduler triggers automatic RL + LoRA cycle
- **Discharging + hot:** Training is blocked; agent-only inference may continue if MODERATE or below
- **Low battery (<15%):** LearningScheduler does not train. Agent can still run tasks

**Background kill (Android LMK):**  
If Android kills the foreground service under memory pressure, ProgressPersistence ensures ARIA can resume from the exact sub-task it was on. The task queue persists too — nothing is lost.

---

## 12. Web Dashboard

Connect a PC or tablet on the same Wi-Fi network to monitor ARIA in real time.

**Setup:**
1. In the app → Settings tab → start the local server
2. Copy the URL shown (e.g. `http://192.168.1.42:8765`)
3. On your PC, open the web dashboard and paste the URL into the "Connect Device" field in the top-right corner
4. Click **Connect** — the dashboard pings `/health` to confirm the connection

**Dashboard pages:**

| Page | Shows |
|------|-------|
| **Overview** | Agent status, LLM stats, thermal level, module health, permissions |
| **Activity Log** | Live stream of every action with type, app, success, reward |
| **RL Metrics** | Episode count, LoRA version, loss history chart, reward history chart |
| **LoRA Versions** | All adapter versions with training date, samples used, success delta |
| **Memory Store** | Embedding count, DB size, edge case count, MiniLM status |

If the device is not reachable (Wi-Fi off, wrong IP, app not running), the dashboard automatically falls back to static mock data so the UI still loads.

---

## 13. Settings Reference

| Setting | Default | Notes |
|---------|---------|-------|
| Quantization | Q4_K_M | Best balance of quality and RAM for M31 |
| Context window | 4096 | Reduce to 2048 if getting OOM |
| Max tokens/turn | 256 | Higher = longer LLM thoughts, slower |
| Temperature ×100 | 80 | 70–90 works well; above 120 = unreliable |
| GPU layers | 32 | 32 = all layers on Mali-G72 via Vulkan |
| RL enabled | ON | Turn OFF only for debugging |
| LoRA adapter path | auto | Set manually if you have a custom adapter |

---

## 14. Troubleshooting

**ARIA keeps tapping the wrong element**
→ Use the Object Labeler to annotate that screen. Label the key elements so ARIA has expert knowledge injected into its prompt.

**Agent is stuck in a loop**
→ The max-steps cap (50) will stop it automatically. If you need it to stop sooner, tap **Stop**. Then check the Logs tab to see which steps failed repeatedly — use the Object Labeler to teach ARIA what those elements do.

**LLM not loaded / model not found**
→ Go to Modules tab → LLM card → tap **Download Model** then **Load Model**. If download fails, check Wi-Fi connection. The file is ~870 MB.

**Accessibility not working after granting**
→ Return to Settings tab and force-refresh. Some Android skins (One UI, MIUI) require a reboot after enabling accessibility for a new app. Reboot and try again.

**Screen Capture permission keeps resetting**
→ This is an Android 14+ behaviour. The MediaProjection token expires when the app is killed. ARIA re-requests it automatically on the next task start — you may see a system permission dialog appear before the task begins.

**High RAM warning (memoryUsedMb > 4000)**
→ Reduce context window from 4096 to 2048 in Settings. Reducing GPU layers (32→16) also helps slightly. Avoid running background apps while ARIA is active.

**Thermal SEVERE / CRITICAL**
→ Stop any current task. Remove the phone from its case. Let it rest for 10 minutes. If training was running, it was paused automatically and will resume when cool.

**RL cycle fails with "not enough samples"**
→ Need at least 50 untrained samples. Run ARIA on several tasks to collect more experience, or do an IRL video training session to inject tuples. Check the Train tab → RL Cycle → "Untrained samples" count.

**Web dashboard shows mock data / can't connect**
→ Ensure both devices are on the same Wi-Fi network. Check the IP address in Settings — it should be a `192.168.x.x` address. Make sure the local server is started (green dot in Settings). Firewall or guest Wi-Fi networks that block device-to-device traffic will prevent connection.

---

*ARIA Agent — Built for Samsung Galaxy M31 · No cloud · No tracking · Fully on-device*
