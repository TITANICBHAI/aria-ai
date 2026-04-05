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
fun ModulesScreen(
    vm: AgentViewModel = viewModel(),
    onRequestScreenCapture: () -> Unit = {},
    onGrantAccessibility: () -> Unit = {},
) {
    val modules              by vm.moduleState.collectAsStateWithLifecycle()
    val learning             by vm.learningState.collectAsStateWithLifecycle()
    val appSkills            by vm.appSkills.collectAsStateWithLifecycle()
    val llmDownloading       by vm.llmDownloading.collectAsStateWithLifecycle()
    val detectorDownloading  by vm.detectorDownloading.collectAsStateWithLifecycle()
    val embeddingDownloading by vm.embeddingDownloading.collectAsStateWithLifecycle()
    val visionDownloading    by vm.visionDownloading.collectAsStateWithLifecycle()
    val visionLoading        by vm.visionLoading.collectAsStateWithLifecycle()
    val sam2Downloading      by vm.sam2Downloading.collectAsStateWithLifecycle()
    val sam2Loading          by vm.sam2Loading.collectAsStateWithLifecycle()

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
        val llmStatus = when {
            modules.modelLoaded -> ModuleStatus.ACTIVE
            modules.modelReady  -> ModuleStatus.READY
            else                -> ModuleStatus.MISSING
        }
        ModuleCard(
            icon = Icons.Default.Psychology,
            title = "Llama 3.2-1B Q4_K_M",
            subtitle = "Primary reasoning engine  •  ~800 MB",
            status = llmStatus,
            detail = when {
                modules.tokensPerSecond > 0 -> "${String.format("%.1f", modules.tokensPerSecond)} tok/s  •  LoRA v${modules.loraVersion}"
                llmDownloading && modules.llmDownloadPercent > 0 ->
                    "${modules.llmDownloadPercent}%  •  ${String.format("%.0f", modules.llmDownloadMb)} / ${String.format("%.0f", modules.llmDownloadTotalMb)} MB  •  ${String.format("%.1f", modules.llmDownloadSpeedMbps)} MB/s"
                else -> null
            },
            downloadProgress = if (llmDownloading && modules.llmDownloadPercent > 0) modules.llmDownloadPercent / 100f else null,
            downloadError = modules.llmDownloadError,
            onDownload = if (llmStatus == ModuleStatus.MISSING && !llmDownloading) {{ vm.downloadLlmModel() }} else null,
            downloading = llmDownloading,
            onUnload = if (modules.modelLoaded) {{ vm.unloadLlmModel() }} else null,
        )

        // ── OCR ───────────────────────────────────────────────────────────────
        ModuleCard(
            icon = Icons.Default.TextFields,
            title = "ML Kit OCR",
            subtitle = "On-device text recognition  •  bundled",
            status = if (modules.ocrReady) ModuleStatus.READY else ModuleStatus.MISSING
        )

        // ── Object Detector ───────────────────────────────────────────────────
        val detectorStatus = if (modules.detectorReady) ModuleStatus.READY else ModuleStatus.MISSING
        ModuleCard(
            icon = Icons.Default.Visibility,
            title = "EfficientDet-Lite0 INT8",
            subtitle = "Screen object detection  •  ~4.4 MB",
            status = detectorStatus,
            detail = if (modules.detectorSizeMb > 0)
                "${String.format("%.1f", modules.detectorSizeMb)} MB downloaded" else "Not downloaded",
            onDownload = if (detectorStatus == ModuleStatus.MISSING) {{ vm.downloadDetectorModel() }} else null,
            downloading = detectorDownloading,
        )

        // ── Vision Model (Phase 17) ───────────────────────────────────────────
        val visionStatus = when {
            modules.visionLoaded  -> ModuleStatus.ACTIVE
            modules.visionReady   -> ModuleStatus.READY
            else                  -> ModuleStatus.MISSING
        }
        val visionDetail = when {
            modules.visionLoaded ->
                "SmolVLM-256M active  •  ${String.format("%.0f", modules.visionModelDownloadedMb + modules.mmProjDownloadedMb)} MB"
            modules.visionReady  ->
                "Model ready  •  ${String.format("%.0f", modules.visionModelDownloadedMb)} MB + ${String.format("%.0f", modules.mmProjDownloadedMb)} MB mmproj"
            visionDownloading && modules.visionDownloadPercent > 0 ->
                "${modules.visionDownloadPercent}%  •  base + mmproj downloading"
            else -> "~200 MB total  •  not downloaded"
        }
        ModuleCard(
            icon = Icons.Default.RemoveRedEye,
            title = "SmolVLM-256M Multimodal",
            subtitle = "Pixel-level screen understanding  •  ~200 MB",
            status = visionStatus,
            detail = visionDetail,
            downloadProgress = if (visionDownloading && modules.visionDownloadPercent > 0)
                modules.visionDownloadPercent / 100f else null,
            downloadError = modules.visionDownloadError,
            onDownload = if (visionStatus == ModuleStatus.MISSING && !visionDownloading)
                {{ vm.downloadVisionModel() }} else null,
            downloading = visionDownloading,
            onLoad = if (modules.visionReady && !modules.visionLoaded && !visionLoading && !visionDownloading)
                {{ vm.loadVisionModel() }} else null,
            loading = visionLoading,
            onUnload = if (modules.visionLoaded) {{ vm.unloadVisionModel() }} else null,
        )

        // ── SAM2 / MobileSAM pixel segmentation (Phase 18) ───────────────────
        val sam2Status = when {
            modules.sam2Loaded -> ModuleStatus.ACTIVE
            modules.sam2Ready  -> ModuleStatus.READY
            else               -> ModuleStatus.MISSING
        }
        val sam2Detail = when {
            modules.sam2Loaded ->
                "MobileSAM active  •  ${String.format("%.0f", modules.sam2DownloadedMb)} MB"
            modules.sam2Ready  ->
                "Encoder ready  •  ${String.format("%.0f", modules.sam2DownloadedMb)} MB"
            sam2Downloading && modules.sam2DownloadPercent > 0 ->
                "${modules.sam2DownloadPercent}%  •  encoder downloading"
            modules.sam2DownloadedMb > 0 ->
                "${String.format("%.0f", modules.sam2DownloadedMb)} MB  •  download incomplete"
            else -> "~22 MB  •  not downloaded"
        }
        ModuleCard(
            icon = Icons.Default.CropFree,
            title = "MobileSAM (ViT-Tiny)",
            subtitle = "Pixel segmentation for game / Flutter screens  •  ~22 MB",
            status = sam2Status,
            detail = sam2Detail,
            downloadProgress = if (sam2Downloading && modules.sam2DownloadPercent > 0)
                modules.sam2DownloadPercent / 100f else null,
            downloadError = modules.sam2DownloadError,
            onDownload = if (sam2Status == ModuleStatus.MISSING && !sam2Downloading)
                {{ vm.downloadSam2Model() }} else null,
            downloading = sam2Downloading,
            onLoad = if (modules.sam2Ready && !modules.sam2Loaded && !sam2Loading && !sam2Downloading)
                {{ vm.loadSam2Model() }} else null,
            loading = sam2Loading,
            onUnload = if (modules.sam2Loaded) {{ vm.unloadSam2Model() }} else null,
        )

        // ── Vector Memory / Embedding model ──────────────────────────────────
        val embStatus = when {
            modules.embeddingReady && modules.embeddingVocabReady -> ModuleStatus.READY
            else                                                   -> ModuleStatus.MISSING
        }
        ModuleCard(
            icon = Icons.Default.DataObject,
            title = "MiniLM Embedding (ONNX)",
            subtitle = "Semantic memory search  •  ~23 MB",
            status = embStatus,
            detail = when {
                embStatus == ModuleStatus.READY ->
                    "${modules.embeddingCount} experiences  •  ${modules.episodesRun} episodes run"
                modules.embeddingDownloadedMb > 0 ->
                    "${String.format("%.1f", modules.embeddingDownloadedMb)} MB downloaded"
                else -> "Model not downloaded — memory search disabled"
            },
            onDownload = if (embStatus == ModuleStatus.MISSING && !embeddingDownloading) {{ vm.downloadEmbeddingModel() }} else null,
            downloading = embeddingDownloading,
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
                icon    = Icons.Default.Accessibility,
                label   = "Accessibility Service",
                granted = modules.accessibilityGranted,
                onGrant = if (!modules.accessibilityGranted) onGrantAccessibility else null,
            )
            Spacer(Modifier.height(6.dp))
            PermissionRow(
                icon = Icons.Default.Screenshot,
                label = "Screen Capture",
                granted = modules.screenCaptureGranted,
                onGrant = if (!modules.screenCaptureGranted) onRequestScreenCapture else null,
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
    detail: String? = null,
    onDownload: (() -> Unit)? = null,
    downloading: Boolean = false,
    downloadProgress: Float? = null,
    downloadError: String? = null,
    onLoad: (() -> Unit)? = null,
    loading: Boolean = false,
    onUnload: (() -> Unit)? = null,
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
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
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
            // Download progress bar (real % from ModelDownloadService)
            if (downloadProgress != null) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress          = { downloadProgress },
                    modifier          = Modifier.fillMaxWidth(),
                    color             = ARIAColors.Primary,
                    trackColor        = ARIAColors.Divider,
                )
            }
            // Download error
            if (downloadError != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    downloadError,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = ARIAColors.Error, fontSize = 11.sp
                    )
                )
            }
            // Download button (shown only when MISSING)
            if (onDownload != null) {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick   = onDownload,
                    enabled   = !downloading,
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = RoundedCornerShape(8.dp),
                    colors    = ButtonDefaults.buttonColors(
                        containerColor         = ARIAColors.Error,
                        disabledContainerColor = ARIAColors.Error.copy(alpha = 0.4f),
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    if (downloading) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color       = Color.White,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Downloading…", fontSize = 12.sp, color = Color.White)
                    } else {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint     = Color.White,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Download", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            // Load into RAM button (shown when downloaded but not yet loaded)
            if (onLoad != null) {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick   = onLoad,
                    enabled   = !loading,
                    modifier  = Modifier.fillMaxWidth(),
                    shape     = RoundedCornerShape(8.dp),
                    colors    = ButtonDefaults.buttonColors(
                        containerColor         = ARIAColors.Primary,
                        disabledContainerColor = ARIAColors.Primary.copy(alpha = 0.4f),
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color       = Color.White,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Loading into RAM…", fontSize = 12.sp, color = Color.White)
                    } else {
                        Icon(
                            Icons.Default.Memory,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint     = Color.White,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Load into RAM", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            // Unload button (shown only when ACTIVE/READY and model is loaded)
            if (onUnload != null) {
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick        = onUnload,
                    modifier       = Modifier.fillMaxWidth(),
                    shape          = RoundedCornerShape(8.dp),
                    colors         = ButtonDefaults.outlinedButtonColors(contentColor = ARIAColors.Warning),
                    border         = androidx.compose.foundation.BorderStroke(1.dp, ARIAColors.Warning.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Free RAM (Unload)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    label: String,
    granted: Boolean,
    onGrant: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (granted) ARIAColors.Success else ARIAColors.Error,
                modifier = Modifier.size(18.dp)
            )
            Text(label, style = MaterialTheme.typography.bodySmall.copy(color = ARIAColors.OnSurface))
        }
        if (!granted && onGrant != null) {
            TextButton(
                onClick        = onGrant,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) {
                Text(
                    "GRANT",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = ARIAColors.Primary, fontWeight = FontWeight.Bold
                    )
                )
            }
        } else {
            Text(
                if (granted) "GRANTED" else "DENIED",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = if (granted) ARIAColors.Success else ARIAColors.Error,
                    fontWeight = FontWeight.Bold
                )
            )
        }
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
