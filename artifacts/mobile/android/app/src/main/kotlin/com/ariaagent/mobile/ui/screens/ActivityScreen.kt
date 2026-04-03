package com.ariaagent.mobile.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ariaagent.mobile.ui.viewmodel.ActionLogEntry
import com.ariaagent.mobile.ui.viewmodel.AgentViewModel
import com.ariaagent.mobile.ui.theme.ARIAColors
import java.text.SimpleDateFormat
import java.util.*

/**
 * ActivityScreen — scrollable list of all agent actions since session start.
 *
 * Receives entries via AgentEventBus → AgentViewModel.actionLogs StateFlow.
 * No bridge polling. Each action_performed event prepends a new entry in real-time.
 *
 * Phase 11 — pure Compose.
 */
@Composable
fun ActivityScreen(vm: AgentViewModel = viewModel()) {
    val logs   by vm.actionLogs.collectAsStateWithLifecycle()
    val agentState by vm.agentState.collectAsStateWithLifecycle()
    val streamBuffer by vm.streamBuffer.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ARIAColors.Background)
    ) {
        // Header
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                "ACTIVITY",
                style = MaterialTheme.typography.headlineMedium.copy(
                    color = ARIAColors.Primary,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                "${logs.size} actions recorded this session",
                style = MaterialTheme.typography.bodySmall.copy(color = ARIAColors.Muted)
            )
        }

        // Live token stream (only when running)
        if (agentState.status == "running" && streamBuffer.isNotBlank()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = ARIAColors.Primary.copy(alpha = 0.08f)),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "THINKING…",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = ARIAColors.Primary, letterSpacing = 1.sp
                        )
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        streamBuffer,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = ARIAColors.OnSurface,
                            lineHeight = 18.sp
                        ),
                        maxLines = 6
                    )
                }
            }
        }

        Divider(color = ARIAColors.Divider, thickness = 0.5.dp)

        if (logs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Timeline,
                        contentDescription = null,
                        tint = ARIAColors.Muted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No actions yet",
                        style = MaterialTheme.typography.bodyMedium.copy(color = ARIAColors.Muted)
                    )
                    Text(
                        "Start the agent to see actions here",
                        style = MaterialTheme.typography.bodySmall.copy(color = ARIAColors.Muted)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(logs, key = { it.id }) { entry ->
                    ActionLogRow(entry = entry)
                }
            }
        }
    }
}

@Composable
private fun ActionLogRow(entry: ActionLogEntry) {
    val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val timeStr = remember(entry.timestamp) { fmt.format(Date(entry.timestamp)) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Tool icon
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(toolColor(entry.tool).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                toolIcon(entry.tool),
                contentDescription = null,
                tint = toolColor(entry.tool),
                modifier = Modifier.size(16.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    entry.tool.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = toolColor(entry.tool),
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    timeStr,
                    style = MaterialTheme.typography.labelSmall.copy(color = ARIAColors.Muted)
                )
            }
            if (entry.nodeId.isNotBlank()) {
                Text(
                    entry.nodeId,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = ARIAColors.OnSurface,
                        fontSize = 11.sp
                    )
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (entry.appPackage.isNotBlank()) {
                    Text(
                        entry.appPackage.substringAfterLast('.'),
                        style = MaterialTheme.typography.labelSmall.copy(color = ARIAColors.Muted)
                    )
                }
                Text(
                    if (entry.success) "✓ +${String.format("%.2f", entry.reward)}"
                    else "✗ ${String.format("%.2f", entry.reward)}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (entry.success) ARIAColors.Success else ARIAColors.Error
                    )
                )
                Text(
                    "step ${entry.stepCount}",
                    style = MaterialTheme.typography.labelSmall.copy(color = ARIAColors.Muted)
                )
            }
        }
    }
}

private fun toolIcon(tool: String): ImageVector = when (tool.lowercase()) {
    "click", "tap"       -> Icons.Default.TouchApp
    "swipe", "scroll"    -> Icons.Default.SwipeVertical
    "type"               -> Icons.Default.Keyboard
    "back"               -> Icons.Default.ArrowBack
    "wait"               -> Icons.Default.HourglassEmpty
    else                 -> Icons.Default.SmartToy
}

private fun toolColor(tool: String): androidx.compose.ui.graphics.Color = when (tool.lowercase()) {
    "click", "tap"       -> ARIAColors.Primary
    "swipe", "scroll"    -> ARIAColors.Accent
    "type"               -> ARIAColors.Success
    "back"               -> ARIAColors.Warning
    else                 -> ARIAColors.Muted
}
