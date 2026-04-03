import React, { useCallback, useEffect, useRef, useState } from "react";
import {
  ActivityIndicator,
  FlatList,
  KeyboardAvoidingView,
  Platform,
  StyleSheet,
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
import { AgentCoreBridge } from "@/native-bindings/AgentCoreBridge";

// ─── Types ────────────────────────────────────────────────────────────────────

type Role = "user" | "aria" | "system";

interface Message {
  id: string;
  role: Role;
  text: string;
  ts: number;
}

// ─── Context prompt builder ────────────────────────────────────────────────────

async function buildContextPrompt(
  history: Message[],
  userMessage: string,
): Promise<string> {
  const [state, memEntries, queue, appSkills] = await Promise.all([
    AgentCoreBridge.getAgentState().catch(() => null),
    AgentCoreBridge.getMemoryEntries(5).catch(() => []),
    AgentCoreBridge.getTaskQueue().catch(() => []),
    AgentCoreBridge.getAllAppSkills().catch(() => []),
  ]);

  const lines: string[] = [];

  // ── System identity ──────────────────────────────────────────────────────
  lines.push(
    "You are ARIA (Adaptive Reasoning Intelligence Agent), an on-device Android AI agent",
    "running locally on a Samsung Galaxy M31 (Exynos 9611, 6 GB LPDDR4X RAM).",
    "You reason and act entirely on-device. No cloud. No internet. All logic runs in Kotlin.",
    "You have: llama.cpp LLM inference · ML Kit OCR · AccessibilityService gestures ·",
    "REINFORCE RL · LoRA fine-tuning · SQLite memory store · per-app skill registry.",
    "Answer concisely and helpfully. When the user asks you to do something on the device,",
    "explain that they should use the Control tab to start a task — you cannot act directly",
    "from this chat interface.",
    "",
  );

  // ── Device / agent state ─────────────────────────────────────────────────
  lines.push("[DEVICE STATE]");
  if (state) {
    lines.push(
      `Agent status    : ${state.status}`,
      `LLM loaded      : ${state.llmLoaded ? "yes" : "no — load it in Control first"}`,
      `Current task    : ${state.currentTask ?? "none"}`,
      `Current app     : ${state.currentApp ?? "none"}`,
      `Actions taken   : ${state.actionsPerformed}`,
      `Token rate      : ${state.tokenRate > 0 ? `${state.tokenRate} tok/s` : "—"}`,
      `RAM used        : ${state.memoryUsedMb > 0 ? `${state.memoryUsedMb} MB` : "—"}`,
    );
  } else {
    lines.push("(agent state unavailable — running on web preview stub)");
  }
  lines.push("");

  // ── Memory store ─────────────────────────────────────────────────────────
  if (memEntries.length > 0) {
    lines.push("[RECENT MEMORY] (last 5 entries from SQLite)");
    memEntries.forEach((m, i) => {
      lines.push(
        `${i + 1}. [${m.app || "global"}] (conf: ${m.confidence.toFixed(2)}) ${m.summary}`,
      );
    });
    lines.push("");
  }

  // ── Task queue ───────────────────────────────────────────────────────────
  if (queue.length > 0) {
    lines.push(`[TASK QUEUE] (${queue.length} pending)`);
    queue.slice(0, 3).forEach((t, i) => {
      lines.push(
        `${i + 1}. ${t.goal}${t.appPackage ? ` → ${t.appPackage}` : ""}`,
      );
    });
    if (queue.length > 3) lines.push(`   …and ${queue.length - 3} more`);
    lines.push("");
  }

  // ── App skills ───────────────────────────────────────────────────────────
  if (appSkills.length > 0) {
    lines.push("[LEARNED APP SKILLS]");
    appSkills.slice(0, 4).forEach((s) => {
      const name =
        s.appName || s.appPackage.split(".").pop() || s.appPackage;
      lines.push(
        `• ${name}: ${s.taskSuccess} successes / ${s.taskFailure} failures,` +
          ` avg ${s.avgSteps.toFixed(1)} steps${s.promptHint ? ` — hint: "${s.promptHint}"` : ""}`,
      );
    });
    lines.push("");
  }

  // ── Conversation history ─────────────────────────────────────────────────
  const recent = history.filter((m) => m.role !== "system").slice(-8);
  if (recent.length > 0) {
    lines.push("[CONVERSATION]");
    recent.forEach((m) => {
      lines.push(
        `${m.role === "user" ? "User" : "ARIA"}: ${m.text}`,
      );
    });
    lines.push("");
  }

  // ── Current user message ─────────────────────────────────────────────────
  lines.push(`User: ${userMessage}`);
  lines.push("ARIA:");

  return lines.join("\n");
}

// ─── Message bubble ────────────────────────────────────────────────────────────

function Bubble({
  msg,
  colors,
}: {
  msg: Message;
  colors: any;
}) {
  const isUser = msg.role === "user";
  const isSystem = msg.role === "system";

  if (isSystem) {
    return (
      <View style={styles.systemRow}>
        <Text style={[styles.systemText, { color: colors.mutedForeground }]}>
          {msg.text}
        </Text>
      </View>
    );
  }

  return (
    <View style={[styles.bubbleRow, isUser ? styles.bubbleRowUser : styles.bubbleRowAria]}>
      {!isUser && (
        <View
          style={[
            styles.avatarIcon,
            { backgroundColor: colors.primary + "22", borderRadius: 10 },
          ]}
        >
          <Feather name="zap" size={14} color={colors.primary} />
        </View>
      )}
      <View
        style={[
          styles.bubble,
          {
            backgroundColor: isUser
              ? colors.primary
              : colors.surface1,
            borderColor: isUser ? "transparent" : colors.border,
            borderWidth: isUser ? 0 : 1,
            borderRadius: isUser ? 18 : 14,
            borderBottomRightRadius: isUser ? 4 : 14,
            borderBottomLeftRadius: isUser ? 14 : 4,
          },
        ]}
      >
        <Text
          style={[
            styles.bubbleText,
            { color: isUser ? colors.primaryForeground : colors.foreground },
          ]}
        >
          {msg.text}
        </Text>
      </View>
    </View>
  );
}

// ─── Typing indicator ────────────────────────────────────────────────────────

function TypingIndicator({ colors }: { colors: any }) {
  return (
    <View style={[styles.bubbleRow, styles.bubbleRowAria]}>
      <View
        style={[
          styles.avatarIcon,
          { backgroundColor: colors.primary + "22", borderRadius: 10 },
        ]}
      >
        <Feather name="zap" size={14} color={colors.primary} />
      </View>
      <View
        style={[
          styles.bubble,
          {
            backgroundColor: colors.surface1,
            borderColor: colors.border,
            borderWidth: 1,
            borderRadius: 14,
            borderBottomLeftRadius: 4,
          },
        ]}
      >
        <ActivityIndicator size="small" color={colors.primary} />
      </View>
    </View>
  );
}

// ─── Main screen ──────────────────────────────────────────────────────────────

const WELCOME: Message = {
  id: "welcome",
  role: "system",
  text: "Chat with ARIA — your messages are sent to the on-device LLM with full context: agent state, memory, task queue and app skills injected automatically.",
  ts: Date.now(),
};

export default function ChatScreen() {
  const colors = useColors();
  const insets = useSafeAreaInsets();
  const { agentState, moduleStatus, taskQueue, appSkills } = useAgent();

  const [messages, setMessages] = useState<Message[]>([WELCOME]);
  const [input, setInput] = useState("");
  const [thinking, setThinking] = useState(false);
  const [contextLine, setContextLine] = useState<string | null>(null);

  const listRef = useRef<FlatList>(null);

  const topPad = Platform.OS === "web" ? 67 : insets.top;
  const bottomPad = Platform.OS === "web" ? 34 : insets.bottom;

  const llmLoaded = moduleStatus?.llm.loaded ?? false;

  // Scroll to bottom when new messages arrive
  useEffect(() => {
    if (messages.length > 1) {
      setTimeout(() => listRef.current?.scrollToEnd({ animated: true }), 80);
    }
  }, [messages.length, thinking]);

  // Build a short context summary for the status bar
  useFocusEffect(
    useCallback(() => {
      const parts: string[] = [];
      if (agentState?.status && agentState.status !== "idle")
        parts.push(`agent: ${agentState.status}`);
      if (taskQueue.length > 0) parts.push(`${taskQueue.length} queued`);
      if (appSkills.length > 0) parts.push(`${appSkills.length} skills`);
      setContextLine(parts.length > 0 ? parts.join(" · ") : null);
    }, [agentState, taskQueue, appSkills]),
  );

  const handleSend = useCallback(async () => {
    const text = input.trim();
    if (!text || thinking) return;

    if (!llmLoaded) {
      const errMsg: Message = {
        id: `sys-${Date.now()}`,
        role: "system",
        text: "LLM is not loaded. Go to Control → Load LLM Engine first, then come back here.",
        ts: Date.now(),
      };
      setMessages((prev) => [...prev, errMsg]);
      return;
    }

    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    const userMsg: Message = {
      id: `u-${Date.now()}`,
      role: "user",
      text,
      ts: Date.now(),
    };
    setMessages((prev) => [...prev, userMsg]);
    setInput("");
    setThinking(true);

    try {
      const prompt = await buildContextPrompt(messages, text);
      const raw = await AgentCoreBridge.runInference(prompt, 512);

      // Strip the "ARIA:" prefix if the model echoes it
      const response = raw
        .replace(/^ARIA:\s*/i, "")
        .replace(/^Assistant:\s*/i, "")
        .trim();

      const ariaMsg: Message = {
        id: `a-${Date.now()}`,
        role: "aria",
        text: response || "(no response — try rephrasing)",
        ts: Date.now(),
      };
      setMessages((prev) => [...prev, ariaMsg]);
    } catch (err: any) {
      const errMsg: Message = {
        id: `err-${Date.now()}`,
        role: "system",
        text: `Inference error: ${err?.message ?? "unknown"}`,
        ts: Date.now(),
      };
      setMessages((prev) => [...prev, errMsg]);
    } finally {
      setThinking(false);
    }
  }, [input, thinking, llmLoaded, messages]);

  const handleClear = () => {
    Haptics.selectionAsync();
    setMessages([WELCOME]);
  };

  return (
    <KeyboardAvoidingView
      style={[styles.root, { backgroundColor: colors.background }]}
      behavior={Platform.OS === "ios" ? "padding" : "height"}
      keyboardVerticalOffset={Platform.OS === "ios" ? 0 : 0}
    >
      {/* Header */}
      <View
        style={[
          styles.header,
          {
            paddingTop: topPad + 12,
            backgroundColor: colors.background,
            borderBottomColor: colors.border,
            borderBottomWidth: StyleSheet.hairlineWidth,
          },
        ]}
      >
        <View style={styles.headerLeft}>
          <View
            style={[
              styles.headerIcon,
              { backgroundColor: colors.primary + "22", borderRadius: 10 },
            ]}
          >
            <Feather name="message-circle" size={16} color={colors.primary} />
          </View>
          <View>
            <Text style={[styles.headerTitle, { color: colors.foreground }]}>
              Chat with ARIA
            </Text>
            {contextLine ? (
              <Text
                style={[styles.headerSub, { color: colors.mutedForeground }]}
              >
                {contextLine}
              </Text>
            ) : (
              <Text
                style={[styles.headerSub, { color: colors.mutedForeground }]}
              >
                {llmLoaded ? "LLM ready · context injected" : "LLM not loaded"}
              </Text>
            )}
          </View>
        </View>

        <View style={styles.headerRight}>
          {/* LLM status pill */}
          <View
            style={[
              styles.statusPill,
              {
                backgroundColor: llmLoaded
                  ? colors.success + "22"
                  : colors.destructive + "18",
              },
            ]}
          >
            <View
              style={[
                styles.statusDot,
                {
                  backgroundColor: llmLoaded
                    ? colors.success
                    : colors.destructive,
                },
              ]}
            />
            <Text
              style={[
                styles.statusPillText,
                {
                  color: llmLoaded ? colors.success : colors.destructive,
                },
              ]}
            >
              {llmLoaded ? "LLM ON" : "LLM OFF"}
            </Text>
          </View>

          {/* Clear button */}
          {messages.length > 1 && (
            <TouchableOpacity
              onPress={handleClear}
              style={[
                styles.clearBtn,
                { backgroundColor: colors.surface1, borderRadius: 8 },
              ]}
              hitSlop={{ top: 6, bottom: 6, left: 6, right: 6 }}
            >
              <Feather name="trash-2" size={15} color={colors.mutedForeground} />
            </TouchableOpacity>
          )}
        </View>
      </View>

      {/* Context injected tags */}
      <View
        style={[
          styles.contextBar,
          { backgroundColor: colors.surface1, borderBottomColor: colors.border, borderBottomWidth: StyleSheet.hairlineWidth },
        ]}
      >
        <ScrollableTag label="Agent State" icon="activity" colors={colors} />
        <ScrollableTag label="Memory" icon="database" colors={colors} />
        <ScrollableTag label="Task Queue" icon="list" count={taskQueue.length} colors={colors} />
        <ScrollableTag label="App Skills" icon="layers" count={appSkills.length} colors={colors} />
      </View>

      {/* Message list */}
      <FlatList
        ref={listRef}
        data={messages}
        keyExtractor={(m) => m.id}
        renderItem={({ item }) => <Bubble msg={item} colors={colors} />}
        contentContainerStyle={[
          styles.listContent,
          { paddingBottom: 16 },
        ]}
        ListFooterComponent={
          thinking ? <TypingIndicator colors={colors} /> : null
        }
        onContentSizeChange={() =>
          listRef.current?.scrollToEnd({ animated: true })
        }
        keyboardShouldPersistTaps="handled"
        showsVerticalScrollIndicator={false}
      />

      {/* Input bar */}
      <View
        style={[
          styles.inputBar,
          {
            backgroundColor: colors.background,
            borderTopColor: colors.border,
            borderTopWidth: StyleSheet.hairlineWidth,
            paddingBottom: bottomPad + 16,
          },
        ]}
      >
        <View
          style={[
            styles.inputWrap,
            {
              backgroundColor: colors.surface1,
              borderColor: colors.border,
              borderWidth: 1,
              borderRadius: 24,
            },
          ]}
        >
          <TextInput
            value={input}
            onChangeText={setInput}
            placeholder={
              llmLoaded
                ? "Ask ARIA anything about the agent, tasks, memory…"
                : "Load the LLM first (Control tab)"
            }
            placeholderTextColor={colors.mutedForeground}
            multiline
            maxLength={800}
            editable={!thinking}
            style={[
              styles.textInput,
              { color: colors.foreground, fontFamily: "Inter_400Regular" },
            ]}
            onSubmitEditing={Platform.OS === "web" ? handleSend : undefined}
            blurOnSubmit={false}
          />
          <TouchableOpacity
            onPress={handleSend}
            disabled={!input.trim() || thinking || !llmLoaded}
            style={[
              styles.sendBtn,
              {
                backgroundColor:
                  input.trim() && !thinking && llmLoaded
                    ? colors.primary
                    : colors.muted,
                borderRadius: 20,
              },
            ]}
          >
            {thinking ? (
              <ActivityIndicator size="small" color={colors.primaryForeground} />
            ) : (
              <Feather
                name="send"
                size={16}
                color={
                  input.trim() && llmLoaded
                    ? colors.primaryForeground
                    : colors.mutedForeground
                }
              />
            )}
          </TouchableOpacity>
        </View>
      </View>
    </KeyboardAvoidingView>
  );
}

// ─── Tiny context tag pill ─────────────────────────────────────────────────────

function ScrollableTag({
  label,
  icon,
  count,
  colors,
}: {
  label: string;
  icon: React.ComponentProps<typeof Feather>["name"];
  count?: number;
  colors: any;
}) {
  return (
    <View
      style={[
        styles.tag,
        { backgroundColor: colors.surface3, borderRadius: 6 },
      ]}
    >
      <Feather name={icon} size={10} color={colors.mutedForeground} />
      <Text style={[styles.tagText, { color: colors.mutedForeground }]}>
        {label}
        {count != null && count > 0 ? ` (${count})` : ""}
      </Text>
    </View>
  );
}

// ─── Styles ───────────────────────────────────────────────────────────────────

const styles = StyleSheet.create({
  root: { flex: 1 },
  header: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: 20,
    paddingBottom: 12,
    gap: 12,
  },
  headerLeft: { flexDirection: "row", alignItems: "center", gap: 10, flex: 1 },
  headerIcon: { width: 34, height: 34, alignItems: "center", justifyContent: "center" },
  headerTitle: { fontSize: 17, fontFamily: "Inter_700Bold" },
  headerSub: { fontSize: 11, fontFamily: "Inter_400Regular", marginTop: 1 },
  headerRight: { flexDirection: "row", alignItems: "center", gap: 8 },
  statusPill: {
    flexDirection: "row",
    alignItems: "center",
    gap: 5,
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 8,
  },
  statusDot: { width: 6, height: 6, borderRadius: 3 },
  statusPillText: { fontSize: 10, fontFamily: "Inter_700Bold", letterSpacing: 0.4 },
  clearBtn: { padding: 7 },
  contextBar: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    paddingHorizontal: 16,
    paddingVertical: 8,
    flexWrap: "wrap",
  },
  tag: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    paddingHorizontal: 7,
    paddingVertical: 4,
  },
  tagText: { fontSize: 10, fontFamily: "Inter_500Medium" },
  listContent: { paddingHorizontal: 16, paddingTop: 12, gap: 10 },
  systemRow: {
    alignSelf: "center",
    maxWidth: "88%",
    marginVertical: 6,
  },
  systemText: {
    fontSize: 12,
    fontFamily: "Inter_400Regular",
    lineHeight: 18,
    textAlign: "center",
  },
  bubbleRow: {
    flexDirection: "row",
    alignItems: "flex-end",
    gap: 8,
    marginVertical: 3,
  },
  bubbleRowUser: { justifyContent: "flex-end" },
  bubbleRowAria: { justifyContent: "flex-start" },
  avatarIcon: {
    width: 30,
    height: 30,
    alignItems: "center",
    justifyContent: "center",
    flexShrink: 0,
  },
  bubble: {
    maxWidth: "78%",
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  bubbleText: {
    fontSize: 14,
    fontFamily: "Inter_400Regular",
    lineHeight: 20,
  },
  inputBar: {
    paddingHorizontal: 16,
    paddingTop: 10,
  },
  inputWrap: {
    flexDirection: "row",
    alignItems: "flex-end",
    gap: 8,
    paddingLeft: 16,
    paddingRight: 6,
    paddingVertical: 6,
  },
  textInput: {
    flex: 1,
    fontSize: 14,
    lineHeight: 20,
    maxHeight: 100,
    paddingVertical: 4,
    textAlignVertical: "center",
  },
  sendBtn: {
    width: 38,
    height: 38,
    alignItems: "center",
    justifyContent: "center",
    flexShrink: 0,
  },
});
