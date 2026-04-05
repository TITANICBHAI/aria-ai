package com.ariaagent.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ariaagent.mobile.ui.viewmodel.ActionLogEntry
import com.ariaagent.mobile.ui.viewmodel.AgentViewModel
import com.ariaagent.mobile.ui.viewmodel.MemoryEntry
import com.ariaagent.mobile.ui.theme.ARIAColors
import java.text.SimpleDateFormat
import java.util.*

/**
 * ActivityScreen — Actions tab + Memory tab with full feature parity to logs.tsx.
 *
 * RN reference: app/(tabs)/logs.tsx — 317 lines
 * DO NOT DELETE logs.tsx until this screen is verified on emulator. (Migration Phase 3)
 *
 * Features matched from logs.tsx:
 *   - Tab bar: Actions | Memory
 *   - Actions tab: live action log list with tool icon, description, app, timestamp, reward
 *   - Memory tab: ExperienceStore entries with summary, app, usage, confidence
 *   - Clear memory button (visible in memory tab when entries exist)
 *   - Per-tab empty state with different icon + message
 *   - Left-border colour coding (green = success, red = failure, violet = memory)
 *
 * Features added beyond RN:
 *   - Live "THINKING…" token stream card (shown during inference)
 *   - Step count badge in header
 */
@Composable
fun ActivityScreen(vm: AgentViewModel = viewModel()) {
    val actionLogs    by vm.actionLogs.collectAsStateWithLifecycle()
    val memoryEntries by vm.memoryEntries.collectAsStateWithLifecycle()
    val agentState    by vm.agentState.collectAsStateWithLifecycle()
    val streamBuffer  by vm.streamBuffer.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(ActivityTab.Actions) }
    var showClearConfirm by remember { mutableStateOf(false) }

    // Refresh memory entries whenever screen becomes active
    LaunchedEffect(Unit) { vm.refreshMemoryEntries() }

    // Clear memory confirmation dialog
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor   = ARIAColors.Surface,
            title = {
                Text(
                    "Clear Memory?",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = ARIAColors.OnSurface, fontWeight = FontWeight.Bold
                    )
                )
            },
            text = {
                Text(
                    "All experience store entries will be deleted. This cannot be undone.",
                    style = MaterialTheme.typography.bodySmall.copy(color = ARIAColors.Muted)
                )
            },
            confirmButton = {
                Button(
                    onClick = { showClearConfirm = false; vm.clearMemory() },
                    colors  = ButtonDefaults.buttonColors(containerColor = ARIAColors.Destructive),
                    shape   = RoundedCornerShape(8.dp)
                ) { Text("Clear", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel", color = ARIAColors.Muted)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ARIAColors.Background)
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    "ACTIVITY",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color      = ARIAColors.Primary,
                        fontWeight = FontWeight.Bold
                    )
                )
                // Clear button — only in memory tab with entries
                if (activeTab == ActivityTab.Memory && memoryEntries.isNotEmpty()) {
                    IconButton(
                        onClick = { showClearConfirm = true },
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(ARIAColors.Destructive.copy(alpha = 0.13f))
                            .size(38.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "Clear memory",
                            tint     = ARIAColors.Destructive,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Tab bar ───────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ARIAColors.Surface),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                ActivityTab.entries.forEach { tab ->
                    val selected = activeTab == tab
                    val count    = if (tab == ActivityTab.Actions) actionLogs.size
                                   else memoryEntries.size
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) ARIAColors.Primary else Color.Transparent)
                            .clickableNoRipple { activeTab = tab }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text(
                                tab.label,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color      = if (selected) ARIAColors.Background
                                                 else ARIAColors.Muted,
                                    fontWeight = if (selected) FontWeight.Bold
                                                 else FontWeight.Normal
                                )
                            )
                            if (count > 0) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(
                                            if (selected) ARIAColors.Background.copy(alpha = 0.25f)
                                            else ARIAColors.Primary.copy(alpha = 0.18f)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        "$count",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color      = if (selected) ARIAColors.Background
                                                         else ARIAColors.Primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Live thinking stream (beyond RN — shown only during inference) ────
        if (agentState.status == "running" && streamBuffer.isNotBlank()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
                shape  = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = ARIAColors.Primary.copy(alpha = 0.08f)
                ),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "THINKING…",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color         = ARIAColors.Primary,
                            letterSpacing = 1.sp
                        )
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        streamBuffer,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color      = ARIAColors.OnSurface,
                            lineHeight = 18.sp
                        ),
                        maxLines = 6
                    )
                }
            }
        }

        HorizontalDivider(color = ARIAColors.Divider, thickness = 0.5.dp)

        // ── Content per tab ───────────────────────────────────────────────────
        when (activeTab) {
            ActivityTab.Actions -> ActionsList(logs = actionLogs)
            ActivityTab.Memory  -> MemoryList(entries = memoryEntries)
        }
    }
}

// ─── Actions tab ──────────────────────────────────────────────────────────────

@Composable
private fun ActionsList(logs: List<ActionLogEntry>) {
    if (logs.isEmpty()) {
        EmptyState(
            icon    = Icons.Default.Timeline,
            title   = "No actions yet",
            message = "Start the agent to see its actions here"
        )
    } else {
        LazyColumn(
            modifier        = Modifier.fillMaxSize(),
            contentPadding  = PaddingValues(horizontal = 16.dp, vertical = 8.dp, ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(logs, key = { it.id }) { entry ->
                ActionLogRow(entry = entry)
            }
        }
    }
}

@Composable
private fun ActionLogRow(entry: ActionLogEntry) {
    val fmt     = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val timeStr = remember(entry.timestamp) { fmt.format(Date(entry.timestamp)) }

    val borderColor = if (entry.success) ARIAColors.Success else ARIAColors.Destructive
    val iconColor   = if (entry.success) ARIAColors.Success else ARIAColors.Destructive

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ARIAColors.Surface)
            .drawLeftBorder(color = borderColor, width = 3.dp)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment     = Alignment.Top
    ) {
        // Tool icon
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                toolIcon(entry.tool),
                contentDescription = null,
                tint     = iconColor,
                modifier = Modifier.size(16.dp)
            )
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            // Description
            if (entry.nodeId.isNotBlank()) {
                Text(
                    entry.nodeId,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color      = ARIAColors.OnSurface,
                        lineHeight = 18.sp
                    )
                )
            }
            // Meta row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    entry.tool.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color      = iconColor,
                        fontWeight = FontWeight.Bold
                    )
                )
                if (entry.appPackage.isNotBlank()) {
                    Text(
                        entry.appPackage.substringAfterLast('.'),
                        style = MaterialTheme.typography.labelSmall.copy(color = ARIAColors.Primary)
                    )
                }
                Text(
                    timeStr,
                    style = MaterialTheme.typography.labelSmall.copy(color = ARIAColors.Muted)
                )
                // Reward signal
                Text(
                    if (entry.success) "r=+${"%.2f".format(entry.reward)}"
                    else               "r=${"%.2f".format(entry.reward)}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color      = iconColor,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                )
            }
        }
    }
}

// ─── Memory tab ───────────────────────────────────────────────────────────────

@Composable
private fun MemoryList(entries: List<MemoryEntry>) {
    if (entries.isEmpty()) {
        EmptyState(
            icon    = Icons.Default.Book,
            title   = "Memory is empty",
            message = "The agent will store learned patterns here"
        )
    } else {
        LazyColumn(
            modifier        = Modifier.fillMaxSize(),
            contentPadding  = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(entries, key = { it.id }) { entry ->
                MemoryRow(entry = entry)
            }
        }
    }
}

@Composable
private fun MemoryRow(entry: MemoryEntry) {
    val fmt     = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val timeStr = remember(entry.timestamp) { fmt.format(Date(entry.timestamp)) }
    val confPct = (entry.reward.coerceIn(0.0, 1.0) * 100).toInt()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ARIAColors.Surface)
            .drawLeftBorder(color = ARIAColors.Accent, width = 3.dp)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment     = Alignment.Top
    ) {
        // Icon
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ARIAColors.Accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Book,
                contentDescription = null,
                tint     = ARIAColors.Accent,
                modifier = Modifier.size(16.dp)
            )
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                entry.summary.take(120),
                style = MaterialTheme.typography.bodySmall.copy(
                    color      = ARIAColors.OnSurface,
                    lineHeight = 18.sp
                )
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    entry.app,
                    style = MaterialTheme.typography.labelSmall.copy(color = ARIAColors.Primary)
                )
                Text(
                    timeStr,
                    style = MaterialTheme.typography.labelSmall.copy(color = ARIAColors.Muted)
                )
                // Confidence / reward indicator
                Text(
                    "$confPct% conf",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color      = if (entry.result == "success") ARIAColors.Success
                                     else ARIAColors.Destructive,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                )
                if (entry.isEdgeCase) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(ARIAColors.Warning.copy(alpha = 0.18f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            "EDGE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color      = ARIAColors.Warning,
                                fontWeight = FontWeight.Bold,
                                fontSize   = 9.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

// ─── Shared composables ───────────────────────────────────────────────────────

@Composable
private fun EmptyState(icon: ImageVector, title: String, message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 40.dp)
        ) {
            Icon(icon, contentDescription = null, tint = ARIAColors.Muted, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = ARIAColors.OnSurface, fontWeight = FontWeight.SemiBold
                )
            )
            Text(
                message,
                style = MaterialTheme.typography.bodySmall.copy(
                    color      = ARIAColors.Muted,
                    lineHeight = 18.sp
                ),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private enum class ActivityTab(val label: String) {
    Actions("Actions"),
    Memory("Memory"),
}

private fun toolIcon(tool: String): ImageVector = when (tool.lowercase()) {
    "click", "tap"    -> Icons.Default.TouchApp
    "swipe", "scroll" -> Icons.Default.SwipeVertical
    "type"            -> Icons.Default.Keyboard
    "back"            -> Icons.Default.ArrowBack
    "wait"            -> Icons.Default.HourglassEmpty
    "intent"          -> Icons.Default.Share
    "observe"         -> Icons.Default.RemoveRedEye
    else              -> Icons.Default.SmartToy
}

// Draw a left border using a Modifier DrawBehind
private fun Modifier.drawLeftBorder(color: Color, width: Dp): Modifier =
    this.then(
        Modifier.drawBehind {
            drawRect(
                color   = color,
                size    = Size(width.toPx(), size.height),
                topLeft = Offset.Zero
            )
        }
    )

// Tap without ink ripple for the tab bar
@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this.then(
        Modifier.clickable(
            interactionSource = interactionSource,
            indication        = null,
            onClick           = onClick
        )
    )
}
