package com.ariaagent.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ariaagent.mobile.ui.viewmodel.AgentViewModel
import com.ariaagent.mobile.ui.viewmodel.QueuedTaskItem
import com.ariaagent.mobile.ui.theme.ARIAColors
import java.text.SimpleDateFormat
import java.util.*

/**
 * ControlScreen — start/pause/stop the agent and manage the task queue.
 *
 * Phase 11: start/pause/stop + goal input.
 * Phase 15 update: preset task chips + task queue panel (add/remove/clear).
 *
 * Pure Compose — calls AgentViewModel which calls AgentLoop / TaskQueueManager directly.
 * No bridge, no React Native, no JS.
 */

private val PRESET_TASKS = listOf(
    "Open YouTube and play the trending video",
    "Open Settings and check available storage",
    "Open Gallery and find the latest photo",
    "Open Chrome and go to google.com",
    "Open WhatsApp and read the latest message",
    "Open Maps and search for nearby restaurants",
    "Open Calculator and compute 15% tip on 85",
    "Check battery level in Settings",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControlScreen(vm: AgentViewModel = viewModel()) {
    val agentState  by vm.agentState.collectAsStateWithLifecycle()
    val moduleState by vm.moduleState.collectAsStateWithLifecycle()
    val taskQueue   by vm.taskQueue.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    var goalText  by remember { mutableStateOf("") }
    var targetApp by remember { mutableStateOf("") }

    val isRunning = agentState.status == "running"
    val isPaused  = agentState.status == "paused"
    val isIdle    = agentState.status == "idle" || agentState.status == "done" || agentState.status == "error"
    val canStart  = isIdle && goalText.isNotBlank() && moduleState.modelReady && moduleState.accessibilityGranted

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ARIAColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "CONTROL",
            style = MaterialTheme.typography.headlineMedium.copy(
                color = ARIAColors.Primary,
                fontWeight = FontWeight.Bold
            )
        )

        // ── Readiness checks ──────────────────────────────────────────────────
        ARIACard {
            Text("READINESS", style = MaterialTheme.typography.labelSmall.copy(color = ARIAColors.Muted))
            Spacer(Modifier.height(8.dp))
            ReadinessRow("Model ready",    ok = moduleState.modelReady)
            ReadinessRow("Model loaded",   ok = moduleState.modelLoaded)
            ReadinessRow("Accessibility",  ok = moduleState.accessibilityGranted)
            ReadinessRow("Screen capture", ok = moduleState.screenCaptureGranted)
        }

        // ── Goal input ────────────────────────────────────────────────────────
        ARIACard {
            Text("TASK GOAL", style = MaterialTheme.typography.labelSmall.copy(color = ARIAColors.Muted))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = goalText,
                onValueChange = { goalText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        "e.g. Open YouTube and search for cooking tutorials",
                        style = MaterialTheme.typography.bodySmall.copy(color = ARIAColors.Muted)
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = ARIAColors.Primary,
                    unfocusedBorderColor = ARIAColors.Divider,
                    focusedTextColor     = ARIAColors.OnSurface,
                    unfocusedTextColor   = ARIAColors.OnSurface,
                    cursorColor          = ARIAColors.Primary,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.clearFocus() }),
                maxLines = 3,
                enabled = isIdle
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = targetApp,
                onValueChange = { targetApp = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        "Target app package (optional)",
                        style = MaterialTheme.typography.bodySmall.copy(color = ARIAColors.Muted)
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = ARIAColors.Primary,
                    unfocusedBorderColor = ARIAColors.Divider,
                    focusedTextColor     = ARIAColors.OnSurface,
                    unfocusedTextColor   = ARIAColors.OnSurface,
                    cursorColor          = ARIAColors.Primary,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                maxLines = 1,
                enabled = isIdle
            )
        }

        // ── Preset task chips ─────────────────────────────────────────────────
        ARIACard {
            Text(
                "PRESET TASKS",
                style = MaterialTheme.typography.labelSmall.copy(color = ARIAColors.Muted)
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PRESET_TASKS.forEach { preset ->
                    SuggestionChip(
                        onClick = {
                            goalText = preset
                            focusManager.clearFocus()
                        },
                        label = {
                            Text(
                                preset.take(32) + if (preset.length > 32) "…" else "",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = ARIAColors.Primary.copy(alpha = 0.08f),
                            labelColor = ARIAColors.Primary
                        ),
                        border = SuggestionChipDefaults.suggestionChipBorder(
                            enabled = true,
                            borderColor = ARIAColors.Primary.copy(alpha = 0.25f)
                        )
                    )
                }
            }
        }

        // ── Control buttons ───────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    focusManager.clearFocus()
                    vm.startAgent(goalText.trim(), targetApp.trim())
                },
                modifier = Modifier.weight(1f),
                enabled = canStart,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ARIAColors.Primary,
                    disabledContainerColor = ARIAColors.Divider
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("START", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = {
                    if (isPaused) vm.startAgent(agentState.currentTask, agentState.currentApp)
                    else vm.pauseAgent()
                },
                modifier = Modifier.weight(1f),
                enabled = isRunning || isPaused,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ARIAColors.Warning),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(if (isPaused) "RESUME" else "PAUSE", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = { vm.stopAgent() },
                modifier = Modifier.weight(1f),
                enabled = isRunning || isPaused,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ARIAColors.Error),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("STOP", fontWeight = FontWeight.Bold)
            }
        }

        // ── Phase 15: Task Queue ──────────────────────────────────────────────
        ARIACard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.PlaylistPlay,
                        contentDescription = null,
                        tint = ARIAColors.Accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "TASK QUEUE",
                        style = MaterialTheme.typography.labelSmall.copy(color = ARIAColors.Muted)
                    )
                    if (taskQueue.isNotEmpty()) {
                        Text(
                            "(${taskQueue.size})",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = ARIAColors.Accent, fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (taskQueue.isNotEmpty()) {
                        TextButton(
                            onClick = { vm.clearTaskQueue() },
                            colors = ButtonDefaults.textButtonColors(contentColor = ARIAColors.Error)
                        ) {
                            Text("Clear all", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    IconButton(
                        onClick = { vm.refreshTaskQueue() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh queue",
                            tint = ARIAColors.Muted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Add to queue row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        if (goalText.isNotBlank()) {
                            focusManager.clearFocus()
                            vm.enqueueTask(goalText.trim(), targetApp.trim())
                        }
                    },
                    enabled = goalText.isNotBlank(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = ARIAColors.Accent
                    ),
                    border = ButtonDefaults.outlinedButtonBorder,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.AddCircleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Add current goal to queue", fontWeight = FontWeight.SemiBold)
                }
            }

            if (taskQueue.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = ARIAColors.Divider)
                Spacer(Modifier.height(8.dp))

                // Queue item list (non-lazy, max 10 shown)
                taskQueue.take(10).forEachIndexed { index, task ->
                    QueuedTaskRow(
                        index = index + 1,
                        task = task,
                        onRemove = { vm.removeQueuedTask(task.id) }
                    )
                    if (index < taskQueue.size - 1 && index < 9) {
                        HorizontalDivider(
                            color = ARIAColors.Divider.copy(alpha = 0.4f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
                if (taskQueue.size > 10) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "… and ${taskQueue.size - 10} more tasks",
                        style = MaterialTheme.typography.labelSmall.copy(color = ARIAColors.Muted)
                    )
                }
            } else {
                Text(
                    "No tasks queued. Add goals here so ARIA auto-chains them after each task completes.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = ARIAColors.Muted,
                        lineHeight = 18.sp
                    )
                )
            }
        }

        // ── Status banners ────────────────────────────────────────────────────
        if (!moduleState.modelReady) {
            InfoBanner("Model not downloaded. Go to Modules to download it.", ARIAColors.Warning)
        }
        if (!moduleState.accessibilityGranted) {
            InfoBanner("Accessibility permission not granted. Enable ARIA in Accessibility Settings.", ARIAColors.Error)
        }
        if (agentState.lastError.isNotBlank()) {
            InfoBanner("Last error: ${agentState.lastError}", ARIAColors.Error)
        }
    }
}

// ─── Private composables ──────────────────────────────────────────────────────

@Composable
private fun QueuedTaskRow(
    index: Int,
    task: QueuedTaskItem,
    onRemove: () -> Unit
) {
    val fmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$index",
            style = MaterialTheme.typography.labelSmall.copy(
                color = ARIAColors.Accent, fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.width(16.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                task.goal,
                style = MaterialTheme.typography.bodySmall.copy(color = ARIAColors.OnSurface),
                maxLines = 2
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (task.appPackage.isNotBlank()) {
                    Text(
                        task.appPackage.substringAfterLast('.'),
                        style = MaterialTheme.typography.labelSmall.copy(color = ARIAColors.Muted, fontSize = 10.sp)
                    )
                }
                Text(
                    fmt.format(Date(task.enqueuedAt)),
                    style = MaterialTheme.typography.labelSmall.copy(color = ARIAColors.Muted, fontSize = 10.sp)
                )
                if (task.priority != 0) {
                    Text(
                        "p${task.priority}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = ARIAColors.Warning, fontSize = 10.sp
                        )
                    )
                }
            }
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                Icons.Default.RemoveCircleOutline,
                contentDescription = "Remove",
                tint = ARIAColors.Error,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ReadinessRow(label: String, ok: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall.copy(color = ARIAColors.OnSurface))
        Icon(
            if (ok) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = if (ok) ARIAColors.Success else ARIAColors.Error,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun InfoBanner(message: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Info, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(message, style = MaterialTheme.typography.bodySmall.copy(color = color))
    }
}
