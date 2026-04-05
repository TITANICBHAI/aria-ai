package com.ariaagent.mobile.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ariaagent.mobile.ui.theme.ARIAColors

/**
 * KnowledgeWizardScreen — a self-contained "encyclopedia" of how ARIA works.
 *
 * This is NOT a how-to wizard — it explains the underlying concepts behind the app:
 * what the AI engine actually does, how on-device learning works, what's real vs stub,
 * and what each component contributes.
 *
 * Accessible from Settings → "Knowledge Base" nav card.
 * Full-screen, no bottom nav bar.
 *
 * Pages:
 *   0  What is ARIA?
 *   1  The AI Engine (Llama 3.2-1B)
 *   2  Observe → Think → Act
 *   3  Vision (SmolVLM-256M)
 *   4  Memory & Experience
 *   5  On-Device Learning (RL + LoRA)
 *   6  Your Privacy
 *   7  What Needs the NDK Build
 *   8  The Permissions & Why
 *   9  Tips for Best Results
 *
 *  ── TRAINING MANUAL ──
 *  10  Training ARIA From Zero (overview)
 *  11  Mode 1: REINFORCE (Auto-Learning)
 *  12  Mode 2: IRL (Imitation from Video)
 *  13  Mode 3: Manual Labeling
 *  14  Mode 4: LoRA Fine-Tuning
 *  15  Training Recipes (combined examples)
 */

private data class KnowledgePage(
    val icon: ImageVector,
    val iconTint: Color,
    val title: String,
    val body: String,
    val bullets: List<Pair<ImageVector, String>> = emptyList(),
    val callout: String? = null,
    val calloutColor: Color = ARIAColors.Primary,
)

@Composable
fun KnowledgeWizardScreen(onBack: () -> Unit) {

    val pages: List<KnowledgePage> = listOf(
        KnowledgePage(
            icon      = Icons.Default.SmartToy,
            iconTint  = ARIAColors.Primary,
            title     = "What Is ARIA?",
            body      = "ARIA stands for AI Runtime Interface for Android. It is an autonomous agent that runs entirely on your device — no cloud servers, no subscriptions, no accounts.\n\nUnlike a chatbot, ARIA can open apps, tap buttons, type text, scroll, and complete multi-step tasks on your behalf — all decided by a local large language model (LLM) that runs in RAM.",
            bullets   = listOf(
                Icons.Default.PhoneAndroid to "100 % on-device — nothing leaves your phone",
                Icons.Default.AutoAwesome  to "Autonomous — decides what to do next, not just what to say",
                Icons.Default.Lock         to "Private by design — no account, no API key required",
                Icons.Default.FitnessCenter to "Self-improving — gets smarter with every task it runs",
            ),
            callout   = "ARIA is not a chatbot. It acts on your phone just like a human would — seeing the screen, reading text, and tapping the right things.",
        ),

        KnowledgePage(
            icon      = Icons.Default.Memory,
            iconTint  = ARIAColors.Accent,
            title     = "The AI Engine: Llama 3.2-1B",
            body      = "ARIA's decisions come from Llama 3.2-1B-Instruct — a 1-billion-parameter language model from Meta, quantised to Q4_K_M format (~700 MB) so it fits on a mid-range Android phone.\n\nInference is handled by llama.cpp, a highly-optimised C++ runtime. On a Samsung Galaxy M31 (Exynos 9611), ARIA targets 8–15 tokens per second using 4 CPU cores plus partial GPU offload via Vulkan.\n\nThe model receives a structured prompt describing the screen, the goal, and past actions — and outputs a JSON action command.",
            bullets   = listOf(
                Icons.Default.Memory       to "Q4_K_M quantisation: 870 MB disk, ~1700 MB RAM with mmap",
                Icons.Default.Speed        to "8–15 tok/s on Exynos 9611 + Vulkan (target, not yet measured)",
                Icons.Default.Code         to "llama.cpp C++ runtime — same engine used by leading open-source tools",
                Icons.Default.Warning      to "Stub mode active until the NDK build compiles libllama-jni.so",
            ),
            callout   = "Until the native library is compiled, ARIA runs in stub mode — all decisions come from a hardcoded fallback, not the real model.",
            calloutColor = ARIAColors.Warning,
        ),

        KnowledgePage(
            icon      = Icons.Default.Loop,
            iconTint  = ARIAColors.Primary,
            title     = "Observe → Think → Act",
            body      = "Every step of a task follows the same three-phase cycle:\n\n1. OBSERVE: ARIA reads the Accessibility Tree (a text description of every visible UI element) and runs OCR on the screen. If vision is enabled, SmolVLM-256M also produces a visual description.\n\n2. THINK: The full observation — plus past actions, goal, and retrieved memories — is assembled into a structured prompt and sent to Llama 3.2-1B. The model outputs a JSON command such as {\"tool\":\"Click\",\"node_id\":\"#4\"}.\n\n3. ACT: GestureEngine dispatches a real programmatic touch via the Android Accessibility Service — the same mechanism used by screen readers.",
            bullets   = listOf(
                Icons.Default.Visibility   to "Observe: a11y tree + OCR + optional vision every step",
                Icons.Default.Psychology   to "Think: full prompt → LLM → JSON action command",
                Icons.Default.TouchApp     to "Act: real gesture dispatched via AccessibilityService",
                Icons.Default.Save         to "Store: result saved to SQLite as an experience tuple",
            ),
            callout   = "The loop runs up to 50 steps per task. Stuck detection aborts automatically if the screen stops changing.",
        ),

        KnowledgePage(
            icon      = Icons.Default.RemoveRedEye,
            iconTint  = ARIAColors.Primary,
            title     = "Vision: SmolVLM-256M",
            body      = "ARIA can optionally load SmolVLM-256M — a compact multimodal model that describes what it sees on screen using visual understanding, not just text.\n\nThis helps in three cases:\n  • Games and Flutter apps without an accessibility tree\n  • UI elements that are purely graphical (icons, images, buttons with no text)\n  • Detecting whether an action visually succeeded (pixel diff verification)\n\nSmolVLM uses the CLIP vision encoder to convert a JPEG screenshot into image embeddings, then Llama.cpp generates a goal-aware description. It adds ~200 MB to disk and runs in a separate model context so it never interferes with the main text model.",
            bullets   = listOf(
                Icons.Default.PhotoCamera  to "Screenshot → CLIP encode → text description every step",
                Icons.Default.Cached       to "Frame caching: ~0 ms when the screen hasn't changed",
                Icons.Default.SdCard       to "~200 MB download (base GGUF + mmproj file)",
                Icons.Default.CallSplit     to "Separate handle — does not share context with the text LLM",
            ),
            callout   = "Vision is optional. ARIA works without it using the accessibility tree alone.",
        ),

        KnowledgePage(
            icon      = Icons.Default.Storage,
            iconTint  = ARIAColors.Accent,
            title     = "Memory & Experience",
            body      = "Every action ARIA takes is recorded in an SQLite database called ExperienceStore. Each row is an experience tuple:\n\n  (screen_hash, action_json, result, reward, timestamp)\n\nBefore each step, ARIA retrieves the 5 most relevant past experiences using MiniLM-L6 embeddings (a 23 MB ONNX model). This lets ARIA avoid repeating past mistakes and reuse successful patterns.\n\nThe Object Label Store is a separate database of UI element annotations created by you in the Labeler screen. ARIA uses these annotations at 3× weight during training — your corrections have more influence than passive experience.",
            bullets   = listOf(
                Icons.Default.TableRows    to "ExperienceStore: SQLite, success/failure/edge-case episodes",
                Icons.Default.Search       to "MiniLM embeddings: semantic similarity search over past steps",
                Icons.Default.Label        to "ObjectLabelStore: human-annotated UI elements (3× weight)",
                Icons.Default.Memory       to "Embedding model: ~23 MB ONNX, runs on-device with ONNX Runtime",
            ),
        ),

        KnowledgePage(
            icon      = Icons.Default.School,
            iconTint  = ARIAColors.Success,
            title     = "On-Device Learning: RL + LoRA",
            body      = "ARIA improves itself through two complementary mechanisms:\n\nREINFORCE Policy Network: A 3-layer neural network (pure Kotlin, no native library required) that learns which action type to prefer given a situation. Updated after every task using the REINFORCE policy gradient algorithm with discounted returns and Adam optimisation.\n\nLoRA Fine-Tuning: The LLM itself can be fine-tuned using Low-Rank Adapters (LoRA). ARIA batches successful experience tuples into a JSONL dataset and calls llama.cpp's AdamW optimizer via JNI. This directly improves LLM decision quality — but requires the NDK build.",
            bullets   = listOf(
                Icons.Default.Psychology   to "REINFORCE: works immediately, no NDK — starts random, improves with experience",
                Icons.Default.AutoAwesome  to "LoRA: improves the LLM directly — requires NDK + compiled library",
                Icons.Default.VideoLibrary to "IRL Learning: teach by recording a video of yourself doing the task",
                Icons.Default.BarChart     to "Adam step count and policy loss visible in the Train screen",
            ),
            callout   = "The policy network starts with random weights. It needs at least 20–50 completed tasks before its suggestions become meaningful.",
            calloutColor = ARIAColors.Warning,
        ),

        KnowledgePage(
            icon      = Icons.Default.Lock,
            iconTint  = ARIAColors.Success,
            title     = "Your Privacy",
            body      = "ARIA was designed privacy-first. Here is exactly what stays on your device and what does not:\n\nStays on device: Everything. The LLM model weights, every screenshot taken, every decision made, every experience stored, your goals, your task history, your labels.\n\nLeaves your device: Only the model download (one-time, from a public source). Nothing else.\n\nThere are no analytics, no crash reporters, no telemetry, no remote logging. The app does not need an internet connection after the initial model download.",
            bullets   = listOf(
                Icons.Default.PhoneAndroid to "All inference runs locally — no API calls",
                Icons.Default.LockOpen     to "No accounts, no login, no email required",
                Icons.Default.WifiOff      to "Works fully offline after model download",
                Icons.Default.VisibilityOff to "Screenshots never transmitted — used only for on-device OCR",
            ),
            callout   = "The only network requests are the one-time model downloads (Llama, SmolVLM, MiniLM, EfficientDet).",
        ),

        KnowledgePage(
            icon      = Icons.Default.Build,
            iconTint  = ARIAColors.Warning,
            title     = "What Needs the NDK Build",
            body      = "Several features require the Android NDK to compile a native library (libllama-jni.so). Until that build runs, ARIA falls back to safe stubs and logs a clear warning.\n\nThe NDK build compiles:\n  • llama.cpp — the LLM C++ runtime\n  • NEON SIMD acceleration for the policy network\n  • LoRA AdamW optimizer\n  • CLIP vision encoder (mtmd library)\n\nTo trigger the build: clone the repo, install Android Studio with NDK r27+, run ./gradlew assembleDebug from the android/ directory. The build takes 10–20 minutes the first time.",
            bullets   = listOf(
                Icons.Default.PriorityHigh to "LLM inference (Llama 3.2-1B) — stub until built",
                Icons.Default.PriorityHigh to "Vision inference (SmolVLM) — stub until built",
                Icons.Default.Info          to "LoRA fine-tuning — writes metadata file until built",
                Icons.Default.CheckCircle   to "Policy network (REINFORCE) — works without NDK in Kotlin",
            ),
            callout   = "Stub mode is clearly labelled in logcat. Real mode activates automatically once System.loadLibrary(\"llama-jni\") succeeds at startup.",
            calloutColor = ARIAColors.Warning,
        ),

        KnowledgePage(
            icon      = Icons.Default.Accessibility,
            iconTint  = ARIAColors.Success,
            title     = "The Permissions & Why",
            body      = "ARIA requests three system permissions. Here is exactly what each one does:\n\nAccessibility Service: Required to read the UI node tree of any open app and dispatch real touch gestures. Without it, ARIA cannot see or interact with other apps. Enable once in Android Settings → Accessibility → ARIA Agent.\n\nScreen Capture (MediaProjection): Required to take screenshots for OCR and vision inference. Only active during a running task. You approve it each session.\n\nWake Lock: Keeps the CPU from sleeping during a training cycle. Released immediately when training finishes.",
            bullets   = listOf(
                Icons.Default.Accessibility to "Accessibility: reads UI + dispatches gestures (required)",
                Icons.Default.Screenshot    to "Screen Capture: screenshots for OCR + vision (per-session)",
                Icons.Default.BatteryFull   to "Wake Lock: held only during RL training, auto-released",
                Icons.Default.Notifications to "Notifications: foreground service notification while agent runs",
            ),
        ),

        KnowledgePage(
            icon      = Icons.Default.Lightbulb,
            iconTint  = ARIAColors.Accent,
            title     = "Tips for Best Results",
            body      = "ARIA performs best when you give it clear, specific goals and let it learn over time. Here are the most impactful things you can do:",
            bullets   = listOf(
                Icons.Default.Edit          to "Be specific: \"Open Chrome, search for X, tap the first result\" beats \"find something about X\"",
                Icons.Default.Label         to "Label screens: the more UI elements you annotate in the Labeler, the better ARIA understands new apps",
                Icons.Default.VideoLibrary  to "Record yourself: IRL video training is the fastest way to teach ARIA a new workflow",
                Icons.Default.School         to "Run learn-only: lets ARIA observe without touching — safe for exploring unfamiliar apps",
                Icons.Default.BarChart      to "Check the Dashboard: thermal state, LoRA version, policy loss tell you how training is progressing",
                Icons.Default.Queue         to "Queue tasks: line up multiple tasks and ARIA chains them automatically when one completes",
            ),
            callout   = "Tip: after training on IRL video, run an RL cycle immediately. The two learning signals combine for faster convergence.",
            calloutColor = ARIAColors.Success,
        ),

        // ── TRAINING MANUAL ───────────────────────────────────────────────────

        KnowledgePage(
            icon      = Icons.Default.School,
            iconTint  = ARIAColors.Primary,
            title     = "Training ARIA From Zero",
            body      = "Training ARIA is not like training a traditional app. You are teaching a live AI agent that already knows how to reason — you just need to show it your phone, your apps, and your goals.\n\nThere are four distinct training modes. Each one teaches ARIA something different, and they all compound over time:\n\n  1. REINFORCE — learns automatically from every task it runs\n  2. IRL (Imitation) — you record a video, ARIA copies your actions\n  3. Manual Labeling — you annotate UI elements by name\n  4. LoRA Fine-Tuning — directly improves the LLM brain (advanced)\n\nYou can use all four, but start with REINFORCE — it requires zero effort and starts working immediately.",
            bullets   = listOf(
                Icons.Default.AutoMode      to "Day 1–3: just run tasks. REINFORCE learns automatically",
                Icons.Default.VideoLibrary  to "Day 4+: record IRL videos for complex workflows",
                Icons.Default.Label         to "Week 2: annotate stubborn UI elements in the Labeler",
                Icons.Default.AutoAwesome   to "Advanced: LoRA fine-tuning once NDK is compiled",
            ),
            callout   = "No GPU, no cloud, no data labels required to start. Just run a task, and ARIA starts learning.",
            calloutColor = ARIAColors.Primary,
        ),

        KnowledgePage(
            icon      = Icons.Default.AutoMode,
            iconTint  = ARIAColors.Success,
            title     = "Mode 1: REINFORCE (Auto-Learning)",
            body      = "REINFORCE is always on. Every time ARIA completes a step — success or failure — it stores the experience and updates its policy network automatically.\n\nHow it works:\n  • ARIA runs a step and records (screen, action, result, reward)\n  • After the task ends, it calculates discounted returns: steps that led to success get high reward, failed paths get penalised\n  • The 3-layer policy network is updated with Adam gradient descent\n  • Over 20–50 tasks, ARIA learns which action types work in which context\n\nEXAMPLE — Teaching ARIA to open WhatsApp:\n  Task 1: ARIA tries random things, opens the wrong app (reward = −1)\n  Task 5: ARIA scrolls then taps WhatsApp icon (reward = +0.5)\n  Task 20: ARIA finds and opens WhatsApp in 2 steps every time (reward = +1)\n\nYou do nothing except run the task and give feedback (thumbs up / thumbs down).",
            bullets   = listOf(
                Icons.Default.ThumbUp       to "Thumbs up = +1 reward → that action path is reinforced",
                Icons.Default.ThumbDown     to "Thumbs down = −1 reward → that path is penalised",
                Icons.Default.AutoAwesome   to "No feedback = neutral reward from task completion signal",
                Icons.Default.BarChart      to "Watch policy loss in the Train screen — falling = learning",
            ),
            callout   = "The policy network starts completely random. After 20 tasks it becomes 2–3× faster. After 100 tasks it becomes highly reliable for familiar apps.",
            calloutColor = ARIAColors.Success,
        ),

        KnowledgePage(
            icon      = Icons.Default.VideoLibrary,
            iconTint  = ARIAColors.Accent,
            title     = "Mode 2: IRL (Imitation from Video)",
            body      = "IRL (Inverse Reinforcement Learning) is the fastest way to teach ARIA a specific workflow. You record a screen recording of yourself doing the task — ARIA watches it frame by frame and extracts what you tapped, where, and in what order.\n\nHow to record a teaching video:\n  1. Go to the Train screen → tap \"Record IRL Session\"\n  2. Do the task naturally (it records your screen + taps)\n  3. Stop recording — ARIA processes the video automatically\n  4. Each frame is matched to an action: tap, scroll, type, swipe\n  5. These become high-confidence training examples (3× normal weight)\n\nEXAMPLE — Teaching ARIA to recharge Paytm:\n  You record: open Paytm → tap Recharge → tap Mobile → enter number → tap Proceed → select UPI → tap Pay\n  ARIA extracts 7 action steps with coordinates and labels them\n  After just 1 video, ARIA can complete the same flow with ~80% accuracy\n\nEXAMPLE — Teaching ARIA to book a cab on Ola:\n  You record the booking flow once (takes ~90 seconds)\n  ARIA learns: home → tap Bike → enter destination → confirm pickup → tap Book\n  It generalises: if the destination changes, it still follows the same structure",
            bullets   = listOf(
                Icons.Default.Videocam      to "Record once → ARIA trains for ~2 minutes on-device",
                Icons.Default.Repeat        to "Record the same flow 3 times → accuracy jumps to 95%+",
                Icons.Default.SlowMotion24  to "Record at normal speed — ARIA handles frame extraction",
                Icons.Default.Warning       to "Avoid recording personal info — process runs locally but store is on-device",
            ),
            callout   = "IRL + REINFORCE together: record the task once (IRL), then run it 10 times (REINFORCE). This is the fastest training recipe in ARIA.",
            calloutColor = ARIAColors.Accent,
        ),

        KnowledgePage(
            icon      = Icons.Default.Label,
            iconTint  = ARIAColors.Warning,
            title     = "Mode 3: Manual Labeling",
            body      = "Some UI elements have no text — icons, image buttons, custom-drawn views in games, Flutter apps. ARIA cannot name these from the accessibility tree alone. Manual labeling teaches it what they mean.\n\nHow to label:\n  1. Go to the Train screen → tap \"Open Labeler\"\n  2. Take a screenshot of the app you want to teach\n  3. Draw a box around a UI element\n  4. Type its name and role: e.g. \"Send button\" or \"Hamburger menu\"\n  5. Save — ARIA immediately uses this label in all future tasks\n\nEXAMPLE — Labeling a game UI:\n  You open PUBG Mobile. The fire button has no accessibility label.\n  You draw a box around it and label it: \"Fire button\"\n  The jump button: \"Jump button\"\n  Now ARIA can play the game using these names in its reasoning\n\nEXAMPLE — Labeling a custom icon bar:\n  A banking app has 5 icon tabs with no text.\n  You label each one: \"Home\", \"Pay\", \"Cards\", \"History\", \"Profile\"\n  Next task: ARIA says \"tap Pay tab\" and finds it instantly\n\nLabeled elements get 3× reward weight in training — your annotations override ARIA's guesses.",
            bullets   = listOf(
                Icons.Default.TouchApp      to "Draw box → type name → save. Takes 5 seconds per element",
                Icons.Default.Games         to "Essential for games — accessibility tree is empty in Unity/Unreal",
                Icons.Default.Apps          to "Essential for Flutter apps — they render their own widgets",
                Icons.Default.Bolt          to "Immediate effect — ARIA uses the label from the very next task",
            ),
            callout   = "Start with the 3–5 elements ARIA gets wrong most often. Labeling those specific elements gives the biggest accuracy jump.",
            calloutColor = ARIAColors.Warning,
        ),

        KnowledgePage(
            icon      = Icons.Default.AutoAwesome,
            iconTint  = ARIAColors.Primary,
            title     = "Mode 4: LoRA Fine-Tuning",
            body      = "LoRA (Low-Rank Adaptation) is the most powerful training mode. It directly modifies the weights of Llama 3.2-1B — the AI brain itself — making it permanently smarter at your specific use cases.\n\nRequires: NDK compiled (libllama-jni.so must be built). See the \"NDK Build\" page.\n\nHow LoRA works:\n  • ARIA gathers your best experiences (high-reward episodes)\n  • Converts them to a JSONL fine-tuning dataset in llama.cpp format\n  • Trains a small set of adapter matrices (LoRA rank 8–16)\n  • These adapters are loaded on top of the base model — no re-download needed\n  • LoRA version counter increments in the Train screen\n\nEXAMPLE — Before vs After LoRA for Swiggy orders:\n  Before LoRA (Day 1): takes 18 steps, sometimes clicks wrong restaurant\n  After 1 LoRA cycle on 30 episodes: takes 9 steps, >90% correct first tap\n  After 3 LoRA cycles: completes Swiggy order in 6 steps reliably\n\nWhen to trigger LoRA:\n  • After collecting 50+ successful episodes (Train → tap \"Train LoRA\")\n  • After IRL sessions (combine IRL data with RL data)\n  • Scheduled: enable auto-training in Settings → Train runs overnight\n\nLoRA runs on CPU (4 cores, ~15 min/cycle). Battery drain is real — plug in first.",
            bullets   = listOf(
                Icons.Default.Memory        to "LoRA adapters are ~10–30 MB — tiny compared to the 800 MB base model",
                Icons.Default.Battery5Bar   to "Always plug in before LoRA training — it takes 10–20 minutes",
                Icons.Default.History       to "Old adapters are kept — revert anytime from the Train screen",
                Icons.Default.PriorityHigh  to "Requires NDK build — check Modules screen → LLM must show ACTIVE",
            ),
            callout   = "LoRA is optional. ARIA improves meaningfully through REINFORCE + IRL alone. LoRA is for users who want maximum performance.",
            calloutColor = ARIAColors.Primary,
        ),

        KnowledgePage(
            icon      = Icons.Default.RocketLaunch,
            iconTint  = ARIAColors.Success,
            title     = "Training Recipes",
            body      = "The fastest ways to train ARIA for real-world tasks, combining the four modes:\n\nRECIPE A — Learn a new app in one evening:\n  1. Run the task 5 times (REINFORCE collects data)\n  2. Record 2 IRL videos of yourself doing it\n  3. Label any icon-only buttons ARIA missed\n  4. Run LoRA fine-tuning overnight (optional)\n  → Next morning: ARIA handles that app reliably\n\nRECIPE B — Fix a recurring mistake:\n  ARIA keeps tapping the wrong button → record an IRL video of the correct tap → that action gets 3× weight → mistake disappears within 3 tasks\n\nRECIPE C — Train for gaming:\n  1. Label all game UI elements (fire, jump, aim, map)\n  2. Enable MobileSAM (Modules screen) for pixel-level detection\n  3. Switch to Game Mode in the Control screen\n  4. Run a 10-minute training session — ARIA observes your gameplay\n  5. Let REINFORCE run for 20 game sessions\n  → ARIA learns game-specific tap timing and sequences\n\nRECIPE D — Total from-scratch setup (first week):\n  Day 1: download all modules, run 5 tasks (any app)\n  Day 2–3: run 10 tasks, give thumbs up/down honestly\n  Day 4: record 3 IRL videos of your most-used workflows\n  Day 5: open Labeler, label 10–15 stubborn icons\n  Day 7: trigger first LoRA cycle (if NDK is built)\n  → End of week 1: ARIA is 3–5× faster and more accurate than Day 1",
            bullets   = listOf(
                Icons.Default.Speed         to "Fastest gain: IRL video (1 recording = 50+ labelled examples)",
                Icons.Default.Stairs        to "Steady gain: REINFORCE runs automatically — just keep using ARIA",
                Icons.Default.Tune          to "Targeted fix: labeling specific missed elements fixes them immediately",
                Icons.Default.NightsStay    to "Overnight boost: LoRA scheduled training while you sleep",
            ),
            callout   = "You do not need to do all of this. Even just running REINFORCE for two weeks of normal use makes ARIA significantly smarter at your daily apps.",
            calloutColor = ARIAColors.Success,
        ),
    )

    var pageIdx by remember { mutableIntStateOf(0) }
    val page = pages[pageIdx]
    val isFirst = pageIdx == 0
    val isLast  = pageIdx == pages.lastIndex

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ARIAColors.Background)
    ) {
        Column(
            modifier            = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            // ── Top bar ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = ARIAColors.Muted
                    )
                }
                Text(
                    "KNOWLEDGE BASE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color         = ARIAColors.Muted,
                        fontWeight    = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        fontFamily    = FontFamily.Monospace,
                    )
                )
                Text(
                    "${pageIdx + 1} / ${pages.size}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color      = ARIAColors.Muted,
                        fontFamily = FontFamily.Monospace,
                    ),
                    modifier = Modifier.padding(end = 16.dp)
                )
            }

            // ── Progress dots ─────────────────────────────────────────────────
            Row(
                modifier              = Modifier.padding(horizontal = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                pages.forEachIndexed { i, _ ->
                    val active = i == pageIdx
                    val done   = i < pageIdx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                when {
                                    active -> ARIAColors.Primary
                                    done   -> ARIAColors.Primary.copy(alpha = 0.4f)
                                    else   -> ARIAColors.Divider
                                }
                            )
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Page content ─────────────────────────────────────────────────
            AnimatedContent(
                targetState = pageIdx,
                transitionSpec = {
                    val forward = targetState > initialState
                    val enter   = fadeIn() + slideInHorizontally { w -> if (forward) w / 4 else -w / 4 }
                    val exit    = fadeOut() + slideOutHorizontally { w -> if (forward) -w / 4 else w / 4 }
                    enter togetherWith exit
                },
                label = "knowledge_page",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { idx ->
                val p = pages[idx]
                KnowledgePageContent(p)
            }

            // ── Navigation ────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                if (!isFirst) {
                    OutlinedButton(
                        onClick = { pageIdx-- },
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(10.dp),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = ARIAColors.Muted),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, ARIAColors.Divider),
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Back")
                    }
                }

                Button(
                    onClick  = {
                        if (isLast) onBack() else pageIdx++
                    },
                    modifier = Modifier.weight(if (isFirst) 2f else 1f),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = if (isLast) ARIAColors.Success else ARIAColors.Primary
                    ),
                ) {
                    Text(
                        if (isLast) "Done" else "Next",
                        fontWeight = FontWeight.Bold,
                        color      = Color.White,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        if (isLast) Icons.Default.CheckCircle else Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint     = Color.White,
                    )
                }
            }
        }
    }
}

// ─── Page body composable ─────────────────────────────────────────────────────

@Composable
private fun KnowledgePageContent(page: KnowledgePage) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {

        // Icon
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(page.iconTint.copy(alpha = 0.12f))
                .align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                page.icon,
                contentDescription = null,
                tint     = page.iconTint,
                modifier = Modifier.size(36.dp),
            )
        }

        // Title
        Text(
            page.title,
            style = MaterialTheme.typography.titleLarge.copy(
                color      = ARIAColors.OnSurface,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center,
                lineHeight = 30.sp,
            ),
            textAlign = TextAlign.Center,
            modifier  = Modifier.fillMaxWidth(),
        )

        // Body paragraphs
        Text(
            page.body,
            style = MaterialTheme.typography.bodyMedium.copy(
                color      = ARIAColors.Muted,
                lineHeight = 23.sp,
            ),
        )

        // Bullets
        if (page.bullets.isNotEmpty()) {
            Card(
                modifier  = Modifier.fillMaxWidth(),
                shape     = RoundedCornerShape(12.dp),
                colors    = CardDefaults.cardColors(containerColor = ARIAColors.Surface),
                elevation = CardDefaults.cardElevation(0.dp),
            ) {
                Column(
                    modifier            = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    page.bullets.forEachIndexed { i, (icon, text) ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment     = Alignment.Top,
                        ) {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint     = page.iconTint,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(top = 2.dp),
                            )
                            Text(
                                text,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color      = ARIAColors.OnSurface,
                                    lineHeight = 19.sp,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (i < page.bullets.lastIndex) {
                            HorizontalDivider(color = ARIAColors.Divider, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }

        // Callout box
        if (page.callout != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(page.calloutColor.copy(alpha = 0.08f))
                    .border(
                        width = 1.dp,
                        color = page.calloutColor.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(10.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment     = Alignment.Top,
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint     = page.calloutColor,
                    modifier = Modifier.size(16.dp).padding(top = 1.dp),
                )
                Text(
                    page.callout,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color      = page.calloutColor,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}
