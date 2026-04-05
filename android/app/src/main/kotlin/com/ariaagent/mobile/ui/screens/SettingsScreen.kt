package com.ariaagent.mobile.ui.screens

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ariaagent.mobile.core.config.AriaConfig
import com.ariaagent.mobile.ui.viewmodel.AgentViewModel
import com.ariaagent.mobile.ui.theme.ARIAColors

/**
 * SettingsScreen — full feature-parity with settings.tsx (852 lines).
 *
 * RN reference: app/(tabs)/settings.tsx
 * DO NOT DELETE settings.tsx until this screen is verified on emulator. (Migration Phase 2)
 *
 * Features matched:
 *   - Editable model path field
 *   - Quantization chip selector (Q4_K_M / Q4_0 / IQ2_S / Q5_K_M)
 *   - Context window chip selector (512 / 1024 / 2048 / 4096)
 *   - GPU layers chip selector (0=CPU / 8 / 16 / 24 / 32)
 *   - Temperature preset buttons (0.1 / 0.3 / 0.5 / 0.7 / 0.9)
 *   - RL enabled toggle
 *   - Editable LoRA adapter path field
 *   - Permissions section (Accessibility, Notifications, Screen Capture)
 *   - Save configuration button with success feedback
 *
 * Features added beyond RN:
 *   - System info row (device model + Android API level)
 *   - Clear Memory button (ExperienceStore.clearAll) with confirmation dialog
 *   - Reset Agent button (ProgressPersistence.clearProgress) with confirmation dialog
 *
 * TODO (Phase 10): Web Dashboard / Local Monitoring Server section.
 *   The server backend currently lives in bridge/AgentCoreModule.kt (RN bridge).
 *   Extract it into a standalone LocalMonitoringServer.kt before adding the toggle here.
 */
@Composable
fun SettingsScreen(vm: AgentViewModel = viewModel()) {
    val context      = LocalContext.current
    val focusManager = LocalFocusManager.current

    val config      by vm.config.collectAsStateWithLifecycle()
    val moduleState by vm.moduleState.collectAsStateWithLifecycle()

    // ── Local draft — applied only when Save is tapped ────────────────────────
    var modelPath       by remember(config.modelPath)        { mutableStateOf(config.modelPath) }
    var quantization    by remember(config.quantization)     { mutableStateOf(config.quantization) }
    var contextWindow   by remember(config.contextWindow)    { mutableStateOf(config.contextWindow) }
    var nGpuLayers      by remember(config.nGpuLayers)       { mutableStateOf(config.nGpuLayers) }
    var temperatureX100 by remember(config.temperatureX100)  { mutableStateOf(config.temperatureX100) }
    var rlEnabled       by remember(config.rlEnabled)        { mutableStateOf(config.rlEnabled) }
    var loraPath        by remember(config.loraAdapterPath)  { mutableStateOf(config.loraAdapterPath ?: "") }

    // ── Permission state — checked live via DisposableEffect + moduleState ────
    val accessibilityGranted = moduleState.accessibilityGranted
    val screenCaptureGranted = moduleState.screenCaptureGranted
    var notificationsGranted by remember { mutableStateOf(false) }

    // Refresh notification permission state each time screen composes
    LaunchedEffect(Unit) {
        notificationsGranted = NotificationManagerCompat.from(context).areNotificationsEnabled()
        vm.refreshModuleState()
    }

    // ── Save feedback ─────────────────────────────────────────────────────────
    var saveSuccess by remember { mutableStateOf(false) }

    // ── Danger-zone dialogs ───────────────────────────────────────────────────
    var showClearMemoryDialog  by remember { mutableStateOf(false) }
    var showResetAgentDialog   by remember { mutableStateOf(false) }

    if (showClearMemoryDialog) {
        ConfirmDialog(
            title   = "Clear Memory?",
            message = "This will permanently delete all experience store entries and embedding memories. The model weights are not affected.",
            confirmLabel = "Clear Memory",
            confirmColor = ARIAColors.Warning,
            onConfirm = {
                showClearMemoryDialog = false
                vm.clearMemory()
            },
            onDismiss = { showClearMemoryDialog = false }
        )
    }

    if (showResetAgentDialog) {
        ConfirmDialog(
            title   = "Reset Agent?",
            message = "This clears all learned progress, task history, app skill data, and LoRA references. LoRA adapter files on disk are preserved.",
            confirmLabel = "Reset Agent",
            confirmColor = ARIAColors.Destructive,
            onConfirm = {
                showResetAgentDialog = false
                vm.resetAgent()
            },
            onDismiss = { showResetAgentDialog = false }
        )
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ARIAColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "SETTINGS",
            style = MaterialTheme.typography.headlineMedium.copy(
                color      = ARIAColors.Primary,
                fontWeight = FontWeight.Bold
            )
        )

        // ── Model Configuration ───────────────────────────────────────────────
        SectionLabel("Model Configuration")

        SettingsCard {
            // Model path — editable (matches RN)
            FieldLabel("GGUF Model Path")
            Text(
                "Internal storage path to your .gguf file",
                style = MaterialTheme.typography.bodySmall.copy(color = ARIAColors.Muted),
                modifier = Modifier.padding(bottom = 6.dp)
            )
            OutlinedTextField(
                value         = modelPath,
                onValueChange = { modelPath = it },
                modifier      = Modifier.fillMaxWidth(),
                placeholder   = {
                    Text(
                        "/data/user/0/com.ariaagent.mobile/files/models/your-model.gguf",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color    = ARIAColors.Divider,
                            fontSize = 11.sp
                        )
                    )
                },
                colors        = ariaTextFieldColors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                singleLine    = false,
                maxLines      = 3
            )

            CardDivider()

            // Quantization chip selector
            FieldLabel("Quantization")
            Spacer(Modifier.height(6.dp))
            Row(
                modifier              = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Q4_K_M", "Q4_0", "IQ2_S", "Q5_K_M").forEach { q ->
                    SelectableChip(
                        label    = q,
                        selected = quantization == q,
                        onClick  = { quantization = q }
                    )
                }
            }

            CardDivider()

            // Context window chip selector
            FieldLabel("Context Window")
            Spacer(Modifier.height(6.dp))
            Row(
                modifier              = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(512, 1024, 2048, 4096).forEach { sz ->
                    SelectableChip(
                        label    = sz.toString(),
                        selected = contextWindow == sz,
                        onClick  = { contextWindow = sz }
                    )
                }
            }

            CardDivider()

            // GPU layers chip selector
            Row(
                modifier            = Modifier.fillMaxWidth(),
                verticalAlignment   = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    FieldLabel("GPU Layers (n_gpu_layers)")
                    Text(
                        "Mali-G72: 32 recommended  ·  0 = CPU-only",
                        style = MaterialTheme.typography.bodySmall.copy(color = ARIAColors.Muted)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier              = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0, 8, 16, 24, 32).forEach { n ->
                    SelectableChip(
                        label    = if (n == 0) "CPU" else "$n L",
                        selected = nGpuLayers == n,
                        onClick  = { nGpuLayers = n }
                    )
                }
            }

            CardDivider()

            // Temperature preset buttons
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                FieldLabel("Temperature")
                Text(
                    "%.2f".format(temperatureX100 / 100f),
                    style = MaterialTheme.typography.titleLarge.copy(
                        color      = ARIAColors.Primary,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(0.1f, 0.3f, 0.5f, 0.7f, 0.9f).forEach { v ->
                    val targetX100 = Math.round(v * 100)
                    SelectableChip(
                        label    = "$v",
                        selected = temperatureX100 == targetX100,
                        onClick  = { temperatureX100 = targetX100 },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ── On-Device Learning ────────────────────────────────────────────────
        SectionLabel("On-Device Learning")

        SettingsCard {
            // RL toggle
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    FieldLabel("RL Module")
                    Text(
                        "Reinforcement learning from actions",
                        style = MaterialTheme.typography.bodySmall.copy(color = ARIAColors.Muted)
                    )
                }
                Switch(
                    checked         = rlEnabled,
                    onCheckedChange = { rlEnabled = it },
                    colors          = SwitchDefaults.colors(
                        checkedThumbColor   = ARIAColors.Background,
                        checkedTrackColor   = ARIAColors.Primary,
                        uncheckedThumbColor = ARIAColors.Muted,
                        uncheckedTrackColor = ARIAColors.Divider,
                    )
                )
            }

            CardDivider()

            // LoRA adapter path — editable (matches RN)
            FieldLabel("LoRA Adapter Path")
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value         = loraPath,
                onValueChange = { loraPath = it },
                modifier      = Modifier.fillMaxWidth(),
                placeholder   = {
                    Text(
                        "Optional — leave empty to use latest trained adapter",
                        style = MaterialTheme.typography.bodySmall.copy(color = ARIAColors.Divider)
                    )
                },
                colors          = ariaTextFieldColors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                singleLine      = false,
                maxLines        = 2
            )
        }

        // ── Permissions ───────────────────────────────────────────────────────
        SectionLabel("Permissions")

        SettingsCard(padding = 0.dp) {
            // Accessibility Service
            PermissionRow(
                icon          = Icons.Default.Visibility,
                title         = "Accessibility Service",
                description   = "Reads the UI tree and dispatches gestures. ARIA cannot navigate apps without this.",
                granted       = accessibilityGranted,
                grantedLabel  = "ACTIVE",
                deniedLabel   = "REQUIRED",
                grantedColor  = ARIAColors.Success,
                deniedColor   = ARIAColors.Destructive,
                showDivider   = true,
                actionLabel   = if (!accessibilityGranted) "Open Accessibility Settings" else null,
                onAction      = {
                    context.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            )

            // Notifications
            PermissionRow(
                icon          = Icons.Default.Notifications,
                title         = "Notifications",
                description   = "Download progress, training completion, and agent status alerts.",
                granted       = notificationsGranted,
                grantedLabel  = "GRANTED",
                deniedLabel   = "BLOCKED",
                grantedColor  = ARIAColors.Success,
                deniedColor   = ARIAColors.Warning,
                showDivider   = true,
                actionLabel   = if (!notificationsGranted) "Open Notification Settings" else null,
                onAction      = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            )

            // Screen Capture
            PermissionRow(
                icon          = Icons.Default.Fullscreen,
                title         = "Screen Capture",
                description   = "MediaProjection — Android shows a one-time consent dialog when you start the agent. No permanent grant needed.",
                granted       = screenCaptureGranted,
                grantedLabel  = "ACTIVE",
                deniedLabel   = "ON-DEMAND",
                grantedColor  = ARIAColors.Success,
                deniedColor   = ARIAColors.Primary,
                showDivider   = false,
                actionLabel   = null,
                onAction      = {}
            )
        }

        // ── System Info (new — beyond RN) ─────────────────────────────────────
        SectionLabel("System Info")

        SettingsCard {
            InfoRow("Device",       Build.MANUFACTURER + " " + Build.MODEL)
            InfoRow("Android",      "API " + Build.VERSION.SDK_INT + "  (${Build.VERSION.RELEASE})")
            InfoRow("Package",      context.packageName)
            InfoRow("Architecture", Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown")
        }

        // ── Save button ───────────────────────────────────────────────────────
        Button(
            onClick  = {
                focusManager.clearFocus()
                vm.saveConfig(
                    AriaConfig(
                        modelPath        = modelPath.trim(),
                        quantization     = quantization,
                        contextWindow    = contextWindow,
                        maxTokensPerTurn = config.maxTokensPerTurn,
                        temperatureX100  = temperatureX100,
                        nGpuLayers       = nGpuLayers,
                        rlEnabled        = rlEnabled,
                        loraAdapterPath  = loraPath.trim().ifBlank { null },
                    )
                )
                saveSuccess = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor = if (saveSuccess) ARIAColors.Success else ARIAColors.Primary
            ),
            shape    = RoundedCornerShape(12.dp)
        ) {
            Icon(
                if (saveSuccess) Icons.Default.CheckCircle else Icons.Default.Save,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (saveSuccess) "SAVED!" else "SAVE CONFIGURATION",
                fontWeight = FontWeight.Bold
            )
        }

        if (saveSuccess) {
            LaunchedEffect(saveSuccess) {
                kotlinx.coroutines.delay(3_000)
                saveSuccess = false
            }
        }

        // ── Danger Zone (new — beyond RN) ─────────────────────────────────────
        SectionLabel("Danger Zone")

        SettingsCard {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Clear Memory
                OutlinedButton(
                    onClick  = { showClearMemoryDialog = true },
                    modifier = Modifier.weight(1f),
                    colors   = ButtonDefaults.outlinedButtonColors(
                        contentColor = ARIAColors.Warning
                    ),
                    border   = androidx.compose.foundation.BorderStroke(
                        1.dp, ARIAColors.Warning.copy(alpha = 0.5f)
                    ),
                    shape    = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Clear Memory", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }

                // Reset Agent
                OutlinedButton(
                    onClick  = { showResetAgentDialog = true },
                    modifier = Modifier.weight(1f),
                    colors   = ButtonDefaults.outlinedButtonColors(
                        contentColor = ARIAColors.Destructive
                    ),
                    border   = androidx.compose.foundation.BorderStroke(
                        1.dp, ARIAColors.Destructive.copy(alpha = 0.5f)
                    ),
                    shape    = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reset Agent", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                "Clear Memory removes experience entries and embeddings. " +
                "Reset Agent clears all learned progress, skills, and task history.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = ARIAColors.Muted,
                    lineHeight = 18.sp
                )
            )
        }

        // ── On-device privacy note ─────────────────────────────────────────────
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint     = ARIAColors.Muted,
                modifier = Modifier.size(12.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Strictly on-device. No data ever leaves this phone.",
                style = MaterialTheme.typography.labelSmall.copy(color = ARIAColors.Muted)
            )
        }
    }
}

// ─── Sub-composables ──────────────────────────────────────────────────────────

@Composable
private fun SettingsCard(
    padding: androidx.compose.ui.unit.Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = ARIAColors.Surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(if (padding > 0.dp) 10.dp else 0.dp),
            content = content
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            color       = ARIAColors.Muted,
            fontWeight  = FontWeight.Bold,
            letterSpacing = 1.sp
        ),
        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium.copy(
            color      = ARIAColors.OnSurface,
            fontWeight = FontWeight.Medium
        )
    )
}

@Composable
private fun CardDivider() {
    HorizontalDivider(
        modifier  = Modifier.padding(vertical = 6.dp),
        color     = ARIAColors.Divider,
        thickness = 0.5.dp
    )
}

@Composable
private fun SelectableChip(
    label    : String,
    selected : Boolean,
    onClick  : () -> Unit,
    modifier : Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) ARIAColors.Primary else ARIAColors.SurfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(
                color      = if (selected) ARIAColors.Background else ARIAColors.OnSurface,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        )
    }
}

@Composable
private fun PermissionRow(
    icon          : ImageVector,
    title         : String,
    description   : String,
    granted       : Boolean,
    grantedLabel  : String,
    deniedLabel   : String,
    grantedColor  : Color,
    deniedColor   : Color,
    showDivider   : Boolean,
    actionLabel   : String?,
    onAction      : () -> Unit,
) {
    Column {
        Row(
            modifier  = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon circle
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (granted) grantedColor.copy(alpha = 0.13f)
                        else deniedColor.copy(alpha = 0.13f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint     = if (granted) grantedColor else deniedColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Title + badge
                Row(
                    verticalAlignment      = Alignment.CenterVertically,
                    horizontalArrangement  = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color      = ARIAColors.OnSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (granted) grantedColor.copy(alpha = 0.13f)
                                else deniedColor.copy(alpha = 0.13f)
                            )
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            if (granted) grantedLabel else deniedLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color      = if (granted) grantedColor else deniedColor,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp
                            )
                        )
                    }
                }

                // Description
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color      = ARIAColors.Muted,
                        lineHeight = 18.sp
                    )
                )

                // Action button — only when not granted and actionLabel != null
                if (!granted && actionLabel != null) {
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = onAction,
                        colors  = ButtonDefaults.outlinedButtonColors(
                            contentColor = deniedColor
                        ),
                        border  = androidx.compose.foundation.BorderStroke(
                            1.dp, deniedColor.copy(alpha = 0.4f)
                        ),
                        shape   = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Default.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(actionLabel, fontSize = 13.sp)
                    }
                }
            }
        }

        if (showDivider) {
            HorizontalDivider(color = ARIAColors.Divider, thickness = 0.5.dp)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall.copy(color = ARIAColors.Muted)
        )
        Text(
            value,
            style    = MaterialTheme.typography.bodySmall.copy(
                color      = ARIAColors.OnSurface,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun ConfirmDialog(
    title        : String,
    message      : String,
    confirmLabel : String,
    confirmColor : Color,
    onConfirm    : () -> Unit,
    onDismiss    : () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = ARIAColors.Surface,
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium.copy(
                    color      = ARIAColors.OnSurface,
                    fontWeight = FontWeight.Bold
                )
            )
        },
        text = {
            Text(
                message,
                style = MaterialTheme.typography.bodySmall.copy(
                    color      = ARIAColors.Muted,
                    lineHeight = 20.sp
                )
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors  = ButtonDefaults.buttonColors(containerColor = confirmColor),
                shape   = RoundedCornerShape(8.dp)
            ) {
                Text(confirmLabel, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = ARIAColors.Muted)
            }
        }
    )
}

@Composable
private fun ariaTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = ARIAColors.Primary,
    unfocusedBorderColor = ARIAColors.Divider,
    focusedTextColor     = ARIAColors.OnSurface,
    unfocusedTextColor   = ARIAColors.OnSurface,
    cursorColor          = ARIAColors.Primary,
)
