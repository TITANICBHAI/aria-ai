/**
 * FloatingChatBubble — Phase 17: Persistent on-screen LLM assistant bubble.
 *
 * Features:
 *  • Draggable bubble overlay visible on every screen
 *  • Three modes: Chat (LLM conversation) | Execute (tap/gesture commands) | Learn (screen observation)
 *  • Full bridge access: can start/stop/pause agent, run RL/IRL cycles, execute taps/swipes,
 *    observe the screen, read accessibility tree, switch agent modes, and more
 *  • Learn mode: transparent tap-interceptor that records & feeds user gestures to the LLM
 *  • Command parser understands natural language ("click X", "scroll down", "train RL", etc.)
 *  • Screen-observation aware: auto-injects current screen context into prompts
 */

import React, {
  useCallback,
  useEffect,
  useRef,
  useState,
} from "react";
import {
  ActivityIndicator,
  Animated,
  Dimensions,
  FlatList,
  GestureResponderEvent,
  KeyboardAvoidingView,
  Modal,
  PanResponder,
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  TouchableWithoutFeedback,
  View,
} from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { Feather } from "@expo/vector-icons";
import * as Haptics from "expo-haptics";

import { useAgent } from "@/context/AgentContext";
import { useColors } from "@/hooks/useColors";
import { AgentCoreBridge } from "@/native-bindings/AgentCoreBridge";

// ─── Types ────────────────────────────────────────────────────────────────────

type BubbleMode = "chat" | "execute" | "learn";
type Role = "user" | "aria" | "system";

interface Message {
  id: string;
  role: Role;
  text: string;
  ts: number;
}

// ─── Command parser ───────────────────────────────────────────────────────────

interface ParsedCommand {
  type:
    | "tap"
    | "swipe"
    | "type_text"
    | "scroll"
    | "start_agent"
    | "stop_agent"
    | "pause_agent"
    | "learn_mode"
    | "run_rl"
    | "run_irl"
    | "observe"
    | "load_model"
    | "enqueue_task"
    | "switch_mode"
    | "chat";
  payload?: Record<string, any>;
  rawText: string;
}

function parseCommand(text: string): ParsedCommand {
  const t = text.trim().toLowerCase();

  // Tap / click commands: "click X", "tap the button", "tap at 0.5 0.7"
  if (/^(click|tap)/.test(t)) {
    const coordMatch = t.match(/(\d+\.?\d*)\s+(\d+\.?\d*)/);
    if (coordMatch) {
      return {
        type: "tap",
        payload: { x: parseFloat(coordMatch[1]), y: parseFloat(coordMatch[2]) },
        rawText: text,
      };
    }
    const target = t.replace(/^(click|tap)\s+(on\s+)?/, "").trim();
    return { type: "tap", payload: { target }, rawText: text };
  }

  // Swipe commands: "swipe up", "swipe down", "swipe left", "swipe right"
  if (/^swipe (up|down|left|right)/.test(t)) {
    const dir = t.match(/swipe (up|down|left|right)/)?.[1] ?? "up";
    return { type: "swipe", payload: { direction: dir }, rawText: text };
  }

  // Scroll
  if (/^scroll (up|down)/.test(t)) {
    const dir = t.includes("up") ? "up" : "down";
    return { type: "scroll", payload: { direction: dir }, rawText: text };
  }

  // Type text: "type hello world"
  if (/^type /.test(t)) {
    const content = text.trim().replace(/^type\s+/i, "");
    return { type: "type_text", payload: { text: content }, rawText: text };
  }

  // Start agent: "start agent [goal]", "run [goal]", "do [goal]"
  if (/^(start agent|start|run agent|do)\s+/.test(t)) {
    const goal = text.trim().replace(/^(start agent|start|run agent|do)\s+/i, "");
    return { type: "start_agent", payload: { goal }, rawText: text };
  }

  // Stop agent
  if (/^(stop|halt|cancel|abort)(\s+agent)?$/.test(t)) {
    return { type: "stop_agent", rawText: text };
  }

  // Pause agent
  if (/^pause(\s+agent)?$/.test(t)) {
    return { type: "pause_agent", rawText: text };
  }

  // Learn mode: "learn [goal]", "watch me [goal]", "learn how to [goal]"
  if (/^(learn|watch me|learn how to|imitate)\s+/.test(t)) {
    const goal = text.trim().replace(/^(learn|watch me|learn how to|imitate)\s+/i, "");
    return { type: "learn_mode", payload: { goal }, rawText: text };
  }

  // Run RL training
  if (/^(train|run|trigger)\s+(rl|reinforce(ment\s+learning)?|policy|policy network)/.test(t)) {
    return { type: "run_rl", rawText: text };
  }

  // Run IRL (imitation learning from video)
  if (/^(run|trigger|process)\s+(irl|imitation|imitation\s+learning)/.test(t)) {
    return { type: "run_irl", rawText: text };
  }

  // Observe / see screen
  if (/^(observe|see|show|describe|what.s (on|the) screen|look at screen)/.test(t)) {
    return { type: "observe", rawText: text };
  }

  // Load model
  if (/^(load|start)\s+(llm|model|engine)/.test(t)) {
    return { type: "load_model", rawText: text };
  }

  // Enqueue task: "queue [goal]", "add task [goal]"
  if (/^(queue|add task|enqueue)\s+/.test(t)) {
    const goal = text.trim().replace(/^(queue|add task|enqueue)\s+/i, "");
    return { type: "enqueue_task", payload: { goal }, rawText: text };
  }

  // Default: chat with LLM
  return { type: "chat", rawText: text };
}

// ─── Build action JSON for executeAction bridge call ─────────────────────────

function buildActionJson(cmd: ParsedCommand): string | null {
  if (cmd.type === "tap") {
    if (cmd.payload?.x !== undefined) {
      return JSON.stringify({ tool: "tap", x: cmd.payload.x, y: cmd.payload.y });
    }
    // Named target — agent will find it by accessibility tree
    return JSON.stringify({ tool: "tap", target: cmd.payload?.target ?? "" });
  }
  if (cmd.type === "swipe") {
    const dir = cmd.payload?.direction ?? "up";
    const map: Record<string, object> = {
      up:    { tool: "swipe", startX: 0.5, startY: 0.7, endX: 0.5, endY: 0.3 },
      down:  { tool: "swipe", startX: 0.5, startY: 0.3, endX: 0.5, endY: 0.7 },
      left:  { tool: "swipe", startX: 0.8, startY: 0.5, endX: 0.2, endY: 0.5 },
      right: { tool: "swipe", startX: 0.2, startY: 0.5, endX: 0.8, endY: 0.5 },
    };
    return JSON.stringify(map[dir] ?? map.up);
  }
  if (cmd.type === "scroll") {
    const dir = cmd.payload?.direction ?? "down";
    return JSON.stringify({
      tool: "swipe",
      startX: 0.5, startY: dir === "down" ? 0.3 : 0.7,
      endX:   0.5, endY:   dir === "down" ? 0.7 : 0.3,
    });
  }
  if (cmd.type === "type_text") {
    return JSON.stringify({ tool: "type", text: cmd.payload?.text ?? "" });
  }
  return null;
}

// ─── Bubble message helpers ──────────────────────────────────────────────────

function sysMsg(text: string): Message {
  return { id: `sys-${Date.now()}-${Math.random()}`, role: "system", text, ts: Date.now() };
}
function ariaMsg(text: string): Message {
  return { id: `a-${Date.now()}-${Math.random()}`, role: "aria", text, ts: Date.now() };
}

// ─── Mode pill ───────────────────────────────────────────────────────────────

function ModePill({
  mode,
  active,
  onPress,
  colors,
}: {
  mode: BubbleMode;
  active: boolean;
  onPress: () => void;
  colors: any;
}) {
  const icons: Record<BubbleMode, React.ComponentProps<typeof Feather>["name"]> = {
    chat:    "message-circle",
    execute: "zap",
    learn:   "eye",
  };
  return (
    <TouchableOpacity
      onPress={onPress}
      style={[
        styles.modePill,
        {
          backgroundColor: active ? colors.primary : colors.surface3,
          borderColor: active ? colors.primary : colors.border,
        },
      ]}
    >
      <Feather name={icons[mode]} size={11} color={active ? colors.primaryForeground : colors.mutedForeground} />
      <Text style={[styles.modePillText, { color: active ? colors.primaryForeground : colors.mutedForeground }]}>
        {mode.charAt(0).toUpperCase() + mode.slice(1)}
      </Text>
    </TouchableOpacity>
  );
}

// ─── Chat bubble (message) ────────────────────────────────────────────────────

function ChatBubbleMsg({ msg, colors }: { msg: Message; colors: any }) {
  const isUser   = msg.role === "user";
  const isSystem = msg.role === "system";

  if (isSystem) {
    return (
      <View style={styles.sysMsgRow}>
        <Text style={[styles.sysMsgText, { color: colors.mutedForeground }]}>{msg.text}</Text>
      </View>
    );
  }

  return (
    <View style={[styles.msgRow, isUser ? styles.msgRowUser : styles.msgRowAria]}>
      {!isUser && (
        <View style={[styles.miniAvatar, { backgroundColor: colors.primary + "22" }]}>
          <Feather name="zap" size={11} color={colors.primary} />
        </View>
      )}
      <View
        style={[
          styles.msgBubble,
          {
            backgroundColor: isUser ? colors.primary : colors.surface2,
            borderColor: isUser ? "transparent" : colors.border,
            borderWidth: isUser ? 0 : StyleSheet.hairlineWidth,
          },
        ]}
      >
        <Text style={[styles.msgText, { color: isUser ? colors.primaryForeground : colors.foreground }]}>
          {msg.text}
        </Text>
      </View>
    </View>
  );
}

// ─── Main component ───────────────────────────────────────────────────────────

const BUBBLE_SIZE = 52;
const PANEL_HEIGHT = 420;
const { width: SCREEN_W, height: SCREEN_H } = Dimensions.get("window");

const WELCOME: Message = sysMsg(
  "Hi! I'm ARIA's floating assistant. I can execute commands, control the agent, run RL/IRL training, observe your screen, or just chat. Try: 'tap search bar', 'start agent open YouTube', 'learn how to order food', 'train RL', 'observe screen'."
);

export function FloatingChatBubble() {
  const colors  = useColors();
  const insets  = useSafeAreaInsets();
  const {
    agentState,
    moduleStatus,
    taskQueue,
    appSkills,
    startAgent,
    stopAgent,
    pauseAgent,
    startLearnOnly,
    loadModel,
    enqueueTask,
  } = useAgent();

  // ── Position ───────────────────────────────────────────────────────────────
  const pan = useRef(new Animated.ValueXY({ x: SCREEN_W - BUBBLE_SIZE - 16, y: SCREEN_H * 0.55 })).current;
  const panRef = useRef({ x: SCREEN_W - BUBBLE_SIZE - 16, y: SCREEN_H * 0.55 });

  // ── State ──────────────────────────────────────────────────────────────────
  const [open, setOpen]           = useState(false);
  const [mode, setMode]           = useState<BubbleMode>("chat");
  const [messages, setMessages]   = useState<Message[]>([WELCOME]);
  const [input, setInput]         = useState("");
  const [thinking, setThinking]   = useState(false);
  const [learnTaps, setLearnTaps] = useState<Array<{ x: number; y: number; ts: number }>>([]);
  const [learnGoal, setLearnGoal] = useState<string>("");
  const [isLearning, setIsLearning] = useState(false);
  const listRef = useRef<FlatList>(null);
  const pulse   = useRef(new Animated.Value(1)).current;

  const llmLoaded = moduleStatus?.llm.loaded ?? false;

  // ── Pulse animation while agent is running ─────────────────────────────────
  useEffect(() => {
    if (agentState?.status === "running" && !open) {
      const anim = Animated.loop(
        Animated.sequence([
          Animated.timing(pulse, { toValue: 1.18, duration: 700, useNativeDriver: true }),
          Animated.timing(pulse, { toValue: 1.0,  duration: 700, useNativeDriver: true }),
        ])
      );
      anim.start();
      return () => anim.stop();
    }
    pulse.setValue(1);
  }, [agentState?.status, open, pulse]);

  // ── Drag via PanResponder ──────────────────────────────────────────────────
  const isDragging = useRef(false);
  const dragStart  = useRef({ x: 0, y: 0 });

  const panResponder = useRef(
    PanResponder.create({
      onStartShouldSetPanResponder: () => !open,
      onMoveShouldSetPanResponder: (_e, gs) =>
        !open && (Math.abs(gs.dx) > 4 || Math.abs(gs.dy) > 4),
      onPanResponderGrant: (_e, gs) => {
        isDragging.current = false;
        dragStart.current  = { x: gs.x0, y: gs.y0 };
        pan.setOffset({ x: (pan.x as any)._value, y: (pan.y as any)._value });
        pan.setValue({ x: 0, y: 0 });
      },
      onPanResponderMove: (_e, gs) => {
        if (Math.abs(gs.dx) > 6 || Math.abs(gs.dy) > 6) isDragging.current = true;
        pan.setValue({ x: gs.dx, y: gs.dy });
      },
      onPanResponderRelease: (_e, gs) => {
        pan.flattenOffset();
        const newX = Math.max(8, Math.min((pan.x as any)._value, SCREEN_W - BUBBLE_SIZE - 8));
        const newY = Math.max(insets.top + 8, Math.min((pan.y as any)._value, SCREEN_H - BUBBLE_SIZE - insets.bottom - 8));
        pan.setValue({ x: newX, y: newY });
        panRef.current = { x: newX, y: newY };
        if (!isDragging.current) {
          Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
          setOpen(true);
        }
      },
    })
  ).current;

  // ── Auto-scroll on new messages ────────────────────────────────────────────
  useEffect(() => {
    setTimeout(() => listRef.current?.scrollToEnd({ animated: true }), 80);
  }, [messages.length, thinking]);

  // ── Handle learn-mode taps ─────────────────────────────────────────────────
  const handleLearnTap = useCallback(
    (e: GestureResponderEvent) => {
      if (!isLearning) return;
      const { locationX, locationY } = e.nativeEvent;
      const normX = locationX / SCREEN_W;
      const normY = locationY / SCREEN_H;
      const entry = { x: normX, y: normY, ts: Date.now() };
      setLearnTaps((prev) => [...prev, entry]);
      Haptics.selectionAsync();
      setMessages((prev) => [
        ...prev,
        sysMsg(`📍 Tap recorded at (${(normX * 100).toFixed(0)}%, ${(normY * 100).toFixed(0)}%)`),
      ]);
    },
    [isLearning]
  );

  // ── Push message helper ────────────────────────────────────────────────────
  const push = useCallback((msg: Message) => {
    setMessages((prev) => [...prev, msg]);
  }, []);

  // ── Execute a parsed command ───────────────────────────────────────────────
  const executeCommand = useCallback(
    async (cmd: ParsedCommand): Promise<string> => {
      switch (cmd.type) {
        case "tap":
        case "swipe":
        case "scroll":
        case "type_text": {
          const actionJson = buildActionJson(cmd);
          if (!actionJson) return "Could not build action.";
          const ok = await AgentCoreBridge.executeAction(actionJson);
          return ok
            ? `Done — ${cmd.type} executed on device.`
            : "Action failed — is Accessibility Service active?";
        }

        case "start_agent": {
          const goal = cmd.payload?.goal ?? "perform task";
          await startAgent(goal);
          return `Agent started with goal: "${goal}"`;
        }

        case "stop_agent": {
          await stopAgent();
          return "Agent stopped.";
        }

        case "pause_agent": {
          await pauseAgent();
          return "Agent paused.";
        }

        case "learn_mode": {
          const goal = cmd.payload?.goal ?? "learn from screen";
          setLearnGoal(goal);
          setIsLearning(true);
          setMode("learn");
          await startLearnOnly(goal);
          return `Learn mode activated for: "${goal}". I'm now watching what you do on screen. Tap the screen to record actions. Say "stop learning" to finish.`;
        }

        case "run_rl": {
          push(sysMsg("Running RL training cycle… (this may take a moment)"));
          const result = await AgentCoreBridge.runRlCycle();
          if (result.success) {
            return `RL cycle complete. Used ${result.samplesUsed} samples. LoRA v${result.loraVersion} saved.`;
          }
          return `RL cycle failed: ${result.errorMessage || "unknown error"}`;
        }

        case "run_irl": {
          return "IRL (imitation learning from video) needs a video file path. Use the Train tab to process a recorded video.";
        }

        case "observe": {
          push(sysMsg("Observing screen…"));
          const [tree, ocrInfo] = await Promise.all([
            AgentCoreBridge.getAccessibilityTree(),
            AgentCoreBridge.observeScreen(),
          ]);
          const combined = `Screen observation:\n${ocrInfo}\n\nAccessibility tree:\n${tree}`.slice(0, 1200);
          // Feed to LLM for a natural summary
          const prompt = `You are ARIA. Summarize what you see on the screen for the user in 2-3 sentences.\n\nRAW OBSERVATION:\n${combined}\n\nARIA:`;
          const summary = await AgentCoreBridge.runInference(prompt, 200);
          return summary.replace(/^ARIA:\s*/i, "").trim() || combined.slice(0, 400);
        }

        case "load_model": {
          push(sysMsg("Loading LLM model…"));
          await loadModel();
          return "Model load requested. Check the Control tab for status.";
        }

        case "enqueue_task": {
          const goal = cmd.payload?.goal ?? "";
          const task = await enqueueTask(goal);
          return task
            ? `Task queued: "${task.goal}" (ID: ${task.id.slice(0, 8)}…)`
            : "Failed to queue task.";
        }

        case "chat":
        default:
          return "__CHAT__";
      }
    },
    [startAgent, stopAgent, pauseAgent, startLearnOnly, loadModel, enqueueTask, push]
  );

  // ── Stop learning ──────────────────────────────────────────────────────────
  const stopLearning = useCallback(async () => {
    setIsLearning(false);
    setMode("chat");
    await stopAgent();
    const summary = learnTaps.length > 0
      ? `Recorded ${learnTaps.length} taps while learning "${learnGoal}".`
      : `Stopped learning "${learnGoal}". No taps were recorded.`;
    push(ariaMsg(summary));
    setLearnTaps([]);
  }, [learnTaps, learnGoal, stopAgent, push]);

  // ── Send handler ───────────────────────────────────────────────────────────
  const handleSend = useCallback(async () => {
    const text = input.trim();
    if (!text || thinking) return;

    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
    push({ id: `u-${Date.now()}`, role: "user", text, ts: Date.now() });
    setInput("");
    setThinking(true);

    try {
      // Quick check for "stop learning"
      if (/^stop (learn|learning|watching|observation)/.test(text.toLowerCase())) {
        await stopLearning();
        setThinking(false);
        return;
      }

      // Parse command
      const cmd = parseCommand(text);

      // In execute mode — always try to execute action commands
      if (mode === "execute" && cmd.type !== "chat") {
        const result = await executeCommand(cmd);
        if (result !== "__CHAT__") {
          push(ariaMsg(result));
          setThinking(false);
          return;
        }
      }

      // In learn mode toggle
      if (mode === "learn" && cmd.type !== "chat") {
        const result = await executeCommand(cmd);
        if (result !== "__CHAT__") {
          push(ariaMsg(result));
          setThinking(false);
          return;
        }
      }

      // In chat mode but it's a non-chat command
      if (cmd.type !== "chat") {
        const result = await executeCommand(cmd);
        if (result !== "__CHAT__") {
          push(ariaMsg(result));
          setThinking(false);
          return;
        }
      }

      // ── LLM chat ──────────────────────────────────────────────────────────
      if (!llmLoaded) {
        push(ariaMsg("The LLM isn't loaded yet. Say 'load model' to load it, or go to the Control tab."));
        setThinking(false);
        return;
      }

      // Build context via Kotlin bridge (1 call = agent state + memory + queue + skills)
      const histJson = JSON.stringify(
        messages
          .filter((m) => m.role !== "system")
          .slice(-6)
          .map((m) => ({ role: m.role, text: m.text }))
      );
      const systemCtx = await AgentCoreBridge.buildChatContext(text, histJson);

      // Add learn-mode tap context if applicable
      const learnCtx =
        isLearning && learnTaps.length > 0
          ? `\n[USER TAPS RECORDED IN LEARN MODE]\n${learnTaps
              .slice(-10)
              .map((t) => `  (${(t.x * 100).toFixed(0)}%, ${(t.y * 100).toFixed(0)}%)`)
              .join("\n")}`
          : "";

      // Recent history
      const recentHist = messages
        .filter((m) => m.role !== "system")
        .slice(-6);
      const histBlock =
        recentHist.length > 0
          ? "\n[CONVERSATION]\n" +
            recentHist.map((m) => `${m.role === "user" ? "User" : "ARIA"}: ${m.text}`).join("\n") +
            "\n"
          : "";

      const modeCtx =
        mode === "execute"
          ? "\n[MODE: EXECUTE — interpret commands as on-device actions]\n"
          : mode === "learn"
          ? `\n[MODE: LEARN — observing user actions. Goal: ${learnGoal}]\n`
          : "";

      const prompt = `${systemCtx}${modeCtx}${learnCtx}${histBlock}\nUser: ${text}\nARIA:`;
      const raw     = await AgentCoreBridge.runInference(prompt, 400);
      const reply   = raw.replace(/^ARIA:\s*/i, "").replace(/^Assistant:\s*/i, "").trim();
      push(ariaMsg(reply || "(no response)"));
    } catch (err: any) {
      push(sysMsg(`Error: ${err?.message ?? "unknown"}`));
    } finally {
      setThinking(false);
    }
  }, [input, thinking, mode, messages, llmLoaded, isLearning, learnTaps, learnGoal, executeCommand, stopLearning, push]);

  // ── Panel open/close ───────────────────────────────────────────────────────
  const agentRunning = agentState?.status === "running";
  const agentStatus  = agentState?.status ?? "idle";

  // Status color for bubble ring
  const ringColor =
    agentStatus === "running" ? colors.success :
    agentStatus === "paused"  ? colors.warning :
    agentStatus === "error"   ? colors.destructive :
    colors.border;

  return (
    <>
      {/* ── Transparent learn-mode overlay ──────────────────────────────── */}
      {isLearning && !open && (
        <TouchableWithoutFeedback onPress={handleLearnTap}>
          <View
            pointerEvents="box-only"
            style={[StyleSheet.absoluteFill, styles.learnOverlay]}
          >
            <View style={[styles.learnBanner, { backgroundColor: colors.primary + "dd" }]}>
              <Feather name="eye" size={13} color="#fff" />
              <Text style={styles.learnBannerText}>Learn mode — tap anywhere to record</Text>
              <TouchableOpacity onPress={stopLearning} style={styles.learnStop}>
                <Text style={styles.learnStopText}>Stop</Text>
              </TouchableOpacity>
            </View>
          </View>
        </TouchableWithoutFeedback>
      )}

      {/* ── Draggable bubble ─────────────────────────────────────────────── */}
      <Animated.View
        style={[styles.bubbleWrap, { transform: pan.getTranslateTransform() }]}
        {...panResponder.panHandlers}
      >
        <Animated.View
          style={[
            styles.bubble,
            {
              backgroundColor: colors.primary,
              borderColor: ringColor,
              borderWidth: 2.5,
              transform: [{ scale: pulse }],
            },
          ]}
        >
          <Feather name={isLearning ? "eye" : agentRunning ? "activity" : "message-circle"} size={22} color="#fff" />
        </Animated.View>
        {/* Unread dot when agent is running & panel closed */}
        {agentRunning && !open && (
          <View style={[styles.unreadDot, { backgroundColor: colors.success }]} />
        )}
      </Animated.View>

      {/* ── Chat panel (Modal) ───────────────────────────────────────────── */}
      <Modal
        visible={open}
        transparent
        animationType="slide"
        onRequestClose={() => setOpen(false)}
      >
        <TouchableWithoutFeedback onPress={() => setOpen(false)}>
          <View style={styles.modalBackdrop} />
        </TouchableWithoutFeedback>

        <KeyboardAvoidingView
          behavior={Platform.OS === "ios" ? "padding" : "height"}
          style={styles.panelKAV}
          pointerEvents="box-none"
        >
          <View
            style={[
              styles.panel,
              {
                backgroundColor: colors.background,
                borderTopColor: colors.border,
                borderTopWidth: StyleSheet.hairlineWidth,
                paddingBottom: insets.bottom + 8,
              },
            ]}
          >
            {/* Header */}
            <View style={[styles.panelHeader, { borderBottomColor: colors.border }]}>
              <View style={styles.panelTitleRow}>
                <View style={[styles.panelIcon, { backgroundColor: colors.primary + "22" }]}>
                  <Feather name="zap" size={15} color={colors.primary} />
                </View>
                <View>
                  <Text style={[styles.panelTitle, { color: colors.foreground }]}>ARIA Assistant</Text>
                  <Text style={[styles.panelSub, { color: colors.mutedForeground }]}>
                    {agentStatus !== "idle" ? `Agent: ${agentStatus}` : llmLoaded ? "LLM ready" : "LLM not loaded"}
                    {taskQueue.length > 0 ? ` · ${taskQueue.length} queued` : ""}
                    {appSkills.length > 0 ? ` · ${appSkills.length} skills` : ""}
                  </Text>
                </View>
              </View>
              <TouchableOpacity onPress={() => setOpen(false)} style={styles.closeBtn}>
                <Feather name="x" size={18} color={colors.mutedForeground} />
              </TouchableOpacity>
            </View>

            {/* Mode selector */}
            <View style={[styles.modeRow, { borderBottomColor: colors.border }]}>
              {(["chat", "execute", "learn"] as BubbleMode[]).map((m) => (
                <ModePill
                  key={m}
                  mode={m}
                  active={mode === m}
                  onPress={() => {
                    setMode(m);
                    if (m === "execute") push(sysMsg("Execute mode: tell me what to tap, type, swipe, start/stop agent, run RL, etc."));
                    if (m === "learn")   push(sysMsg("Learn mode: tell me what task to learn, then tap on screen outside this panel."));
                    if (m === "chat")    push(sysMsg("Chat mode: ask me anything about the agent, tasks, or memory."));
                  }}
                  colors={colors}
                />
              ))}
              {isLearning && (
                <TouchableOpacity
                  onPress={stopLearning}
                  style={[styles.stopLearnBtn, { backgroundColor: colors.destructive + "22", borderRadius: 6 }]}
                >
                  <Feather name="square" size={11} color={colors.destructive} />
                  <Text style={[styles.stopLearnText, { color: colors.destructive }]}>Stop Learning</Text>
                </TouchableOpacity>
              )}
            </View>

            {/* Quick action pills (execute mode) */}
            {mode === "execute" && (
              <ScrollView
                horizontal
                showsHorizontalScrollIndicator={false}
                contentContainerStyle={styles.quickRow}
              >
                {[
                  { label: "Observe Screen", icon: "eye" as const,        cmd: "observe screen" },
                  { label: "Stop Agent",     icon: "square" as const,     cmd: "stop" },
                  { label: "Pause",          icon: "pause" as const,      cmd: "pause" },
                  { label: "Train RL",       icon: "trending-up" as const, cmd: "train rl" },
                  { label: "Swipe Up",       icon: "arrow-up" as const,   cmd: "swipe up" },
                  { label: "Swipe Down",     icon: "arrow-down" as const, cmd: "swipe down" },
                  { label: "Scroll Down",    icon: "chevrons-down" as const, cmd: "scroll down" },
                ].map((q) => (
                  <TouchableOpacity
                    key={q.label}
                    onPress={() => {
                      setInput(q.cmd);
                      setTimeout(handleSend, 50);
                    }}
                    style={[styles.quickPill, { backgroundColor: colors.surface3, borderColor: colors.border }]}
                  >
                    <Feather name={q.icon} size={10} color={colors.mutedForeground} />
                    <Text style={[styles.quickPillText, { color: colors.mutedForeground }]}>{q.label}</Text>
                  </TouchableOpacity>
                ))}
              </ScrollView>
            )}

            {/* Messages */}
            <FlatList
              ref={listRef}
              data={messages}
              keyExtractor={(m) => m.id}
              renderItem={({ item }) => <ChatBubbleMsg msg={item} colors={colors} />}
              contentContainerStyle={styles.msgList}
              ListFooterComponent={
                thinking ? (
                  <View style={styles.thinkingRow}>
                    <ActivityIndicator size="small" color={colors.primary} />
                    <Text style={[styles.thinkingText, { color: colors.mutedForeground }]}>thinking…</Text>
                  </View>
                ) : null
              }
              onContentSizeChange={() => listRef.current?.scrollToEnd({ animated: true })}
              keyboardShouldPersistTaps="handled"
              showsVerticalScrollIndicator={false}
            />

            {/* Input */}
            <View
              style={[
                styles.inputRow,
                { borderTopColor: colors.border, borderTopWidth: StyleSheet.hairlineWidth },
              ]}
            >
              <View
                style={[
                  styles.inputWrap,
                  { backgroundColor: colors.surface1, borderColor: colors.border },
                ]}
              >
                <TextInput
                  value={input}
                  onChangeText={setInput}
                  placeholder={
                    mode === "execute"
                      ? "tap <target>, swipe up, train rl…"
                      : mode === "learn"
                      ? "tell me what to learn, then tap on screen…"
                      : "ask anything or give a command…"
                  }
                  placeholderTextColor={colors.mutedForeground}
                  style={[styles.textInput, { color: colors.foreground }]}
                  multiline
                  maxLength={600}
                  editable={!thinking}
                  onSubmitEditing={Platform.OS === "web" ? handleSend : undefined}
                  blurOnSubmit={false}
                />
                <TouchableOpacity
                  onPress={handleSend}
                  disabled={!input.trim() || thinking}
                  style={[
                    styles.sendBtn,
                    {
                      backgroundColor:
                        input.trim() && !thinking ? colors.primary : colors.muted,
                    },
                  ]}
                >
                  {thinking ? (
                    <ActivityIndicator size="small" color="#fff" />
                  ) : (
                    <Feather
                      name="send"
                      size={14}
                      color={input.trim() ? "#fff" : colors.mutedForeground}
                    />
                  )}
                </TouchableOpacity>
              </View>
            </View>
          </View>
        </KeyboardAvoidingView>
      </Modal>
    </>
  );
}

// ─── Styles ───────────────────────────────────────────────────────────────────

const styles = StyleSheet.create({
  // Bubble
  bubbleWrap: {
    position: "absolute",
    width: BUBBLE_SIZE,
    height: BUBBLE_SIZE,
    zIndex: 9999,
    elevation: 20,
  },
  bubble: {
    width: BUBBLE_SIZE,
    height: BUBBLE_SIZE,
    borderRadius: BUBBLE_SIZE / 2,
    alignItems: "center",
    justifyContent: "center",
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.25,
    shadowRadius: 8,
    elevation: 12,
  },
  unreadDot: {
    position: "absolute",
    top: 2,
    right: 2,
    width: 10,
    height: 10,
    borderRadius: 5,
    borderWidth: 1.5,
    borderColor: "#fff",
  },

  // Learn overlay
  learnOverlay: {
    zIndex: 9998,
    elevation: 19,
    pointerEvents: "box-only",
  },
  learnBanner: {
    position: "absolute",
    top: 0,
    left: 0,
    right: 0,
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    paddingHorizontal: 16,
    paddingVertical: 10,
    zIndex: 9998,
  },
  learnBannerText: {
    flex: 1,
    fontSize: 12,
    color: "#fff",
    fontFamily: "Inter_500Medium",
  },
  learnStop: {
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 6,
    backgroundColor: "rgba(255,255,255,0.2)",
  },
  learnStopText: { fontSize: 12, color: "#fff", fontFamily: "Inter_600SemiBold" },

  // Modal / panel
  modalBackdrop: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: "rgba(0,0,0,0.35)",
    zIndex: 9997,
  },
  panelKAV: {
    position: "absolute",
    bottom: 0,
    left: 0,
    right: 0,
    zIndex: 9998,
  },
  panel: {
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    maxHeight: PANEL_HEIGHT,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: -4 },
    shadowOpacity: 0.18,
    shadowRadius: 12,
    elevation: 20,
  },

  // Panel header
  panelHeader: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  panelTitleRow: { flexDirection: "row", alignItems: "center", gap: 10 },
  panelIcon: {
    width: 32,
    height: 32,
    borderRadius: 10,
    alignItems: "center",
    justifyContent: "center",
  },
  panelTitle: { fontSize: 15, fontFamily: "Inter_700Bold" },
  panelSub: { fontSize: 11, fontFamily: "Inter_400Regular", marginTop: 1 },
  closeBtn: { padding: 6 },

  // Mode row
  modeRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 6,
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  modePill: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 8,
    borderWidth: 1,
  },
  modePillText: { fontSize: 11, fontFamily: "Inter_600SemiBold" },
  stopLearnBtn: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    paddingHorizontal: 8,
    paddingVertical: 5,
    marginLeft: 4,
  },
  stopLearnText: { fontSize: 11, fontFamily: "Inter_600SemiBold" },

  // Quick action pills
  quickRow: {
    flexDirection: "row",
    gap: 6,
    paddingHorizontal: 14,
    paddingVertical: 8,
  },
  quickPill: {
    flexDirection: "row",
    alignItems: "center",
    gap: 4,
    paddingHorizontal: 9,
    paddingVertical: 5,
    borderRadius: 8,
    borderWidth: 1,
  },
  quickPillText: { fontSize: 10, fontFamily: "Inter_500Medium" },

  // Messages
  msgList: { paddingHorizontal: 14, paddingVertical: 10, gap: 8, flexGrow: 1 },
  sysMsgRow: { alignSelf: "center", maxWidth: "90%", marginVertical: 3 },
  sysMsgText: {
    fontSize: 11,
    fontFamily: "Inter_400Regular",
    lineHeight: 16,
    textAlign: "center",
  },
  msgRow: { flexDirection: "row", alignItems: "flex-end", gap: 6, marginVertical: 2 },
  msgRowUser: { justifyContent: "flex-end" },
  msgRowAria: { justifyContent: "flex-start" },
  miniAvatar: {
    width: 24,
    height: 24,
    borderRadius: 8,
    alignItems: "center",
    justifyContent: "center",
    flexShrink: 0,
  },
  msgBubble: {
    maxWidth: "80%",
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 14,
  },
  msgText: { fontSize: 13, fontFamily: "Inter_400Regular", lineHeight: 19 },
  thinkingRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
    paddingHorizontal: 14,
    paddingVertical: 6,
  },
  thinkingText: { fontSize: 12, fontFamily: "Inter_400Regular" },

  // Input
  inputRow: { paddingHorizontal: 12, paddingVertical: 8 },
  inputWrap: {
    flexDirection: "row",
    alignItems: "flex-end",
    borderRadius: 20,
    borderWidth: 1,
    paddingLeft: 14,
    paddingRight: 5,
    paddingVertical: 5,
    gap: 6,
  },
  textInput: {
    flex: 1,
    fontSize: 13,
    fontFamily: "Inter_400Regular",
    lineHeight: 18,
    maxHeight: 80,
    paddingVertical: 3,
    textAlignVertical: "center",
  },
  sendBtn: {
    width: 34,
    height: 34,
    borderRadius: 17,
    alignItems: "center",
    justifyContent: "center",
    flexShrink: 0,
  },
});
