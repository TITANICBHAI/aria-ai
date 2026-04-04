package com.ariaagent.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ariaagent.mobile.ui.viewmodel.AgentViewModel
import com.ariaagent.mobile.ui.viewmodel.AppSkillItem
import com.ariaagent.mobile.ui.theme.ARIAColors

/**
 * ModulesScreen — hardware/software module status panel.
 *
 * Shows:
 *  • LLM (Llama 3.2-1B Q4_K_M)
 *  • OCR (ML Kit)
 *  • Object Detector (EfficientDet-Lite0 INT8)
 *  • Vector Memory (ONNX MiniLM)
 *  • Object Label Store
 *  • Permissions (Accessibility + Screen Capture)
 *  • On-device Learning (LoRA version, policy steps, Adam optimizer metrics)
 *  • [Phase 15] App Skills — per-app success rates and learned elements
 *
 * Phase 11 — pure Compose. Phase 15 update: app skills + RL metrics.
 */
@Composable
fun ModulesScreen(vm: AgentViewModel = viewModel()) {
    val modules   by vm.moduleState.collectAsStateWithLifecycle()
    val learning  by vm.learningState.collectAsStateWithLifecycle()
    val appSkills by vm.appSkills.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ARIAColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "MODULES",
                style = MaterialTheme.typography.headlineMedium.copy(
                    color = ARIAColors.Primary,
                    fontWeight = FontWeight.Bold
                )
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { vm.refreshAppSkills() }) {
                    Icon(Icons.Default.Psychology, contentDescription = "Refresh skills",
                        tint = ARIAColors.Muted, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { vm.refreshModuleState() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh modules",
                        tint = ARIAColors.Muted)
                }
            }
        }

        // ── LLM ──────────────────────────────────────────────────────────────
        ModuleCard(
            icon = Icons.Default.Psychology,
            title = "Llama 3.2-1B Q4_K_M",
            subtitle = "Primary reasoning engine  •  ~800 MB",
            status = when {
                modules.modelLoaded -> ModuleStatus.ACTIVE
                modules.modelReady  -> ModuleStatus.READY
                else                -> ModuleStatus.MISSING
            },
            detail = if (modules.tokensPerSecond > 0)
                "${String.format("%.1f", modules.tokensPerSecond)} tok/s  •  LoRA v${modules.loraVersion}" else null
        )

        // ── OCR ───────────────────────────────────────────────────────────────
        ModuleCard(
            icon = Icons.Default.TextFields,
            title = "ML Kit OCR",
            subtitle = "On-device text recognition  •  bundled",
            status = if (modules.ocrReady) ModuleStatus.READY else ModuleStatus.MISSING
        )

        // ── Object Detector ───────────────────────────────────────────────────
        ModuleCard(
            icon = Icons.Default.Visibility,
            title = "EfficientDet-Lite0 INT8",
            subtitle = "Screen object detection  •  ~4.4 MB",
            status = if (modules.detectorReady) ModuleStatus.READY else ModuleStatus.MISSING,
            detail = if (modules.detectorSizeMb > 0)
                "${String.format("%.1f", modules.detectorSizeMb)} MB downloaded" else "Not downloaded"
        )

        // ── Vector Memory ─────────────────────────────────────────────────────
        ModuleCard(
            icon = Icons.Default.DataObject,
            title = "MiniLM Embedding (ONNX)",
            subtitle = "Experience memory retrieval  •  ~22 MB",
            status = ModuleStatus.READY,
            detail = "${modules.embeddingCount} experiences  •  ${modules.episodesRun} episodes run"
        )

        // ── Object Label Store ────────────────────────────────────────────────
        ModuleCard(
            icon = Icons.Default.Label,
            title = "Object Label Store",
            subtitle = "Custom screen element labels",
            status = ModuleStatus.READY,
            detail = "${modules.labelCount} label${if (modules.labelCount == 1) "" else "s"} defined"
        )

        // ── Permissions ───────────────────────────────────────────────────────
        ARIACard {
            Text("PERMISSIONS", style = MaterialTheme.typography.labelSmall.copy(color = ARIAColors.Muted))
            Spacer(Modifier.height(8.dp))
            PermissionRow(
                icon = Icons.Default.Accessibility,
                label = "Accessibility Service",
                granted = modules.accessibilityGranted
            )
            Spacer(Modifier.height(6.dp))
            PermissionRow(
                icon = Icons.Default.Screenshot,
                label = "Screen Capture",
                granted = modules.screenCaptureGranted
            )
        }

        // ── On-device learning ────────────────────────────────────────────────
        ARIACard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "ON-DEVICE LEARNING",
                    style = MaterialTheme.typography.labelSmall.copy(color = ARIAColors.Muted)
                )
                Icon(
                    Icons.Default.School,
                    contentDescription = null,
                    tint = ARIAColors.Accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.height(10.dp))

            // Main version metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                RlMetric("LoRA",     "v${learning.loraVersion}")
                RlMetric("Policy",   "v${learning.policyVersion}")
                RlMetric("Adapter",  if (modules.adapterLoaded) "LOADED" else "NONE")
                RlMetric("Samples",  "${learning.untrainedSamples}")
            }

            if (learning.adamStep > 0) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = ARIAColors.Divider)
                Spacer(Modifier.height(8.dp))
                Text(
                    "REINFORCE OPTIMIZER",
                    style = MaterialTheme.typography.labelSmall.copy(color = ARIAColors.Muted)
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    RlMetric("Adam Step", "${learning.adamStep}")
                    if (learning.lastPolicyLoss > 0.0) {
                        RlMetric("Policy Loss", String.format("%.5f", learning.lastPolicyLoss))
                    }
                    RlMetric("Episodes", "${modules.episodesRun}")
                }
            }
        }

        // ── Phase 15: App Skills ──────────────────────────────────────────────
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
                        Icons.Default.AppRegistration,
                        contentDescription = null,
                        tint = ARIAColors.Accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "APP SKILLS",
                        style = MaterialTheme.typography.labelSmall.copy(color = ARIAColors.Muted)
                    )
                    if (appSkills.isNotEmpty()) {
                        Text(
                            "(${appSkills.size})",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = ARIAColors.Accent,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
                if (appSkills.isNotEmpty()) {
                    TextButton(
                        onClick = { vm.clearAppSkills() },
                        colors = ButtonDefaults.textButtonColors(contentColor = ARIAColors.Error)
                    ) {
                        Text("Reset", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (appSkills.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "No app skills yet. ARIA learns per-app knowledge after each completed task.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = ARIAColors.Muted,
                        lineHeight = 18.sp
                    )
                )
            } else {
                Spacer(Modifier.height(8.dp))
                appSkills.take(8).forEachIndexed { index, skill ->
                    AppSkillRow(skill = skill)
                    if (index < appSkills.size - 1 && index < 7) {
                        HorizontalDivider(
                            color = ARIAColors.Divider.copy(alpha = 0.4f),
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }
                if (appSkills.size > 8) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "… and ${appSkills.size - 8} more apps",
                        style = MaterialTheme.typography.labelSmall.copy(color = ARIAColors.Muted)
                    )
                }
            }
        }
    }
}

// ─── Private composables ──────────────────────────────────────────────────────

private enum class ModuleStatus { ACTIVE, READY, MISSING }

@Composable
private fun ModuleCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    status: ModuleStatus,
    detail: String? = null
) {
    val (statusColor, statusLabel) = when (status) {
        ModuleStatus.ACTIVE  -> ARIAColors.Primary to "ACTIVE"
        ModuleStatus.READY   -> ARIAColors.Success  to "READY"
        ModuleStatus.MISSING -> ARIAColors.Error     to "MISSING"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ARIAColors.Surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(statusColor.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = statusColor, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium.copy(
                    color = ARIAColors.OnSurface, fontWeight = FontWeight.SemiBold))
                Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(color = ARIAColors.Muted))
                if (detail != null) {
                    Text(detail, style = MaterialTheme.typography.bodySmall.copy(
                        color = statusColor, fontSize = 11.sp))
                }
            }
            Text(
                statusLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = statusColor, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp
                )
            )
        }
    }
}

@Composable
private fun PermissionRow(icon: ImageVector, label: String, granted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (granted) ARIAColors.Success else ARIAColors.Error,
                modifier = Modifier.size(18.dp)
            )
            Text(label, style = MaterialTheme.typography.bodySmall.copy(color = ARIAColors.OnSurface))
        }
        Text(
            if (granted) "GRANTED" else "DENIED",
            style = MaterialTheme.typography.labelSmall.copy(
                color = if (granted) ARIAColors.Success else ARIAColors.Error,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
private fun RlMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(
                color = ARIAColors.Accent,
                fontWeight = FontWeight.Bold
            )
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = ARIAColors.Muted,
                fontSize = 10.sp
            )
        )
    }
}

@Composable
private fun AppSkillRow(skill: AppSkillItem) {
    val totalTasks = skill.taskSuccess + skill.taskFailure
    val rateColor = when {
        skill.successRate >= 0.75f -> ARIAColors.Success
        skill.successRate >= 0.50f -> ARIAColors.Warning
        else                       -> ARIAColors.Error
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .size(36.dp)
                .background(rateColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.PhoneAndroid,
                contentDescription = null,
                tint = rateColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                skill.appName,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = ARIAColors.OnSurface,
                    fontWeight = FontWeight.SemiBold
                )
            )
            Text(
                skill.appPackage,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = ARIAColors.Muted,
                    fontSize = 10.sp
                ),
                maxLines = 1
            )
            if (skill.learnedElements.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    skill.learnedElements.take(3).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = ARIAColors.Muted,
                        fontSize = 10.sp
                    ),
                    maxLines = 1
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${(skill.successRate * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = rateColor,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                "$totalTasks tasks",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = ARIAColors.Muted,
                    fontSize = 10.sp
                )
            )
            if (skill.avgSteps > 0f) {
                Text(
                    "${String.format("%.1f", skill.avgSteps)} steps/task",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = ARIAColors.Muted,
                        fontSize = 10.sp
                    )
                )
            }
        }
    }
}
