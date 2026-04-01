import React from "react";
import {
  Platform,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { Feather } from "@expo/vector-icons";
import * as Haptics from "expo-haptics";

import { useAgent } from "@/context/AgentContext";
import { useColors } from "@/hooks/useColors";
import { AgentCoreBridge } from "@/native-bindings/AgentCoreBridge";
import { SectionHeader } from "@/components/SectionHeader";

interface DetailRowProps {
  label: string;
  value: string;
  colors: any;
}
function DetailRow({ label, value, colors }: DetailRowProps) {
  return (
    <View style={[styles.detailRow, { borderColor: colors.border }]}>
      <Text style={[styles.detailLabel, { color: colors.mutedForeground }]}>
        {label}
      </Text>
      <Text style={[styles.detailValue, { color: colors.foreground }]}>
        {value}
      </Text>
    </View>
  );
}

interface ModuleCardProps {
  title: string;
  subtitle: string;
  icon: React.ComponentProps<typeof Feather>["name"];
  ready: boolean;
  details: { label: string; value: string }[];
  onAction?: () => void;
  actionLabel?: string;
  colors: any;
}
function ModuleCard({
  title,
  subtitle,
  icon,
  ready,
  details,
  onAction,
  actionLabel,
  colors,
}: ModuleCardProps) {
  return (
    <View
      style={[
        styles.card,
        {
          backgroundColor: colors.surface1,
          borderColor: ready ? colors.success + "25" : colors.border,
          borderWidth: 1,
          borderRadius: 16,
        },
      ]}
    >
      {/* Card Header */}
      <View style={styles.cardHeader}>
        <View
          style={[
            styles.cardIcon,
            {
              backgroundColor: ready ? colors.success + "15" : colors.muted,
            },
          ]}
        >
          <Feather
            name={icon}
            size={20}
            color={ready ? colors.success : colors.mutedForeground}
          />
        </View>
        <View style={styles.cardTitles}>
          <Text style={[styles.cardTitle, { color: colors.foreground }]}>
            {title}
          </Text>
          <Text style={[styles.cardSub, { color: colors.mutedForeground }]}>
            {subtitle}
          </Text>
        </View>
        <View
          style={[
            styles.statusPill,
            {
              backgroundColor: ready
                ? colors.success + "20"
                : colors.muted,
            },
          ]}
        >
          <Text
            style={[
              styles.statusPillText,
              { color: ready ? colors.success : colors.mutedForeground },
            ]}
          >
            {ready ? "READY" : "OFFLINE"}
          </Text>
        </View>
      </View>

      {/* Details */}
      <View style={[styles.detailsBlock, { borderColor: colors.border }]}>
        {details.map((d) => (
          <DetailRow key={d.label} label={d.label} value={d.value} colors={colors} />
        ))}
      </View>

      {/* Action */}
      {onAction && (
        <TouchableOpacity
          onPress={onAction}
          style={[
            styles.cardAction,
            {
              backgroundColor: colors.primary + "15",
              borderRadius: 10,
            },
          ]}
        >
          <Text style={[styles.cardActionText, { color: colors.primary }]}>
            {actionLabel ?? "Configure"}
          </Text>
          <Feather name="chevron-right" size={14} color={colors.primary} />
        </TouchableOpacity>
      )}
    </View>
  );
}

export default function ModulesScreen() {
  const colors = useColors();
  const insets = useSafeAreaInsets();
  const { moduleStatus, loadModel, requestPermissions, refresh } = useAgent();
  const [refreshing, setRefreshing] = React.useState(false);
  const [thermal, setThermal] = React.useState<{
    level: string; inferenceSafe: boolean; trainingSafe: boolean; emergency: boolean;
  } | null>(null);

  React.useEffect(() => {
    AgentCoreBridge.getThermalStatus().then(setThermal).catch(() => {});
  }, []);

  const topPad = Platform.OS === "web" ? 67 : insets.top;
  const bottomPad = Platform.OS === "web" ? 34 : 0;

  const llm = moduleStatus?.llm;
  const ocr = moduleStatus?.ocr;
  const rl = moduleStatus?.rl;
  const mem = moduleStatus?.memory;
  const acc = moduleStatus?.accessibility;
  const sc = moduleStatus?.screenCapture;

  const onRefresh = async () => {
    setRefreshing(true);
    await Promise.all([
      refresh(),
      AgentCoreBridge.getThermalStatus().then(setThermal).catch(() => {}),
    ]);
    setRefreshing(false);
  };

  return (
    <ScrollView
      style={[styles.scroll, { backgroundColor: colors.background }]}
      contentContainerStyle={[
        styles.content,
        { paddingTop: topPad + 16, paddingBottom: bottomPad + 100 },
      ]}
      refreshControl={
        <RefreshControl
          refreshing={refreshing}
          onRefresh={onRefresh}
          tintColor={colors.primary}
        />
      }
    >
      <Text style={[styles.title, { color: colors.foreground }]}>Modules</Text>
      <Text style={[styles.subtitle, { color: colors.mutedForeground }]}>
        Kotlin brain layer status
      </Text>

      <SectionHeader title="Core Brain Layer" />

      <ModuleCard
        title="LLM Engine"
        subtitle="Llama 3.2 via llama.cpp"
        icon="cpu"
        ready={llm?.loaded ?? false}
        colors={colors}
        details={[
          { label: "Model", value: llm?.modelName ?? "Llama-3.2-1B-Instruct" },
          { label: "Quantization", value: llm?.quantization ?? "Q4_K_M" },
          { label: "Context", value: `${llm?.contextLength ?? 4096} tokens` },
          { label: "Speed", value: llm?.tokensPerSecond ? `${llm.tokensPerSecond} tok/s` : "—" },
          { label: "RAM usage", value: llm?.memoryMb ? `${llm.memoryMb} MB` : "—" },
          { label: "Framework", value: "llama.cpp + JNI" },
        ]}
        onAction={() => {
          Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
          loadModel();
        }}
        actionLabel={llm?.loaded ? "Reload Model" : "Load Model"}
      />

      <ModuleCard
        title="OCR Engine"
        subtitle="Google ML Kit Text Recognition"
        icon="eye"
        ready={ocr?.ready ?? false}
        colors={colors}
        details={[
          { label: "Engine", value: ocr?.engine ?? "ML Kit v2" },
          { label: "Resolution", value: "512×512 downsampled" },
          { label: "Output", value: "White-space structured text" },
          { label: "Threading", value: "Kotlin Coroutines" },
        ]}
      />

      <ModuleCard
        title="RL Module"
        subtitle="REINFORCE policy gradient + LoRA fine-tuning"
        icon="trending-up"
        ready={rl?.ready ?? false}
        colors={colors}
        details={[
          { label: "Algorithm", value: "REINFORCE (policy gradient)" },
          { label: "Optimizer", value: "Adam — adaptive moment estimation" },
          { label: "Backprop", value: "3-layer MLP, NEON SIMD math" },
          { label: "Episodes", value: String(rl?.episodesRun ?? 0) },
          { label: "LoRA version", value: rl?.loraVersion != null ? `v${rl.loraVersion}` : "v0 (untrained)" },
          { label: "Adapter loaded", value: rl?.adapterLoaded ? "Yes" : "No" },
          { label: "Untrained samples", value: String(rl?.untrainedSamples ?? 0) },
          { label: "Reward signal", value: "Action success + task completion" },
        ]}
      />

      <ModuleCard
        title="Memory Store"
        subtitle="SQLite + file-based embeddings"
        icon="database"
        ready={mem?.ready ?? false}
        colors={colors}
        details={[
          { label: "Embeddings", value: String(mem?.embeddingCount ?? 0) },
          { label: "DB size", value: mem?.dbSizeKb ? `${mem.dbSizeKb} KB` : "—" },
          { label: "Storage", value: "SQLite (app internal)" },
          { label: "Context window", value: "Summarized + retrieved" },
        ]}
      />

      <SectionHeader title="Device Health" />

      <ModuleCard
        title="Thermal Guard"
        subtitle="CPU/GPU temperature monitoring"
        icon="thermometer"
        ready={thermal !== null && !thermal.emergency}
        colors={colors}
        details={[
          {
            label: "Thermal level",
            value: thermal
              ? thermal.level.charAt(0).toUpperCase() + thermal.level.slice(1)
              : "Unknown",
          },
          { label: "Inference allowed", value: thermal ? (thermal.inferenceSafe ? "Yes" : "Paused — too hot") : "—" },
          { label: "Training allowed", value: thermal ? (thermal.trainingSafe ? "Yes" : "Paused — too hot") : "—" },
          { label: "Emergency throttle", value: thermal?.emergency ? "YES — agent stopped" : "No" },
          { label: "API backend", value: "ThermalManager (API 29) + battery fallback" },
        ]}
      />

      <SectionHeader title="System Control Layer" />
      <Text style={[styles.sysNote, { color: colors.mutedForeground }]}>
        JS never calls these directly — only via TurboModule bridge
      </Text>

      <ModuleCard
        title="AccessibilityService"
        subtitle="Gesture injection · UI tree traversal"
        icon="shield"
        ready={acc?.granted ?? false}
        colors={colors}
        details={[
          { label: "Status", value: acc?.active ? "Active" : "Inactive" },
          { label: "Permission", value: acc?.granted ? "Granted" : "Not granted" },
          { label: "Capabilities", value: "Tap, swipe, text, long-press" },
          { label: "Owner", value: "Kotlin AccessibilityService" },
        ]}
        onAction={() => {
          Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
          requestPermissions();
        }}
        actionLabel="Grant Permission"
      />

      <ModuleCard
        title="Screen Capture"
        subtitle="MediaProjection API"
        icon="monitor"
        ready={sc?.granted ?? false}
        colors={colors}
        details={[
          { label: "Status", value: sc?.active ? "Capturing" : "Inactive" },
          { label: "Permission", value: sc?.granted ? "Granted" : "Not granted" },
          { label: "Resolution", value: "512×512 (downsampled)" },
          { label: "Output", value: "Bitmap → OCR + LLM vision" },
        ]}
        onAction={() => {
          Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
          requestPermissions();
        }}
        actionLabel="Grant Permission"
      />

      <SectionHeader title="Bridge Layer" />
      <View
        style={[
          styles.bridgeInfo,
          {
            backgroundColor: colors.surface1,
            borderColor: colors.primary + "25",
            borderWidth: 1,
            borderRadius: 16,
          },
        ]}
      >
        <View style={styles.bridgeRow}>
          <Feather name="link" size={16} color={colors.primary} />
          <Text style={[styles.bridgeText, { color: colors.foreground }]}>
            TurboModules (New Architecture / JSI)
          </Text>
        </View>
        <Text style={[styles.bridgeDesc, { color: colors.mutedForeground }]}>
          JS UI calls Kotlin via JSI-bound TurboModules. Zero serialization overhead. Kotlin Coroutines handle threading. Phase 2 will thin the JS layer further.
        </Text>
        <View style={[styles.bridgePath, { backgroundColor: colors.surface3, borderRadius: 8 }]}>
          <Text style={[styles.bridgeCode, { color: colors.primary }]}>
            android/bridge/turbo/ → AgentCoreModule.kt
          </Text>
        </View>
        <View style={[styles.bridgePath, { backgroundColor: colors.surface3, borderRadius: 8 }]}>
          <Text style={[styles.bridgeCode, { color: colors.accent }]}>
            native-bindings/AgentCoreBridge.ts → stub → TurboModule
          </Text>
        </View>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  scroll: { flex: 1 },
  content: { paddingHorizontal: 20, gap: 14 },
  title: { fontSize: 28, fontFamily: "Inter_700Bold" },
  subtitle: { fontSize: 14, fontFamily: "Inter_400Regular", marginTop: 2, marginBottom: 4 },
  sysNote: { fontSize: 12, fontFamily: "Inter_400Regular", marginTop: -8, marginBottom: 4 },
  card: { padding: 16, gap: 14 },
  cardHeader: { flexDirection: "row", alignItems: "center", gap: 12 },
  cardIcon: {
    width: 42,
    height: 42,
    borderRadius: 12,
    alignItems: "center",
    justifyContent: "center",
  },
  cardTitles: { flex: 1, gap: 2 },
  cardTitle: { fontSize: 15, fontFamily: "Inter_600SemiBold" },
  cardSub: { fontSize: 12, fontFamily: "Inter_400Regular" },
  statusPill: { paddingHorizontal: 8, paddingVertical: 4, borderRadius: 6 },
  statusPillText: { fontSize: 10, fontFamily: "Inter_700Bold", letterSpacing: 0.5 },
  detailsBlock: { gap: 0, borderTopWidth: 1, paddingTop: 8 },
  detailRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    paddingVertical: 6,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  detailLabel: { fontSize: 12, fontFamily: "Inter_400Regular" },
  detailValue: { fontSize: 12, fontFamily: "Inter_500Medium" },
  cardAction: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    padding: 12,
  },
  cardActionText: { fontSize: 13, fontFamily: "Inter_600SemiBold" },
  bridgeInfo: { padding: 16, gap: 12 },
  bridgeRow: { flexDirection: "row", alignItems: "center", gap: 10 },
  bridgeText: { fontSize: 14, fontFamily: "Inter_600SemiBold" },
  bridgeDesc: { fontSize: 12, fontFamily: "Inter_400Regular", lineHeight: 18 },
  bridgePath: { padding: 10 },
  bridgeCode: { fontSize: 12, fontFamily: "Inter_500Medium" },
});
