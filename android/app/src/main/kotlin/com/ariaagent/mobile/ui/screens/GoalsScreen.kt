package com.ariaagent.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ariaagent.mobile.ui.theme.ARIAColors
import com.ariaagent.mobile.ui.viewmodel.AgentViewModel
import com.ariaagent.mobile.ui.viewmodel.QueuedTaskItem
import java.text.SimpleDateFormat
import java.util.*

/**
 * GoalsScreen — full-screen task queue management.
 *
 * Accessible from ControlScreen header "Goals →" button.
 * No bottom-nav entry — it's a focused full-screen editor.
 *
 * Tabs:
 *   Queue     — live task queue with priority badges, remove, clear-all
 *   Templates — preset task library tiles; tap to enqueue quickly
 *   Triggers  — placeholder for future scheduled/event-based triggers
 */

private val GOAL_TEMPLATES = listOf(
    Triple("Open YouTube",            "Watch trending videos",                "com.google.android.youtube"),
    Triple("Check Gmail",             "Read and summarize new emails",        "com.google.android.gm"),
    Triple("Open Settings",           "Check available storage",              "com.android.settings"),
    Triple("Open Google Maps",        "Find nearby restaurants",              "com.google.android.apps.maps"),
    Triple("Open Chrome",             "Go to google.com",                     "com.android.chrome"),
    Triple("Open WhatsApp",           "Read the latest message",              "com.whatsapp"),
    Triple("Open Camera",             "Take a photo",                         "com.android.camera2"),
    Triple("Check Battery",           "Open Settings and check battery level","com.android.settings"),
    Triple("Open Spotify",            "Play a recommended playlist",          "com.spotify.music"),
    Triple("Open Play Store",         "Check for pending app updates",        "com.android.vending"),
    Triple("Open Calendar",           "Review today's events",                "com.google.android.calendar"),
    Triple("Open Calculator",         "Compute 15% tip on 85",               "com.android.calculator2"),
)

private enum class GoalsTab(val label: String, val icon: ImageVector) {
    Queue("Queue",     Icons.Default.Queue),
    Templates("Templates", Icons.Default.GridView),
    Triggers("Triggers",   Icons.Default.Schedule),
}

@Composable
fun GoalsScreen(
    vm: AgentViewModel,
    onBack: () -> Unit,
) {
    val taskQueue    by vm.taskQueue.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    var activeTab      by remember { mutableStateOf(GoalsTab.Queue) }
    var goalText       by remember { mutableStateOf("") }
    var appText        by remember { mutableStateOf("") }
    var priorityLevel  by remember { mutableIntStateOf(0) }
    var showClearConfirm by remember { mutableStateOf(false) }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor   = ARIAColors.Surface,
            title = { Text("Clear queue?", style = MaterialTheme.typography.titleMedium.copy(color = ARIAColors.OnSurface, fontWeight = FontWeight.Bold)) },
            text  = { Text("All ${taskQueue.size} queued tasks will be permanently removed.", style = MaterialTheme.typography.bodySmall.copy(color = ARIAColors.Muted)) },
            confirmButton = {
                Button(onClick = { vm.clearTaskQueue(); showClearConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = ARIAColors.Destructive),
                    shape  = RoundedCornerShape(8.dp)) {
                    Text("Clear All", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("Cancel", color = ARIAColors.Muted) } }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ARIAColors.Background)
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ARIAColors.Surface)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = ARIAColors.OnSurface)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("GOALS", style = MaterialTheme.typography.titleLarge.copy(color = ARIAColors.Primary, fontWeight = FontWeight.Bold))
                Text("${taskQueue.size} task${if (taskQueue.size != 1) "s" else ""} queued", style = MaterialTheme.typography.bodySmall.copy(color = ARIAColors.Muted))
            }
            if (activeTab == GoalsTab.Queue && taskQueue.isNotEmpty()) {
                IconButton(onClick = { showClearConfirm = true }) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear all", tint = ARIAColors.Destructive)
                }
            }
        }

        // ── Tab bar ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ARIAColors.Surface)
                .padding(horizontal = 16.dp, vertical = 0.dp),
        ) {
            GoalsTab.entries.forEach { tab ->
                val selected = tab == activeTab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { activeTab = tab }
                        )
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        tab.icon,
                        contentDescription = tab.label,
                        tint = if (selected) ARIAColors.Primary else ARIAColors.Muted,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (selected) ARIAColors.Primary else ARIAColors.Muted,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    )
                    Box(
                        modifier = Modifier
                            .height(2.dp)
                            .fillMaxWidth(0.6f)
                            .clip(RoundedCornerShape(1.dp))
                            .background(if (selected) ARIAColors.Primary else ARIAColors.Background)
                    )
                }
            }
        }

        HorizontalDivider(color = ARIAColors.Divider, thickness = 0.5.dp)

        // ── Tab content ───────────────────────────────────────────────────────
        when (activeTab) {
            GoalsTab.Queue -> QueueTab(
                taskQueue   = taskQueue,
                goalText    = goalText,
                appText     = appText,
                priorityLevel = priorityLevel,
                onGoalChange  = { goalText = it },
                onAppChange   = { appText  = it },
                onPriorityChange = { priorityLevel = it },
                onEnqueue     = {
                    if (goalText.isNotBlank()) {
                        vm.enqueueTask(goalText.trim(), appText.trim(), priorityLevel)
                        goalText = ""; appText = ""; priorityLevel = 0
                        focusManager.clearFocus()
                    }
                },
                onRemove      = { vm.removeQueuedTask(it) },
            )
            GoalsTab.Templates -> TemplatesTab(
                onEnqueue = { goal, app ->
                    vm.enqueueTask(goal, app, 0)
                    activeTab = GoalsTab.Queue
                }
            )
            GoalsTab.Triggers -> TriggersTab()
        }
    }
}

// ─── Queue tab ────────────────────────────────────────────────────────────────

@Composable
private fun QueueTab(
    taskQueue: List<QueuedTaskItem>,
    goalText: String,
    appText: String,
    priorityLevel: Int,
    onGoalChange: (String) -> Unit,
    onAppChange: (String) -> Unit,
    onPriorityChange: (Int) -> Unit,
    onEnqueue: () -> Unit,
    onRemove: (String) -> Unit,
) {
    LazyColumn(
        modifier        = Modifier.fillMaxSize(),
        contentPadding  = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Add goal card
        item {
            GoalComposerCard(
                goalText      = goalText,
                appText       = appText,
                priorityLevel = priorityLevel,
                onGoalChange  = onGoalChange,
                onAppChange   = onAppChange,
                onPriorityChange = onPriorityChange,
                onEnqueue     = onEnqueue,
            )
        }

        if (taskQueue.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = ARIAColors.Muted, modifier = Modifier.size(48.dp))
                        Text("Queue is empty", style = MaterialTheme.typography.bodyLarge.copy(color = ARIAColors.OnSurface, fontWeight = FontWeight.SemiBold))
                        Text("Add a goal above or pick from the Templates tab", style = MaterialTheme.typography.bodySmall.copy(color = ARIAColors.Muted), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        } else {
            item {
                Text(
                    "NEXT UP",
                    style = MaterialTheme.typography.labelSmall.copy(color = ARIAColors.Muted, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
                )
            }
            items(taskQueue, key = { it.id }) { task ->
                QueueTaskRow(task = task, onRemove = { onRemove(task.id) })
            }
        }
    }
}

@Composable
private fun GoalComposerCard(
    goalText: String,
    appText: String,
    priorityLevel: Int,
    onGoalChange: (String) -> Unit,
    onAppChange: (String) -> Unit,
    onPriorityChange: (Int) -> Unit,
    onEnqueue: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = ARIAColors.Surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("ADD GOAL", style = MaterialTheme.typography.labelSmall.copy(color = ARIAColors.Muted, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp))

            OutlinedTextField(
                value         = goalText,
                onValueChange = onGoalChange,
                modifier      = Modifier.fillMaxWidth(),
                placeholder   = { Text("What should ARIA do?", style = MaterialTheme.typography.bodySmall.copy(color = ARIAColors.Muted)) },
                colors        = goalsFieldColors(),
                shape         = RoundedCornerShape(8.dp),
                minLines      = 2,
                maxLines      = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )

            OutlinedTextField(
                value         = appText,
                onValueChange = onAppChange,
                modifier      = Modifier.fillMaxWidth(),
                placeholder   = { Text("App package (optional)", style = MaterialTheme.typography.bodySmall.copy(color = ARIAColors.Muted)) },
                colors        = goalsFieldColors(),
                shape         = RoundedCornerShape(8.dp),
                singleLine    = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                leadingIcon   = { Icon(Icons.Default.Apps, contentDescription = null, tint = ARIAColors.Muted, modifier = Modifier.size(16.dp)) },
            )

            // Priority selector
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Priority:", style = MaterialTheme.typography.bodySmall.copy(color = ARIAColors.Muted))
                listOf(0 to "Normal", 1 to "High", 2 to "Critical").forEach { (level, label) ->
                    val selected = priorityLevel == level
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (selected) when (level) {
                                    2 -> ARIAColors.Destructive.copy(alpha = 0.2f)
                                    1 -> ARIAColors.Warning.copy(alpha = 0.2f)
                                    else -> ARIAColors.Success.copy(alpha = 0.2f)
                                } else ARIAColors.SurfaceVariant
                            )
                            .border(
                                1.dp,
                                if (selected) when (level) {
                                    2 -> ARIAColors.Destructive.copy(alpha = 0.6f)
                                    1 -> ARIAColors.Warning.copy(alpha = 0.6f)
                                    else -> ARIAColors.Success.copy(alpha = 0.6f)
                                } else ARIAColors.Divider,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onPriorityChange(level) }
                            )
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (selected) when (level) {
                                    2 -> ARIAColors.Destructive
                                    1 -> ARIAColors.Warning
                                    else -> ARIAColors.Success
                                } else ARIAColors.Muted,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    }
                }
            }

            Button(
                onClick  = onEnqueue,
                enabled  = goalText.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(8.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = ARIAColors.Primary,
                    disabledContainerColor = ARIAColors.SurfaceVariant,
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add to Queue", fontWeight = FontWeight.SemiBold, color = if (goalText.isNotBlank()) ARIAColors.Background else ARIAColors.Muted)
            }
        }
    }
}

@Composable
private fun QueueTaskRow(task: QueuedTaskItem, onRemove: () -> Unit) {
    val fmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val priorityColor = when (task.priority) {
        2 -> ARIAColors.Destructive
        1 -> ARIAColors.Warning
        else -> ARIAColors.Success
    }
    val priorityLabel = when (task.priority) {
        2 -> "CRITICAL"
        1 -> "HIGH"
        else -> "NORMAL"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ARIAColors.Surface)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Priority indicator
        Box(
            modifier = Modifier
                .size(6.dp)
                .offset(y = 6.dp)
                .clip(CircleShape)
                .background(priorityColor)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(task.goal, style = MaterialTheme.typography.bodySmall.copy(color = ARIAColors.OnSurface), maxLines = 3)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (task.appPackage.isNotBlank()) {
                    Text(task.appPackage.substringAfterLast('.'), style = MaterialTheme.typography.labelSmall.copy(color = ARIAColors.Primary, fontSize = 10.sp))
                }
                Text(fmt.format(Date(task.enqueuedAt)), style = MaterialTheme.typography.labelSmall.copy(color = ARIAColors.Muted, fontSize = 10.sp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(priorityColor.copy(alpha = 0.15f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(priorityLabel, style = MaterialTheme.typography.labelSmall.copy(color = priorityColor, fontWeight = FontWeight.Bold, fontSize = 9.sp))
                }
            }
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Remove", tint = ARIAColors.Destructive, modifier = Modifier.size(16.dp))
        }
    }
}

// ─── Templates tab ────────────────────────────────────────────────────────────

@Composable
private fun TemplatesTab(onEnqueue: (goal: String, app: String) -> Unit) {
    var enqueuedLabel by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(enqueuedLabel) {
        if (enqueuedLabel != null) {
            kotlinx.coroutines.delay(1500)
            enqueuedLabel = null
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        enqueuedLabel?.let { label ->
            Row(
                modifier = Modifier.fillMaxWidth().background(ARIAColors.Success.copy(alpha = 0.12f)).padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ARIAColors.Success, modifier = Modifier.size(16.dp))
                Text("\"$label\" added to queue", style = MaterialTheme.typography.bodySmall.copy(color = ARIAColors.Success))
            }
        }

        LazyVerticalGrid(
            columns         = GridCells.Fixed(2),
            modifier        = Modifier.fillMaxSize(),
            contentPadding  = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement   = Arrangement.spacedBy(10.dp)
        ) {
            items(GOAL_TEMPLATES) { (title, goal, app) ->
                TemplateCard(title = title, goal = goal, app = app, onClick = {
                    onEnqueue(goal, app)
                    enqueuedLabel = title
                })
            }
        }
    }
}

@Composable
private fun TemplateCard(title: String, goal: String, app: String, onClick: () -> Unit) {
    Card(
        onClick   = onClick,
        modifier  = Modifier.fillMaxWidth().aspectRatio(1.2f),
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = ARIAColors.Surface),
        border    = androidx.compose.foundation.BorderStroke(1.dp, ARIAColors.Divider),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = ARIAColors.Primary, modifier = Modifier.size(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.bodySmall.copy(color = ARIAColors.OnSurface, fontWeight = FontWeight.SemiBold), maxLines = 2)
                Text(app.substringAfterLast('.'), style = MaterialTheme.typography.labelSmall.copy(color = ARIAColors.Muted, fontSize = 10.sp))
            }
        }
    }
}

// ─── Triggers tab ─────────────────────────────────────────────────────────────

@Composable
private fun TriggersTab() {
    val upcoming = listOf(
        Triple(Icons.Default.Schedule,       "Time-based",       "Run tasks at a specific time or on a schedule"),
        Triple(Icons.Default.Notifications,  "On Notification",  "Trigger when a notification arrives from a specific app"),
        Triple(Icons.Default.AppShortcut,    "On App Launch",    "Run a task whenever a specific app is opened"),
        Triple(Icons.Default.BatteryFull,    "On Charging",      "Queue a task to run when the device starts charging"),
        Triple(Icons.Default.Wifi,           "On Network",       "Trigger when connecting to a specific Wi-Fi network"),
        Triple(Icons.Default.LocationOn,     "On Location",      "Run a task when arriving at or leaving a location"),
    )

    LazyColumn(
        modifier        = Modifier.fillMaxSize(),
        contentPadding  = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ARIAColors.Primary.copy(alpha = 0.08f))
                    .border(1.dp, ARIAColors.Primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Construction, contentDescription = null, tint = ARIAColors.Primary, modifier = Modifier.size(18.dp))
                    Column {
                        Text("Triggers coming soon", style = MaterialTheme.typography.bodySmall.copy(color = ARIAColors.Primary, fontWeight = FontWeight.Bold))
                        Text("Automatic task scheduling is in development. Below is a preview of the trigger types planned.", style = MaterialTheme.typography.bodySmall.copy(color = ARIAColors.Muted))
                    }
                }
            }
        }

        items(upcoming) { (icon, title, desc) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ARIAColors.Surface)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ARIAColors.Muted.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = ARIAColors.Muted, modifier = Modifier.size(18.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodySmall.copy(color = ARIAColors.OnSurface, fontWeight = FontWeight.SemiBold))
                    Text(desc,  style = MaterialTheme.typography.bodySmall.copy(color = ARIAColors.Muted, lineHeight = 15.sp))
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(ARIAColors.Muted.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("SOON", style = MaterialTheme.typography.labelSmall.copy(color = ARIAColors.Muted, fontWeight = FontWeight.Bold, fontSize = 9.sp))
                }
            }
        }
    }
}

// ─── Field colors ─────────────────────────────────────────────────────────────

@Composable
private fun goalsFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor      = ARIAColors.Primary,
    unfocusedBorderColor    = ARIAColors.Divider,
    focusedContainerColor   = ARIAColors.SurfaceVariant,
    unfocusedContainerColor = ARIAColors.SurfaceVariant,
    focusedTextColor        = ARIAColors.OnSurface,
    unfocusedTextColor      = ARIAColors.OnSurface,
    cursorColor             = ARIAColors.Primary,
)
