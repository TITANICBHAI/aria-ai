import React, { useState } from "react";
import {
  ActivityIndicator,
  Alert,
  Platform,
  ScrollView,
  StyleSheet,
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
import { StatusDot } from "@/components/StatusDot";
import { SectionHeader } from "@/components/SectionHeader";

const PRESET_GOALS = [
  "Open YouTube and play the top trending video",
  "Open Gmail and summarize my last 5 unread emails",
  "Open Maps and find the nearest coffee shop",
  "Open Settings and enable Do Not Disturb",
  "Open Chrome and search for today's news",
];

export default function ControlScreen() {
  const colors = useColors();
  const insets = useSafeAreaInsets();
  const { agentState, startAgent, stopAgent, pauseAgent, loadModel, moduleStatus, isLoading } = useAgent();
  const [goal, setGoal] = useState("");
  const [working, setWorking] = useState(false);

  const topPad = Platform.OS === "web" ? 67 : insets.top;
  const bottomPad = Platform.OS === "web" ? 34 : 0;

  const status = agentState?.status ?? "idle";
  const llmLoaded = moduleStatus?.llm.loaded ?? false;

  const handleStart = async () => {
    if (!goal.trim()) {
      Alert.alert("Goal required", "Enter a goal for the agent to pursue.");
      return;
    }
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy);
    setWorking(true);
    await startAgent(goal.trim());
    setWorking(false);
  };

  const handleStop = async () => {
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning);
    setWorking(true);
    await stopAgent();
    setWorking(false);
    setGoal("");
  };

  const handlePause = async () => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
    await pauseAgent();
  };

  const handleLoadModel = async () => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    setWorking(true);
    await loadModel();
    setWorking(false);
  };

  return (
    <ScrollView
      style={[styles.scroll, { backgroundColor: colors.background }]}
      contentContainerStyle={[
        styles.content,
        { paddingTop: topPad + 16, paddingBottom: bottomPad + 100 },
      ]}
      keyboardShouldPersistTaps="handled"
    >
      {/* Header */}
      <View style={styles.headerRow}>
        <Text style={[styles.title, { color: colors.foreground }]}>Control</Text>
        <View style={styles.statusRow}>
          <StatusDot status={status} size={10} />
          <Text style={[styles.statusText, { color: colors.mutedForeground }]}>
            {status.charAt(0).toUpperCase() + status.slice(1)}
          </Text>
        </View>
      </View>

      {/* LLM Load Gate */}
      {!llmLoaded && (
        <TouchableOpacity
          onPress={handleLoadModel}
          disabled={working}
          style={[
            styles.loadBtn,
            {
              backgroundColor: colors.accent + "20",
              borderColor: colors.accent + "55",
              borderWidth: 1,
              borderRadius: 14,
            },
          ]}
        >
          {working ? (
            <ActivityIndicator color={colors.accent} />
          ) : (
            <Feather name="cpu" size={20} color={colors.accent} />
          )}
          <View style={styles.loadText}>
            <Text style={[styles.loadTitle, { color: colors.accent }]}>
              Load LLM Engine
            </Text>
            <Text style={[styles.loadSub, { color: colors.mutedForeground }]}>
              Llama 3.2-1B Q4_K_M · ~870MB RAM
            </Text>
          </View>
          <Feather name="chevron-right" size={18} color={colors.accent} />
        </TouchableOpacity>
      )}

      {/* Goal Input */}
      <SectionHeader title="Agent Goal" />
      <View
        style={[
          styles.inputBox,
          {
            backgroundColor: colors.surface1,
            borderColor: colors.border,
            borderWidth: 1,
            borderRadius: 14,
          },
        ]}
      >
        <TextInput
          value={goal}
          onChangeText={setGoal}
          placeholder="Describe what you want the agent to do…"
          placeholderTextColor={colors.mutedForeground}
          multiline
          numberOfLines={3}
          style={[
            styles.input,
            { color: colors.foreground, fontFamily: "Inter_400Regular" },
          ]}
          editable={status !== "running"}
        />
      </View>

      {/* Preset Goals */}
      <SectionHeader title="Quick Goals" />
      <View style={styles.presets}>
        {PRESET_GOALS.map((p) => (
          <TouchableOpacity
            key={p}
            onPress={() => {
              Haptics.selectionAsync();
              setGoal(p);
            }}
            style={[
              styles.preset,
              {
                backgroundColor: colors.surface2,
                borderColor: colors.border,
                borderWidth: 1,
                borderRadius: 10,
              },
            ]}
          >
            <Feather name="zap" size={13} color={colors.primary} />
            <Text style={[styles.presetText, { color: colors.foreground }]}>
              {p}
            </Text>
          </TouchableOpacity>
        ))}
      </View>

      {/* Action Buttons */}
      <View style={styles.actionRow}>
        {status === "idle" || status === "error" ? (
          <TouchableOpacity
            onPress={handleStart}
            disabled={working || !llmLoaded}
            style={[
              styles.primaryBtn,
              {
                backgroundColor:
                  working || !llmLoaded ? colors.muted : colors.primary,
                borderRadius: 14,
              },
            ]}
          >
            {working ? (
              <ActivityIndicator color={colors.primaryForeground} />
            ) : (
              <Feather name="play" size={20} color={colors.primaryForeground} />
            )}
            <Text
              style={[
                styles.primaryBtnText,
                {
                  color: working || !llmLoaded
                    ? colors.mutedForeground
                    : colors.primaryForeground,
                },
              ]}
            >
              {llmLoaded ? "Start Agent" : "Load Model First"}
            </Text>
          </TouchableOpacity>
        ) : (
          <View style={styles.runningBtns}>
            <TouchableOpacity
              onPress={handlePause}
              style={[
                styles.secondaryBtn,
                {
                  backgroundColor: colors.surface1,
                  borderColor: colors.warning + "44",
                  borderWidth: 1,
                  borderRadius: 14,
                },
              ]}
            >
              <Feather
                name={status === "paused" ? "play" : "pause"}
                size={18}
                color={colors.warning}
              />
              <Text style={[styles.secondaryBtnText, { color: colors.warning }]}>
                {status === "paused" ? "Resume" : "Pause"}
              </Text>
            </TouchableOpacity>
            <TouchableOpacity
              onPress={handleStop}
              style={[
                styles.secondaryBtn,
                {
                  backgroundColor: colors.surface1,
                  borderColor: colors.destructive + "44",
                  borderWidth: 1,
                  borderRadius: 14,
                },
              ]}
            >
              <Feather name="square" size={18} color={colors.destructive} />
              <Text
                style={[
                  styles.secondaryBtnText,
                  { color: colors.destructive },
                ]}
              >
                Stop
              </Text>
            </TouchableOpacity>
          </View>
        )}
      </View>

      {/* Active Task Display */}
      {agentState?.currentTask && (
        <View
          style={[
            styles.taskBox,
            {
              backgroundColor: colors.success + "10",
              borderColor: colors.success + "30",
              borderWidth: 1,
              borderRadius: 12,
            },
          ]}
        >
          <Feather name="activity" size={14} color={colors.success} />
          <Text style={[styles.taskText, { color: colors.foreground }]}>
            {agentState.currentTask}
          </Text>
        </View>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  scroll: { flex: 1 },
  content: { paddingHorizontal: 20, gap: 14 },
  headerRow: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    marginBottom: 4,
  },
  title: { fontSize: 28, fontFamily: "Inter_700Bold" },
  statusRow: { flexDirection: "row", alignItems: "center", gap: 8 },
  statusText: { fontSize: 13, fontFamily: "Inter_500Medium" },
  loadBtn: {
    flexDirection: "row",
    alignItems: "center",
    padding: 16,
    gap: 12,
  },
  loadText: { flex: 1 },
  loadTitle: { fontSize: 15, fontFamily: "Inter_600SemiBold" },
  loadSub: { fontSize: 12, fontFamily: "Inter_400Regular", marginTop: 2 },
  inputBox: { padding: 14 },
  input: { fontSize: 15, lineHeight: 22, minHeight: 80, textAlignVertical: "top" },
  presets: { gap: 8 },
  preset: {
    flexDirection: "row",
    alignItems: "flex-start",
    gap: 10,
    padding: 12,
  },
  presetText: { fontSize: 13, fontFamily: "Inter_400Regular", flex: 1, lineHeight: 18 },
  actionRow: { marginTop: 4 },
  primaryBtn: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 10,
    paddingVertical: 16,
  },
  primaryBtnText: { fontSize: 16, fontFamily: "Inter_600SemiBold" },
  runningBtns: { flexDirection: "row", gap: 12 },
  secondaryBtn: {
    flex: 1,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
    paddingVertical: 14,
  },
  secondaryBtnText: { fontSize: 14, fontFamily: "Inter_600SemiBold" },
  taskBox: {
    flexDirection: "row",
    alignItems: "flex-start",
    gap: 10,
    padding: 14,
  },
  taskText: { flex: 1, fontSize: 13, fontFamily: "Inter_400Regular", lineHeight: 18 },
});
