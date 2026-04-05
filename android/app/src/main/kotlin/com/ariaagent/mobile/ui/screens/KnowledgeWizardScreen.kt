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
