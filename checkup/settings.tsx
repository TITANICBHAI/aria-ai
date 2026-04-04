import React, { useCallback, useEffect, useState } from "react";
import {
  Alert,
  Platform,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { Feather } from "@expo/vector-icons";
import * as Haptics from "expo-haptics";
import { useFocusEffect } from "expo-router";

import { useAgent } from "@/context/AgentContext";
import { useColors } from "@/hooks/useColors";
import { AgentConfig, AgentCoreBridge } from "@/native-bindings/AgentCoreBridge";
import { SectionHeader } from "@/components/SectionHeader";

const QUANTIZATIONS = ["Q4_K_M", "Q4_0", "IQ2_S", "Q5_K_M"];
const CONTEXT_SIZES = [512, 1024, 2048, 4096];
const GPU_LAYERS = [0, 8, 16, 24, 32];

type PermState = { accessibility: boolean; screenCapture: boolean; notifications: boolean };

export default function SettingsScreen() {
  const colors = useColors();
  const insets = useSafeAreaInsets();
  const { config, updateConfig, isLoading } = useAgent();
  const [local, setLocal] = useState<Partial<AgentConfig>>({});
  const [saved, setSaved] = useState(false);
  const [perms, setPerms] = useState<PermState>({
    accessibility: false,
    screenCapture: false,
    notifications: false,
  });
  const [serverUrl, setServerUrl] = useState<string | null>(null);
  const [deviceIp, setDeviceIp] = useState<string | null>(null);
  const [serverRunning, setServerRunning] = useState(false);
  const [serverWorking, setServerWorking] = useState(false);
  const [urlCopied, setUrlCopied] = useState(false);

  const topPad = Platform.OS === "web" ? 67 : insets.top;
  const bottomPad = Platform.OS === "web" ? 34 : 0;

  useEffect(() => {
    if (config) setLocal(config);
  }, [config]);

  const refreshPerms = useCallback(async () => {
    try {
      const status = await AgentCoreBridge.getPermissionsStatus();
      setPerms(status);
    } catch { /* bridge unavailable on web */ }
  }, []);

  const refreshServer = useCallback(async () => {
    try {
      const [url, ip, running] = await Promise.all([
        AgentCoreBridge.getLocalServerUrl(),
        AgentCoreBridge.getDeviceIp(),
        AgentCoreBridge.isLocalServerRunning(),
      ]);
      setServerUrl(url);
      setDeviceIp(ip);
      setServerRunning(running);
    } catch { /* bridge unavailable on web */ }
  }, []);

  useFocusEffect(
    useCallback(() => {
      refreshPerms();
      refreshServer();
      const interval = setInterval(refreshPerms, 3000);
      return () => clearInterval(interval);
    }, [refreshPerms, refreshServer])
  );

  const handleToggleServer = async () => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
    setServerWorking(true);
    if (serverRunning) {
      await AgentCoreBridge.stopLocalServer();
    } else {
      const url = await AgentCoreBridge.startLocalServer(0);
      setServerUrl(url);
    }
    await refreshServer();
    setServerWorking(false);
  };

  const handleCopyUrl = () => {
    if (!serverUrl) return;
    Haptics.selectionAsync();
    Alert.alert(
      "Dashboard URL",
      serverUrl,
      [{ text: "OK" }]
    );
    setUrlCopied(true);
    setTimeout(() => setUrlCopied(false), 2000);
  };

  const patch = (key: keyof AgentConfig, value: any) => {
    setLocal((p) => ({ ...p, [key]: value }));
    setSaved(false);
  };

  const handleSave = async () => {
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
    await updateConfig(local);
    setSaved(true);
    setTimeout(() => setSaved(false), 2000);
  };

  const tempValue = (local.temperatureX100 ?? 70) / 100;

  return (
    <ScrollView
      style={[styles.scroll, { backgroundColor: colors.background }]}
      contentContainerStyle={[
        styles.content,
        { paddingTop: topPad + 16, paddingBottom: bottomPad + 100 },
      ]}
    >
      <Text style={[styles.title, { color: colors.foreground }]}>Settings</Text>

      {/* Model Configuration */}
      <SectionHeader title="Model Configuration" />
      <View
        style={[
          styles.section,
          {
            backgroundColor: colors.surface1,
            borderColor: colors.border,
            borderWidth: 1,
            borderRadius: 16,
          },
        ]}
      >
        <View style={styles.fieldRow}>
          <View style={styles.fieldInfo}>
            <Text style={[styles.fieldLabel, { color: colors.foreground }]}>
              GGUF Model Path
            </Text>
            <Text style={[styles.fieldSub, { color: colors.mutedForeground }]}>
              Internal storage path
            </Text>
          </View>
        </View>
        <TextInput
          value={local.modelPath ?? ""}
          onChangeText={(v) => patch("modelPath", v)}
          placeholder="/data/user/0/com.ariaagent.mobile/files/models/your-model.gguf"
          style={[
            styles.pathInput,
            {
              backgroundColor: colors.surface3,
              color: colors.foreground,
              borderRadius: 8,
              fontFamily: "Inter_400Regular",
            },
          ]}
          placeholderTextColor={colors.mutedForeground}
        />
        <Text style={[styles.fieldSub, { color: colors.mutedForeground, marginTop: 4 }]}>
          Swap to any Llama-compatible GGUF — place the file in internal storage, paste the full path above, and tap Save. ARIA loads it on the next "Load Model" press.
        </Text>

        <View style={[styles.divider, { borderColor: colors.border }]} />

        <View style={styles.fieldRow}>
          <Text style={[styles.fieldLabel, { color: colors.foreground }]}>
            Quantization
          </Text>
        </View>
        <View style={styles.chipRow}>
          {QUANTIZATIONS.map((q) => (
            <TouchableOpacity
              key={q}
              onPress={() => {
                Haptics.selectionAsync();
                patch("quantization", q);
              }}
              style={[
                styles.chip,
                {
                  backgroundColor:
                    local.quantization === q
                      ? colors.primary
                      : colors.surface3,
                  borderRadius: 8,
                },
              ]}
            >
              <Text
                style={[
                  styles.chipText,
                  {
                    color:
                      local.quantization === q
                        ? colors.primaryForeground
                        : colors.foreground,
                  },
                ]}
              >
                {q}
              </Text>
            </TouchableOpacity>
          ))}
        </View>

        <View style={[styles.divider, { borderColor: colors.border }]} />

        <View style={styles.fieldRow}>
          <Text style={[styles.fieldLabel, { color: colors.foreground }]}>
            Context Window
          </Text>
        </View>
        <View style={styles.chipRow}>
          {CONTEXT_SIZES.map((sz) => (
            <TouchableOpacity
              key={sz}
              onPress={() => {
                Haptics.selectionAsync();
                patch("contextWindow", sz);
              }}
              style={[
                styles.chip,
                {
                  backgroundColor:
                    local.contextWindow === sz
                      ? colors.primary
                      : colors.surface3,
                  borderRadius: 8,
                },
              ]}
            >
              <Text
                style={[
                  styles.chipText,
                  {
                    color:
                      local.contextWindow === sz
                        ? colors.primaryForeground
                        : colors.foreground,
                  },
                ]}
              >
                {sz}
              </Text>
            </TouchableOpacity>
          ))}
        </View>

        <View style={[styles.divider, { borderColor: colors.border }]} />

        <View style={styles.fieldRow}>
          <View style={styles.fieldInfo}>
            <Text style={[styles.fieldLabel, { color: colors.foreground }]}>
              GPU Layers (n_gpu_layers)
            </Text>
            <Text style={[styles.fieldSub, { color: colors.mutedForeground }]}>
              Mali-G72: 32 recommended · 0 = CPU-only
            </Text>
          </View>
        </View>
        <View style={styles.chipRow}>
          {GPU_LAYERS.map((n) => (
            <TouchableOpacity
              key={n}
              onPress={() => {
                Haptics.selectionAsync();
                patch("nGpuLayers", n);
              }}
              style={[
                styles.chip,
                {
                  backgroundColor:
                    (local.nGpuLayers ?? 32) === n
                      ? colors.primary
                      : colors.surface3,
                  borderRadius: 8,
                },
              ]}
            >
              <Text
                style={[
                  styles.chipText,
                  {
                    color:
                      (local.nGpuLayers ?? 32) === n
                        ? colors.primaryForeground
                        : colors.foreground,
                  },
                ]}
              >
                {n === 0 ? "CPU" : `${n} L`}
              </Text>
            </TouchableOpacity>
          ))}
        </View>

        <View style={[styles.divider, { borderColor: colors.border }]} />

        <View style={styles.sliderRow}>
          <Text style={[styles.fieldLabel, { color: colors.foreground }]}>
            Temperature
          </Text>
          <Text style={[styles.sliderVal, { color: colors.primary }]}>
            {tempValue.toFixed(2)}
          </Text>
        </View>
        <View style={styles.tempButtons}>
          {[0.1, 0.3, 0.5, 0.7, 0.9].map((v) => (
            <TouchableOpacity
              key={v}
              onPress={() => {
                Haptics.selectionAsync();
                patch("temperatureX100", Math.round(v * 100));
              }}
              style={[
                styles.tempBtn,
                {
                  backgroundColor:
                    Math.round((local.temperatureX100 ?? 70)) ===
                    Math.round(v * 100)
                      ? colors.primary
                      : colors.surface3,
                  borderRadius: 8,
                },
              ]}
            >
              <Text
                style={[
                  styles.chipText,
                  {
                    color:
                      Math.round(local.temperatureX100 ?? 70) ===
                      Math.round(v * 100)
                        ? colors.primaryForeground
                        : colors.foreground,
                  },
                ]}
              >
                {v}
              </Text>
            </TouchableOpacity>
          ))}
        </View>
      </View>

      {/* Learning */}
      <SectionHeader title="On-Device Learning" />
      <View
        style={[
          styles.section,
          {
            backgroundColor: colors.surface1,
            borderColor: colors.border,
            borderWidth: 1,
            borderRadius: 16,
          },
        ]}
      >
        <View style={styles.switchRow}>
          <View style={styles.fieldInfo}>
            <Text style={[styles.fieldLabel, { color: colors.foreground }]}>
              RL Module
            </Text>
            <Text style={[styles.fieldSub, { color: colors.mutedForeground }]}>
              Reinforcement learning from actions
            </Text>
          </View>
          <Switch
            value={local.rlEnabled ?? false}
            onValueChange={(v) => patch("rlEnabled", v)}
            trackColor={{ false: colors.muted, true: colors.primary + "66" }}
            thumbColor={local.rlEnabled ? colors.primary : colors.mutedForeground}
          />
        </View>

        <View style={[styles.divider, { borderColor: colors.border }]} />

        <View style={styles.fieldRow}>
          <Text style={[styles.fieldLabel, { color: colors.foreground }]}>
            LoRA Adapter Path
          </Text>
        </View>
        <TextInput
          value={local.loraAdapterPath ?? ""}
          onChangeText={(v) => patch("loraAdapterPath", v || null)}
          placeholder="Optional — leave empty to skip"
          placeholderTextColor={colors.mutedForeground}
          style={[
            styles.pathInput,
            {
              backgroundColor: colors.surface3,
              color: colors.foreground,
              borderRadius: 8,
              fontFamily: "Inter_400Regular",
            },
          ]}
        />
      </View>

      {/* Permissions */}
      <SectionHeader title="Permissions" />
      <View
        style={[
          styles.section,
          {
            backgroundColor: colors.surface1,
            borderColor: colors.border,
            borderWidth: 1,
            borderRadius: 16,
            gap: 0,
            padding: 0,
          },
        ]}
      >
        {/* Accessibility Service */}
        <View style={[styles.permRow, { borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: colors.border }]}>
          <View style={[styles.permIconWrap, { backgroundColor: perms.accessibility ? colors.success + "22" : colors.destructive + "22" }]}>
            <Feather
              name="eye"
              size={18}
              color={perms.accessibility ? colors.success : colors.destructive}
            />
          </View>
          <View style={styles.permInfo}>
            <View style={styles.permTitleRow}>
              <Text style={[styles.permTitle, { color: colors.foreground }]}>Accessibility Service</Text>
              <View style={[styles.permBadge, { backgroundColor: perms.accessibility ? colors.success + "22" : colors.destructive + "22" }]}>
                <Text style={[styles.permBadgeText, { color: perms.accessibility ? colors.success : colors.destructive }]}>
                  {perms.accessibility ? "ACTIVE" : "REQUIRED"}
                </Text>
              </View>
            </View>
            <Text style={[styles.permDesc, { color: colors.mutedForeground }]}>
              Reads the UI tree and dispatches gestures. ARIA cannot navigate apps without this.
            </Text>
            {!perms.accessibility && (
              <TouchableOpacity
                onPress={async () => {
                  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
                  await AgentCoreBridge.openAccessibilitySettings();
                }}
                style={[styles.permBtn, { backgroundColor: colors.primary + "18", borderColor: colors.primary + "44", borderWidth: 1 }]}
                activeOpacity={0.7}
              >
                <Feather name="external-link" size={13} color={colors.primary} />
                <Text style={[styles.permBtnText, { color: colors.primary }]}>
                  Open Accessibility Settings
                </Text>
              </TouchableOpacity>
            )}
          </View>
        </View>

        {/* Notifications */}
        <View style={[styles.permRow, { borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: colors.border }]}>
          <View style={[styles.permIconWrap, { backgroundColor: perms.notifications ? colors.success + "22" : colors.warning + "22" }]}>
            <Feather
              name="bell"
              size={18}
              color={perms.notifications ? colors.success : colors.warning}
            />
          </View>
          <View style={styles.permInfo}>
            <View style={styles.permTitleRow}>
              <Text style={[styles.permTitle, { color: colors.foreground }]}>Notifications</Text>
              <View style={[styles.permBadge, { backgroundColor: perms.notifications ? colors.success + "22" : colors.warning + "22" }]}>
                <Text style={[styles.permBadgeText, { color: perms.notifications ? colors.success : colors.warning }]}>
                  {perms.notifications ? "GRANTED" : "BLOCKED"}
                </Text>
              </View>
            </View>
            <Text style={[styles.permDesc, { color: colors.mutedForeground }]}>
              Download progress, training completion, and agent status alerts.
            </Text>
            {!perms.notifications && (
              <TouchableOpacity
                onPress={async () => {
                  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
                  await AgentCoreBridge.openNotificationSettings();
                }}
                style={[styles.permBtn, { backgroundColor: colors.warning + "18", borderColor: colors.warning + "44", borderWidth: 1 }]}
                activeOpacity={0.7}
              >
                <Feather name="external-link" size={13} color={colors.warning} />
                <Text style={[styles.permBtnText, { color: colors.warning }]}>
                  Open Notification Settings
                </Text>
              </TouchableOpacity>
            )}
          </View>
        </View>

        {/* Screen Capture */}
        <View style={styles.permRow}>
          <View style={[styles.permIconWrap, { backgroundColor: perms.screenCapture ? colors.success + "22" : colors.primary + "18" }]}>
            <Feather
              name="monitor"
              size={18}
              color={perms.screenCapture ? colors.success : colors.primary}
            />
          </View>
          <View style={styles.permInfo}>
            <View style={styles.permTitleRow}>
              <Text style={[styles.permTitle, { color: colors.foreground }]}>Screen Capture</Text>
              <View style={[styles.permBadge, { backgroundColor: perms.screenCapture ? colors.success + "22" : colors.primary + "18" }]}>
                <Text style={[styles.permBadgeText, { color: perms.screenCapture ? colors.success : colors.primary }]}>
                  {perms.screenCapture ? "ACTIVE" : "ON-DEMAND"}
                </Text>
              </View>
            </View>
            <Text style={[styles.permDesc, { color: colors.mutedForeground }]}>
              MediaProjection — Android will show a one-time consent dialog when you start the agent. No permanent grant needed.
            </Text>
          </View>
        </View>
      </View>

      {/* Web Dashboard */}
      <SectionHeader title="Web Dashboard" />
      <View
        style={[
          styles.section,
          {
            backgroundColor: colors.surface1,
            borderColor: serverRunning ? colors.success + "30" : colors.border,
            borderWidth: 1,
            borderRadius: 16,
            gap: 0,
            padding: 0,
          },
        ]}
      >
        {/* Server toggle row */}
        <View style={[styles.serverRow, { borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: colors.border }]}>
          <View
            style={[
              styles.serverIconWrap,
              { backgroundColor: serverRunning ? colors.success + "22" : colors.muted },
            ]}
          >
            <Feather
              name="wifi"
              size={18}
              color={serverRunning ? colors.success : colors.mutedForeground}
            />
          </View>
          <View style={styles.serverInfo}>
            <View style={styles.serverTitleRow}>
              <Text style={[styles.serverTitle, { color: colors.foreground }]}>
                Local Monitoring Server
              </Text>
              <View
                style={[
                  styles.serverBadge,
                  { backgroundColor: serverRunning ? colors.success + "22" : colors.muted },
                ]}
              >
                <Text
                  style={[
                    styles.serverBadgeText,
                    { color: serverRunning ? colors.success : colors.mutedForeground },
                  ]}
                >
                  {serverRunning ? "RUNNING" : "STOPPED"}
                </Text>
              </View>
            </View>
            <Text style={[styles.serverDesc, { color: colors.mutedForeground }]}>
              Embedded HTTP server (port 8765) — serves live agent telemetry over your LAN. Point the web dashboard at this URL to monitor ARIA remotely.
            </Text>
          </View>
          <Switch
            value={serverRunning}
            onValueChange={handleToggleServer}
            disabled={serverWorking}
            trackColor={{ false: colors.muted, true: colors.success + "66" }}
            thumbColor={serverRunning ? colors.success : colors.mutedForeground}
          />
        </View>

        {/* Device IP row */}
        <View style={[styles.serverDetailRow, { borderBottomWidth: StyleSheet.hairlineWidth, borderBottomColor: colors.border }]}>
          <Feather name="radio" size={14} color={colors.mutedForeground} />
          <Text style={[styles.serverDetailLabel, { color: colors.mutedForeground }]}>
            Device IP
          </Text>
          <Text style={[styles.serverDetailValue, { color: colors.foreground }]}>
            {deviceIp ?? "—"}
          </Text>
        </View>

        {/* Server URL row */}
        <View style={styles.serverUrlRow}>
          <View style={styles.serverUrlLeft}>
            <Feather name="link" size={14} color={colors.mutedForeground} />
            <Text style={[styles.serverDetailLabel, { color: colors.mutedForeground }]}>
              Dashboard URL
            </Text>
          </View>
          <View style={styles.serverUrlRight}>
            <Text
              style={[styles.serverUrlText, { color: serverRunning ? colors.accent : colors.mutedForeground }]}
              numberOfLines={1}
            >
              {serverUrl ?? "—"}
            </Text>
            {serverUrl && serverRunning && (
              <TouchableOpacity
                onPress={handleCopyUrl}
                style={[
                  styles.copyBtn,
                  {
                    backgroundColor: urlCopied ? colors.success + "22" : colors.accent + "18",
                    borderRadius: 6,
                  },
                ]}
              >
                <Feather
                  name={urlCopied ? "check" : "copy"}
                  size={13}
                  color={urlCopied ? colors.success : colors.accent}
                />
                <Text
                  style={[
                    styles.copyBtnText,
                    { color: urlCopied ? colors.success : colors.accent },
                  ]}
                >
                  {urlCopied ? "Copied!" : "Copy"}
                </Text>
              </TouchableOpacity>
            )}
          </View>
        </View>
      </View>

      {/* Architecture Info */}
      <SectionHeader title="Architecture (Read-only)" />
      <View
        style={[
          styles.archBox,
          {
            backgroundColor: colors.surface1,
            borderColor: colors.border,
            borderWidth: 1,
            borderRadius: 16,
          },
        ]}
      >
        {[
          {
            phase: "Phase 1 (Now)",
            color: colors.success,
            desc: "New Architecture · JS UI + Kotlin brain · TurboModules",
          },
          {
            phase: "Phase 2",
            color: colors.warning,
            desc: "JS becomes thin wrapper · More logic → Kotlin",
          },
          {
            phase: "Phase 3",
            color: colors.accent,
            desc: "Full Kotlin · Jetpack Compose UI",
          },
        ].map((p) => (
          <View key={p.phase} style={styles.archRow}>
            <View
              style={[
                styles.archDot,
                { backgroundColor: p.color + "30", borderRadius: 8 },
              ]}
            >
              <Text style={[styles.archPhase, { color: p.color }]}>
                {p.phase}
              </Text>
            </View>
            <Text style={[styles.archDesc, { color: colors.mutedForeground }]}>
              {p.desc}
            </Text>
          </View>
        ))}
      </View>

      {/* Save */}
      <TouchableOpacity
        onPress={handleSave}
        style={[
          styles.saveBtn,
          {
            backgroundColor: saved ? colors.success : colors.primary,
            borderRadius: 14,
          },
        ]}
      >
        <Feather
          name={saved ? "check" : "save"}
          size={18}
          color={colors.primaryForeground}
        />
        <Text style={[styles.saveBtnText, { color: colors.primaryForeground }]}>
          {saved ? "Saved!" : "Save Configuration"}
        </Text>
      </TouchableOpacity>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  scroll: { flex: 1 },
  content: { paddingHorizontal: 20, gap: 14 },
  title: { fontSize: 28, fontFamily: "Inter_700Bold", marginBottom: 4 },
  section: { padding: 16, gap: 14 },
  fieldRow: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  fieldInfo: { flex: 1 },
  fieldLabel: { fontSize: 14, fontFamily: "Inter_500Medium" },
  fieldSub: { fontSize: 12, fontFamily: "Inter_400Regular", marginTop: 2 },
  pathInput: {
    padding: 12,
    fontSize: 12,
    lineHeight: 18,
  },
  divider: { borderTopWidth: StyleSheet.hairlineWidth },
  chipRow: { flexDirection: "row", flexWrap: "wrap", gap: 8 },
  chip: { paddingHorizontal: 14, paddingVertical: 8 },
  chipText: { fontSize: 13, fontFamily: "Inter_500Medium" },
  sliderRow: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  sliderVal: { fontSize: 18, fontFamily: "Inter_700Bold" },
  tempButtons: { flexDirection: "row", gap: 8 },
  tempBtn: { flex: 1, paddingVertical: 8, alignItems: "center" },
  switchRow: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  archBox: { padding: 16, gap: 12 },
  archRow: { gap: 6 },
  archDot: { paddingHorizontal: 10, paddingVertical: 5, alignSelf: "flex-start" },
  archPhase: { fontSize: 11, fontFamily: "Inter_700Bold", letterSpacing: 0.5 },
  archDesc: { fontSize: 12, fontFamily: "Inter_400Regular", lineHeight: 18 },
  saveBtn: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 10,
    paddingVertical: 16,
    marginTop: 4,
  },
  saveBtnText: { fontSize: 16, fontFamily: "Inter_600SemiBold" },
  permRow: {
    flexDirection: "row",
    alignItems: "flex-start",
    padding: 16,
    gap: 12,
  },
  permIconWrap: {
    width: 36,
    height: 36,
    borderRadius: 10,
    alignItems: "center",
    justifyContent: "center",
    flexShrink: 0,
    marginTop: 1,
  },
  permInfo: { flex: 1, gap: 4 },
  permTitleRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    flexWrap: "wrap",
  },
  permTitle: { fontSize: 14, fontFamily: "Inter_600SemiBold" },
  permBadge: {
    paddingHorizontal: 7,
    paddingVertical: 2,
    borderRadius: 6,
  },
  permBadgeText: { fontSize: 10, fontFamily: "Inter_700Bold", letterSpacing: 0.6 },
  permDesc: { fontSize: 12, fontFamily: "Inter_400Regular", lineHeight: 18 },
  permBtn: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 8,
    alignSelf: "flex-start",
    marginTop: 4,
  },
  permBtnText: { fontSize: 13, fontFamily: "Inter_500Medium" },
  serverRow: {
    flexDirection: "row",
    alignItems: "flex-start",
    padding: 16,
    gap: 12,
  },
  serverIconWrap: {
    width: 36,
    height: 36,
    borderRadius: 10,
    alignItems: "center",
    justifyContent: "center",
    flexShrink: 0,
    marginTop: 1,
  },
  serverInfo: { flex: 1, gap: 4 },
  serverTitleRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    flexWrap: "wrap",
  },
  serverTitle: { fontSize: 14, fontFamily: "Inter_600SemiBold" },
  serverBadge: { paddingHorizontal: 7, paddingVertical: 2, borderRadius: 6 },
  serverBadgeText: { fontSize: 10, fontFamily: "Inter_700Bold", letterSpacing: 0.6 },
  serverDesc: { fontSize: 12, fontFamily: "Inter_400Regular", lineHeight: 18 },
  serverDetailRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  serverDetailLabel: { fontSize: 12, fontFamily: "Inter_400Regular", flex: 1 },
  serverDetailValue: { fontSize: 12, fontFamily: "Inter_500Medium" },
  serverUrlRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: 16,
    paddingVertical: 12,
    gap: 8,
    flexWrap: "wrap",
  },
  serverUrlLeft: { flexDirection: "row", alignItems: "center", gap: 8 },
  serverUrlRight: { flexDirection: "row", alignItems: "center", gap: 8 },
  serverUrlText: { fontSize: 12, fontFamily: "Inter_500Medium" },
  copyBtn: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    paddingHorizontal: 8,
    paddingVertical: 5,
  },
  copyBtnText: { fontSize: 12, fontFamily: "Inter_500Medium" },
});
