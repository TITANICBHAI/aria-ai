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

import { router } from "expo-router";
import { useAgent } from "@/context/AgentContext";
import { useColors } from "@/hooks/useColors";
import { QueuedTask } from "@/native-bindings/AgentCoreBridge";
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
  const {
    agentState,
    startAgent,
    stopAgent,
    pauseAgent,
    loadModel,
    moduleStatus,
    isLoading,
    taskQueue,
    chainedTask,
    enqueueTask,
    removeQueuedTask,
    clearTaskQueue,
    dismissChainNotification,
  } = useAgent();

  const [goal, setGoal] = useState("");
  const [working, setWorking] = useState(false);
  const [queueGoal, setQueueGoal] = useState("");
  const [queueApp, setQueueApp] = useState("");
  const [enqueueing, setEnqueueing] = useState(false);

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

  const handleEnqueue = async () => {
    if (!queueGoal.trim()) {
      Alert.alert("Goal required", "Enter a goal to add to the queue.");
      return;
    }
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    setEnqueueing(true);
    await enqueueTask(queueGoal.trim(), queueApp.trim(), 0);
    setQueueGoal("");
    setQueueApp("");
    setEnqueueing(false);
  };

  const handleRemoveTask = async (taskId: string) => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    await removeQueuedTask(taskId);
  };

  const handleClearQueue = () => {
    Alert.alert(
      "Clear Queue",
      "Remove all queued tasks?",
      [
        { text: "Cancel", style: "cancel" },
        {
          text: "Clear",
          style: "destructive",
          onPress: async () => {
            Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning);
            await clearTaskQueue();
          },
        },
      ]
    );
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

      {/* Chained Task Notification */}
      {chainedTask && (
        <View
          style={[
            styles.chainBanner,
            {
              backgroundColor: colors.primary + "18",
              borderColor: colors.primary + "44",
              borderWidth: 1,
              borderRadius: 12,
            },
          ]}
        >
          <View style={styles.chainBannerLeft}>
            <Feather name="link" size={14} color={colors.primary} />
            <View style={{ flex: 1 }}>
              <Text style={[styles.chainBannerTitle, { color: colors.primary }]}>
                Auto-chained to next task
              </Text>
              <Text
                style={[styles.chainBannerGoal, { color: colors.foreground }]}
                numberOfLines={2}
              >
                {chainedTask.goal}
              </Text>
              {chainedTask.appPackage ? (
                <Text style={[styles.chainBannerApp, { color: colors.mutedForeground }]}>
                  {chainedTask.appPackage}
                  {chainedTask.queueSize > 0
                    ? ` · ${chainedTask.queueSize} more in queue`
                    : ""}
                </Text>
              ) : chainedTask.queueSize > 0 ? (
                <Text style={[styles.chainBannerApp, { color: colors.mutedForeground }]}>
                  {chainedTask.queueSize} more in queue
                </Text>
              ) : null}
            </View>
          </View>
          <TouchableOpacity
            onPress={() => {
              Haptics.selectionAsync();
              dismissChainNotification();
            }}
            hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
          >
            <Feather name="x" size={16} color={colors.mutedForeground} />
          </TouchableOpacity>
        </View>
      )}

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

      {/* Task Queue */}
      <SectionHeader title={`Task Queue${taskQueue.length > 0 ? ` · ${taskQueue.length}` : ""}`} />

      {/* Enqueue Form */}
      <View
        style={[
          styles.queueForm,
          {
            backgroundColor: colors.surface1,
            borderColor: colors.border,
            borderWidth: 1,
            borderRadius: 14,
          },
        ]}
      >
        <TextInput
          value={queueGoal}
          onChangeText={setQueueGoal}
          placeholder="Goal to queue after current task…"
          placeholderTextColor={colors.mutedForeground}
          style={[
            styles.queueInput,
            {
              color: colors.foreground,
              fontFamily: "Inter_400Regular",
              borderBottomColor: colors.border,
              borderBottomWidth: StyleSheet.hairlineWidth,
            },
          ]}
        />
        <TextInput
          value={queueApp}
          onChangeText={setQueueApp}
          placeholder="App package (optional, e.g. com.google.android.youtube)"
          placeholderTextColor={colors.mutedForeground}
          autoCapitalize="none"
          style={[
            styles.queueInput,
            {
              color: colors.foreground,
              fontFamily: "Inter_400Regular",
              borderBottomColor: colors.border,
              borderBottomWidth: StyleSheet.hairlineWidth,
            },
          ]}
        />
        <TouchableOpacity
          onPress={handleEnqueue}
          disabled={enqueueing || !queueGoal.trim()}
          style={[
            styles.enqueueBtn,
            {
              backgroundColor:
                enqueueing || !queueGoal.trim()
                  ? colors.muted
                  : colors.accent + "22",
              borderRadius: 10,
            },
          ]}
        >
          {enqueueing ? (
            <ActivityIndicator size="small" color={colors.accent} />
          ) : (
            <Feather
              name="plus"
              size={15}
              color={queueGoal.trim() ? colors.accent : colors.mutedForeground}
            />
          )}
          <Text
            style={[
              styles.enqueueBtnText,
              {
                color: queueGoal.trim()
                  ? colors.accent
                  : colors.mutedForeground,
              },
            ]}
          >
            Add to Queue
          </Text>
        </TouchableOpacity>
      </View>

      {/* Queue List */}
      {taskQueue.length === 0 ? (
        <View
          style={[
            styles.emptyQueue,
            {
              backgroundColor: colors.surface1,
              borderColor: colors.border,
              borderWidth: 1,
              borderRadius: 12,
            },
          ]}
        >
          <Feather name="list" size={16} color={colors.mutedForeground} />
          <Text style={[styles.emptyQueueText, { color: colors.mutedForeground }]}>
            No queued tasks · Add one above to chain automatically after the current task finishes
          </Text>
        </View>
      ) : (
        <View style={styles.queueList}>
          {taskQueue.map((task: QueuedTask, idx: number) => (
            <View
              key={task.id}
              style={[
                styles.queueItem,
                {
                  backgroundColor: colors.surface1,
                  borderColor: colors.border,
                  borderWidth: 1,
                  borderRadius: 12,
                },
              ]}
            >
              <View
                style={[
                  styles.queuePriority,
                  { backgroundColor: colors.primary + "22", borderRadius: 6 },
                ]}
              >
                <Text style={[styles.queuePriorityText, { color: colors.primary }]}>
                  #{idx + 1}
                </Text>
              </View>
              <View style={styles.queueItemBody}>
                <Text
                  style={[styles.queueGoalText, { color: colors.foreground }]}
                  numberOfLines={2}
                >
                  {task.goal}
                </Text>
                {task.appPackage ? (
                  <Text
                    style={[styles.queueAppText, { color: colors.mutedForeground }]}
                    numberOfLines={1}
                  >
                    {task.appPackage}
                  </Text>
                ) : null}
              </View>
              <TouchableOpacity
                onPress={() => handleRemoveTask(task.id)}
                hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
              >
                <Feather name="trash-2" size={15} color={colors.destructive + "99"} />
              </TouchableOpacity>
            </View>
          ))}
          <TouchableOpacity
            onPress={handleClearQueue}
            style={[
              styles.clearQueueBtn,
              {
                backgroundColor: colors.destructive + "12",
                borderColor: colors.destructive + "30",
                borderWidth: 1,
                borderRadius: 10,
              },
            ]}
          >
            <Feather name="trash" size={14} color={colors.destructive} />
            <Text style={[styles.clearQueueText, { color: colors.destructive }]}>
              Clear Queue
            </Text>
          </TouchableOpacity>
        </View>
      )}

      {/* Object Labeler Entry */}
      <SectionHeader title="Teach the Agent" />
      <TouchableOpacity
        onPress={() => {
          Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
          router.push("/labeler");
        }}
        style={[
          styles.teachBtn,
          {
            backgroundColor: colors.surface1,
            borderColor: colors.border,
            borderWidth: 1,
            borderRadius: 14,
          },
        ]}
      >
        <View style={[styles.teachIcon, { backgroundColor: colors.accent + "20" }]}>
          <Feather name="tag" size={20} color={colors.accent} />
        </View>
        <View style={styles.teachText}>
          <Text style={[styles.teachTitle, { color: colors.foreground }]}>
            Object Labeler
          </Text>
          <Text style={[styles.teachSub, { color: colors.mutedForeground }]}>
            Annotate UI elements · LLM enriches context · Agent learns
          </Text>
        </View>
        <Feather name="chevron-right" size={18} color={colors.mutedForeground} />
      </TouchableOpacity>
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
  chainBanner: {
    flexDirection: "row",
    alignItems: "flex-start",
    padding: 12,
    gap: 10,
  },
  chainBannerLeft: {
    flex: 1,
    flexDirection: "row",
    alignItems: "flex-start",
    gap: 8,
  },
  chainBannerTitle: { fontSize: 11, fontFamily: "Inter_700Bold", letterSpacing: 0.4 },
  chainBannerGoal: { fontSize: 13, fontFamily: "Inter_500Medium", marginTop: 2, lineHeight: 18 },
  chainBannerApp: { fontSize: 11, fontFamily: "Inter_400Regular", marginTop: 2 },
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
  queueForm: { gap: 0, overflow: "hidden" },
  queueInput: {
    paddingHorizontal: 14,
    paddingVertical: 12,
    fontSize: 14,
    lineHeight: 20,
  },
  enqueueBtn: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
    paddingVertical: 11,
    margin: 10,
  },
  enqueueBtnText: { fontSize: 14, fontFamily: "Inter_600SemiBold" },
  emptyQueue: {
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
    padding: 14,
  },
  emptyQueueText: {
    fontSize: 12,
    fontFamily: "Inter_400Regular",
    lineHeight: 18,
    flex: 1,
  },
  queueList: { gap: 8 },
  queueItem: {
    flexDirection: "row",
    alignItems: "center",
    padding: 12,
    gap: 10,
  },
  queuePriority: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    alignSelf: "flex-start",
  },
  queuePriorityText: { fontSize: 11, fontFamily: "Inter_700Bold" },
  queueItemBody: { flex: 1, gap: 3 },
  queueGoalText: { fontSize: 13, fontFamily: "Inter_500Medium", lineHeight: 18 },
  queueAppText: { fontSize: 11, fontFamily: "Inter_400Regular" },
  clearQueueBtn: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
    gap: 8,
    paddingVertical: 10,
    marginTop: 2,
  },
  clearQueueText: { fontSize: 13, fontFamily: "Inter_500Medium" },
  teachBtn: {
    flexDirection: "row",
    alignItems: "center",
    padding: 14,
    gap: 12,
  },
  teachIcon: {
    width: 42,
    height: 42,
    borderRadius: 12,
    alignItems: "center",
    justifyContent: "center",
  },
  teachText: { flex: 1 },
  teachTitle: { fontSize: 15, fontFamily: "Inter_600SemiBold" },
  teachSub: { fontSize: 12, fontFamily: "Inter_400Regular", marginTop: 2 },
});
