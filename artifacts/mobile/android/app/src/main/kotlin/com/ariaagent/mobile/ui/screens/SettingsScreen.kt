package com.ariaagent.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ariaagent.mobile.core.config.AriaConfig
import com.ariaagent.mobile.ui.viewmodel.AgentViewModel
import com.ariaagent.mobile.ui.theme.ARIAColors

/**
 * SettingsScreen — configure the agent's inference parameters.
 *
 * Reads from AgentViewModel.config StateFlow (DataStore-backed AriaConfig).
 * Writes via ConfigStore.update() — changes are persisted asynchronously and
 * broadcast via AgentEventBus "config_updated" event so all screens react.
 *
 * Parameters exposed:
 *   contextWindow, maxTokensPerTurn, temperatureX100, nGpuLayers, rlEnabled
 *
 * NOTE: model path and quantization are advanced — only show for reference.
 *
 * Phase 11 — pure Compose. No React Native, no bridge.
 */
@Composable
fun SettingsScreen(vm: AgentViewModel = viewModel()) {
    val config  by vm.config.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    // Local draft state — applied on "Save"
    var contextWindow    by remember(config.contextWindow)    { mutableStateOf(config.contextWindow.toString()) }
    var maxTokens        by remember(config.maxTokensPerTurn) { mutableStateOf(config.maxTokensPerTurn.toString()) }
    var temperaturePct   by remember(config.temperatureX100)  { mutableStateOf((config.temperatureX100 / 100f).toString()) }
    var nGpuLayers       by remember(config.nGpuLayers)       { mutableStateOf(config.nGpuLayers.toString()) }
    var rlEnabled        by remember(config.rlEnabled)        { mutableStateOf(config.rlEnabled) }

    var saveSuccess by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ARIAColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "SETTINGS",
            style = MaterialTheme.typography.headlineMedium.copy(
                color = ARIAColors.Primary,
                fontWeight = FontWeight.Bold
            )
        )

        // ── Inference parameters ──────────────────────────────────────────────
        ARIACard {
            Text("INFERENCE", style = MaterialTheme.typography.labelSmall.copy(color = ARIAColors.Muted))
            Spacer(Modifier.height(10.dp))

            ARIANumberField(
                label = "Context Window (tokens)",
                value = contextWindow,
                onValueChange = { contextWindow = it },
                hint = "Default: 4096"
            )
            Spacer(Modifier.height(8.dp))
            ARIANumberField(
                label = "Max Tokens Per Turn",
                value = maxTokens,
                onValueChange = { maxTokens = it },
                hint = "Default: 512"
            )
            Spacer(Modifier.height(8.dp))
            ARIANumberField(
                label = "Temperature (0.0 – 2.0)",
                value = temperaturePct,
                onValueChange = { temperaturePct = it },
                hint = "Default: 0.7",
                isDecimal = true
            )
            Spacer(Modifier.height(8.dp))
            ARIANumberField(
                label = "GPU Layers (0 = CPU only)",
                value = nGpuLayers,
                onValueChange = { nGpuLayers = it },
                hint = "Default: 32  •  Exynos 9611 max: 32"
            )
        }

        // ── Reinforcement learning ────────────────────────────────────────────
        ARIACard {
            Text("LEARNING", style = MaterialTheme.typography.labelSmall.copy(color = ARIAColors.Muted))
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Reinforcement Learning",
                        style = MaterialTheme.typography.bodyMedium.copy(color = ARIAColors.OnSurface)
                    )
                    Text(
                        "On-device LoRA fine-tuning while charging + idle",
                        style = MaterialTheme.typography.bodySmall.copy(color = ARIAColors.Muted)
                    )
                }
                Switch(
                    checked = rlEnabled,
                    onCheckedChange = { rlEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor   = ARIAColors.Background,
                        checkedTrackColor   = ARIAColors.Primary,
                        uncheckedThumbColor = ARIAColors.Muted,
                        uncheckedTrackColor = ARIAColors.Divider,
                    )
                )
            }
        }

        // ── Read-only model info ──────────────────────────────────────────────
        ARIACard {
            Text("MODEL (READ-ONLY)", style = MaterialTheme.typography.labelSmall.copy(color = ARIAColors.Muted))
            Spacer(Modifier.height(8.dp))
            InfoRow("Model",        config.modelPath.substringAfterLast('/'))
            InfoRow("Quantization", config.quantization)
            InfoRow("LoRA Adapter", config.loraAdapterPath?.substringAfterLast('/') ?: "auto (latest trained)")
        }

        // ── Save button ───────────────────────────────────────────────────────
        Button(
            onClick = {
                focusManager.clearFocus()
                val tempX100 = ((temperaturePct.toFloatOrNull() ?: 0.7f) * 100).toInt()
                val patch = AriaConfig(
                    modelPath        = config.modelPath,
                    quantization     = config.quantization,
                    contextWindow    = contextWindow.toIntOrNull() ?: config.contextWindow,
                    maxTokensPerTurn = maxTokens.toIntOrNull() ?: config.maxTokensPerTurn,
                    temperatureX100  = tempX100.coerceIn(0, 200),
                    nGpuLayers       = nGpuLayers.toIntOrNull() ?: config.nGpuLayers,
                    rlEnabled        = rlEnabled,
                    loraAdapterPath  = config.loraAdapterPath,
                )
                // Persist — ConfigStore saves to DataStore, emits config_updated to AgentEventBus
                saveSettingsToStore(vm, patch)
                saveSuccess = true
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = ARIAColors.Primary),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("SAVE SETTINGS", fontWeight = FontWeight.Bold)
        }

        if (saveSuccess) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null,
                    tint = ARIAColors.Success, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Settings saved",
                    style = MaterialTheme.typography.bodySmall.copy(color = ARIAColors.Success)
                )
            }
            LaunchedEffect(saveSuccess) {
                kotlinx.coroutines.delay(3_000)
                saveSuccess = false
            }
        }

        // ── Privacy note ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Lock, contentDescription = null,
                tint = ARIAColors.Muted, modifier = Modifier.size(12.dp))
            Text(
                "Strictly on-device. No data ever leaves this phone.",
                style = MaterialTheme.typography.labelSmall.copy(color = ARIAColors.Muted)
            )
        }
    }
}

// Save through ConfigStore on IO dispatcher
private fun saveSettingsToStore(vm: AgentViewModel, config: AriaConfig) {
    // Access context via the ViewModel's Application
    // ConfigStore.update() is a suspend function — launch from ViewModel
    vm.saveConfig(config)
}

@Composable
private fun ARIANumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String = "",
    isDecimal: Boolean = false
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall.copy(color = ARIAColors.Muted))
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(hint, style = MaterialTheme.typography.bodySmall.copy(color = ARIAColors.Divider)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = ARIAColors.Primary,
                unfocusedBorderColor = ARIAColors.Divider,
                focusedTextColor     = ARIAColors.OnSurface,
                unfocusedTextColor   = ARIAColors.OnSurface,
                cursorColor          = ARIAColors.Primary,
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isDecimal) KeyboardType.Decimal else KeyboardType.Number,
                imeAction = ImeAction.Next
            ),
            singleLine = true
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall.copy(color = ARIAColors.Muted))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(color = ARIAColors.OnSurface),
            maxLines = 1
        )
    }
}
