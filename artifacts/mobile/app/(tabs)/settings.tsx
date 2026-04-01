import React, { useEffect, useState } from "react";
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

import { useAgent } from "@/context/AgentContext";
import { useColors } from "@/hooks/useColors";
import { AgentConfig } from "@/native-bindings/AgentCoreBridge";
import { SectionHeader } from "@/components/SectionHeader";

const QUANTIZATIONS = ["Q4_K_M", "Q4_0", "IQ2_S", "Q5_K_M"];
const CONTEXT_SIZES = [512, 1024, 2048, 4096];

export default function SettingsScreen() {
  const colors = useColors();
  const insets = useSafeAreaInsets();
  const { config, updateConfig, isLoading } = useAgent();
  const [local, setLocal] = useState<Partial<AgentConfig>>({});
  const [saved, setSaved] = useState(false);

  const topPad = Platform.OS === "web" ? 67 : insets.top;
  const bottomPad = Platform.OS === "web" ? 34 : 0;

  useEffect(() => {
    if (config) setLocal(config);
  }, [config]);

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
});
