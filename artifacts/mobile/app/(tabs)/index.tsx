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
import { router } from "expo-router";

import { useAgent } from "@/context/AgentContext";
import { useColors } from "@/hooks/useColors";
import { StatusDot } from "@/components/StatusDot";
import { MetricCard } from "@/components/MetricCard";
import { ModuleRow } from "@/components/ModuleRow";
import { SectionHeader } from "@/components/SectionHeader";

function formatUptime(start: number | null): string {
  if (!start) return "—";
  const secs = Math.floor((Date.now() - start) / 1000);
  const h = Math.floor(secs / 3600);
  const m = Math.floor((secs % 3600) / 60);
  const s = secs % 60;
  if (h > 0) return `${h}h ${m}m`;
  if (m > 0) return `${m}m ${s}s`;
  return `${s}s`;
}

export default function DashboardScreen() {
  const colors = useColors();
  const insets = useSafeAreaInsets();
  const { agentState, moduleStatus, isLoading, refresh } = useAgent();

  const [refreshing, setRefreshing] = React.useState(false);

  const onRefresh = async () => {
    setRefreshing(true);
    await refresh();
    setRefreshing(false);
  };

  const topPad = Platform.OS === "web" ? 67 : insets.top;
  const bottomPad = Platform.OS === "web" ? 34 : 0;

  const status = agentState?.status ?? "idle";

  const statusLabel =
    status === "running"
      ? "Agent Running"
      : status === "paused"
      ? "Agent Paused"
      : status === "error"
      ? "Error"
      : "Agent Idle";

  const llm = moduleStatus?.llm;
  const mem = moduleStatus?.memory;

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
      {/* Header */}
      <View style={styles.header}>
        <View>
          <Text style={[styles.greeting, { color: colors.mutedForeground }]}>
            ARIA
          </Text>
          <Text style={[styles.title, { color: colors.foreground }]}>
            Dashboard
          </Text>
        </View>
        <TouchableOpacity
          onPress={() => {
            Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
            router.push("/(tabs)/settings");
          }}
          style={[styles.iconBtn, { backgroundColor: colors.surface2 }]}
        >
          <Feather name="settings" size={18} color={colors.foreground} />
        </TouchableOpacity>
      </View>

      {/* Status Banner */}
      <TouchableOpacity
        activeOpacity={0.85}
        onPress={() => {
          Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
          router.push("/(tabs)/control");
        }}
        style={[
          styles.statusBanner,
          {
            backgroundColor: colors.surface1,
            borderColor:
              status === "running"
                ? colors.success + "33"
                : status === "error"
                ? colors.destructive + "33"
                : colors.border,
            borderWidth: 1,
            borderRadius: 16,
          },
        ]}
      >
        <View style={styles.statusLeft}>
          <StatusDot status={status} size={12} />
          <View style={styles.statusText}>
            <Text style={[styles.statusLabel, { color: colors.foreground }]}>
              {statusLabel}
            </Text>
            <Text style={[styles.statusSub, { color: colors.mutedForeground }]}>
              {agentState?.currentTask ?? "No active task"}
            </Text>
          </View>
        </View>
        <View style={[styles.controlBtn, { backgroundColor: colors.primary }]}>
          <Feather
            name={status === "running" ? "pause" : "play"}
            size={14}
            color={colors.primaryForeground}
          />
        </View>
      </TouchableOpacity>

      {/* Current App */}
      {agentState?.currentApp ? (
        <View
          style={[
            styles.appBadge,
            { backgroundColor: colors.accent + "15", borderRadius: 10 },
          ]}
        >
          <Feather name="smartphone" size={14} color={colors.accent} />
          <Text style={[styles.appText, { color: colors.accent }]}>
            {agentState.currentApp}
          </Text>
        </View>
      ) : null}

      {/* Metrics */}
      <SectionHeader title="Session Metrics" />
      <View style={styles.metricsRow}>
        <MetricCard
          label="Actions"
          value={String(agentState?.actionsPerformed ?? 0)}
          accent
        />
        <MetricCard
          label="Success"
          value={
            agentState?.actionsPerformed
              ? `${Math.round(agentState.successRate * 100)}%`
              : "—"
          }
        />
        <MetricCard
          label="Uptime"
          value={formatUptime(agentState?.sessionStartedAt ?? null)}
        />
      </View>

      {/* LLM Stats */}
      <SectionHeader title="LLM Performance" />
      <View style={styles.metricsRow}>
        <MetricCard
          label="tok/s"
          value={llm?.tokensPerSecond ? String(llm.tokensPerSecond) : "—"}
          accent
        />
        <MetricCard
          label="RAM"
          value={llm?.memoryMb ? `${llm.memoryMb}MB` : "—"}
        />
        <MetricCard
          label="Context"
          value={llm?.contextLength ? `${llm.contextLength}` : "—"}
          subLabel="tokens"
        />
      </View>

      {/* Module Status */}
      <SectionHeader
        title="Module Status"
        right={
          <TouchableOpacity
            onPress={() => router.push("/(tabs)/modules")}
          >
            <Text style={[styles.seeAll, { color: colors.primary }]}>
              See all
            </Text>
          </TouchableOpacity>
        }
      />
      <View style={styles.moduleList}>
        <ModuleRow
          icon="cpu"
          label="LLM Engine"
          sublabel={
            llm
              ? `${llm.modelName} · ${llm.quantization}`
              : "Llama-3.2-1B · Q4_K_M"
          }
          ready={llm?.loaded ?? false}
        />
        <ModuleRow
          icon="eye"
          label="OCR Engine"
          sublabel={moduleStatus?.ocr.engine ?? "ML Kit"}
          ready={moduleStatus?.ocr.ready ?? false}
        />
        <ModuleRow
          icon="trending-up"
          label="RL Module"
          sublabel={
            moduleStatus?.rl
              ? `${moduleStatus.rl.episodesRun} episodes`
              : "No episodes yet"
          }
          ready={moduleStatus?.rl.ready ?? false}
        />
        <ModuleRow
          icon="database"
          label="Memory Store"
          sublabel={
            mem
              ? `${mem.embeddingCount} embeddings · ${mem.dbSizeKb}KB`
              : "SQLite + embeddings"
          }
          ready={mem?.ready ?? false}
        />
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  scroll: { flex: 1 },
  content: { paddingHorizontal: 20, gap: 16 },
  header: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    marginBottom: 4,
  },
  greeting: {
    fontSize: 11,
    fontFamily: "Inter_600SemiBold",
    letterSpacing: 3,
  },
  title: {
    fontSize: 28,
    fontFamily: "Inter_700Bold",
    marginTop: 2,
  },
  iconBtn: {
    width: 40,
    height: 40,
    borderRadius: 12,
    alignItems: "center",
    justifyContent: "center",
  },
  statusBanner: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    padding: 16,
  },
  statusLeft: { flexDirection: "row", alignItems: "center", gap: 12, flex: 1 },
  statusText: { flex: 1 },
  statusLabel: {
    fontSize: 15,
    fontFamily: "Inter_600SemiBold",
  },
  statusSub: {
    fontSize: 12,
    fontFamily: "Inter_400Regular",
    marginTop: 2,
  },
  controlBtn: {
    width: 34,
    height: 34,
    borderRadius: 10,
    alignItems: "center",
    justifyContent: "center",
  },
  appBadge: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    paddingHorizontal: 12,
    paddingVertical: 8,
    alignSelf: "flex-start",
  },
  appText: {
    fontSize: 13,
    fontFamily: "Inter_500Medium",
  },
  metricsRow: {
    flexDirection: "row",
    gap: 10,
    marginBottom: 4,
  },
  moduleList: { gap: 8, marginBottom: 4 },
  seeAll: {
    fontSize: 12,
    fontFamily: "Inter_500Medium",
  },
});
