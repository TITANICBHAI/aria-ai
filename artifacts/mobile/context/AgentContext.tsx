/**
 * AgentContext — Phase 10 (JS Thinning) + Phase 16 (Task Queue + App Skills).
 *
 * ARCHITECTURE CHANGE in Phase 10:
 *   Phase 9 and earlier: JS polled Kotlin every 2 seconds while running.
 *   Phase 10: JS subscribes to Kotlin push events. No polling while running.
 *
 * Events subscribed (via NativeEventEmitter → AgentCoreModule.emitEvent):
 *   agent_status_changed    → updates agentState fields directly (no bridge call)
 *   action_performed        → prepends new ActionLog entry (no bridge call)
 *   token_generated         → updates agentState.tokenRate (no bridge call)
 *   step_started            → updates currentStep/activity indicator
 *   thermal_status_changed  → updates thermalStatus directly
 *   learning_cycle_complete → triggers fetchAll (module status changes)
 *   model_download_complete → triggers fetchAll
 *   game_loop_status        → updates gameLoopStatus directly
 *   skill_updated           → updates matching appSkills entry (Phase 16)
 *   task_chain_advanced     → updates chainedTask banner + refreshes taskQueue (Phase 16)
 *
 * fetchAll() is still called on:
 *   - Initial mount (once, to hydrate all state)
 *   - Start/stop/pause commands (to confirm full sync)
 *   - Events that change module status (learning complete, model download)
 *   - Explicit user refresh
 *
 * Polling: reduced from 2000ms (running-only) to 30000ms (idle background sync).
 *   The idle sync catches any state drift (e.g., process restart, background kill).
 */

import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from "react";
import {
  AgentConfig,
  AgentCoreBridge,
  AgentCoreEmitter,
  AgentState,
  ActionLog,
  AppSkill,
  GameLoopStatus,
  MemoryEntry,
  ModuleStatus,
  QueuedTask,
} from "@/native-bindings/AgentCoreBridge";

export type { GameLoopStatus };

export interface ThermalStatus {
  level: "safe" | "light" | "moderate" | "severe" | "critical";
  inferenceSafe: boolean;
  trainingSafe: boolean;
  emergency: boolean;
}

export interface LearningEvent {
  loraVersion: number;
  policyVersion: number;
  timestamp: number;
}

export interface StepStatus {
  stepNumber: number;
  activity: "observe" | "reason" | "act" | "store" | "idle";
}

/** Phase 16: notification shown when ARIA auto-chains to a queued task */
export interface ChainedTaskNotification {
  taskId: string;
  goal: string;
  appPackage: string;
  queueSize: number;
  timestamp: number;
}

interface AgentContextValue {
  agentState: AgentState | null;
  moduleStatus: ModuleStatus | null;
  actionLogs: ActionLog[];
  memoryEntries: MemoryEntry[];
  config: AgentConfig | null;
  isLoading: boolean;
  error: string | null;
  thermalStatus: ThermalStatus | null;
  lastLearningEvent: LearningEvent | null;
  gameLoopStatus: GameLoopStatus | null;
  currentStep: StepStatus | null;
  taskQueue: QueuedTask[];                          // Phase 16
  appSkills: AppSkill[];                            // Phase 16
  chainedTask: ChainedTaskNotification | null;      // Phase 16: most recent chain event

  startAgent: (goal: string) => Promise<void>;
  stopAgent: () => Promise<void>;
  pauseAgent: () => Promise<void>;
  clearMemory: () => Promise<void>;
  updateConfig: (patch: Partial<AgentConfig>) => Promise<void>;
  loadModel: () => Promise<void>;
  requestPermissions: () => Promise<void>;
  refresh: () => Promise<void>;

  // Phase 16: task queue management
  enqueueTask: (goal: string, appPackage?: string, priority?: number) => Promise<QueuedTask | null>;
  removeQueuedTask: (taskId: string) => Promise<void>;
  clearTaskQueue: () => Promise<void>;
  refreshTaskQueue: () => Promise<void>;

  // Phase 16: app skills
  refreshAppSkills: () => Promise<void>;
  clearAppSkills: () => Promise<void>;
  dismissChainNotification: () => void;
}

const AgentContext = createContext<AgentContextValue | null>(null);

/** Map a tool name from action_performed event to ActionLog.type */
function toolToLogType(tool: string): ActionLog["type"] {
  const t = tool.toLowerCase();
  if (t === "click" || t === "tap") return "tap";
  if (t === "swipe" || t === "scroll") return "swipe";
  if (t === "type")   return "text";
  if (t === "back")   return "intent";
  if (t === "wait")   return "observe";
  return "intent";
}

export function AgentProvider({ children }: { children: React.ReactNode }) {
  const [agentState, setAgentState] = useState<AgentState | null>(null);
  const [moduleStatus, setModuleStatus] = useState<ModuleStatus | null>(null);
  const [actionLogs, setActionLogs] = useState<ActionLog[]>([]);
  const [memoryEntries, setMemoryEntries] = useState<MemoryEntry[]>([]);
  const [config, setConfig] = useState<AgentConfig | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [thermalStatus, setThermalStatus] = useState<ThermalStatus | null>(null);
  const [lastLearningEvent, setLastLearningEvent] = useState<LearningEvent | null>(null);
  const [gameLoopStatus, setGameLoopStatus] = useState<GameLoopStatus | null>(null);
  const [currentStep, setCurrentStep] = useState<StepStatus | null>(null);

  // Phase 16
  const [taskQueue, setTaskQueue] = useState<QueuedTask[]>([]);
  const [appSkills, setAppSkills] = useState<AppSkill[]>([]);
  const [chainedTask, setChainedTask] = useState<ChainedTaskNotification | null>(null);

  // Idle background sync — catches state drift after process restarts or suspend/resume.
  // Phase 10: only needed while idle (events handle all running-state updates).
  const idlePollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // ── Phase 16 helpers ──────────────────────────────────────────────────────

  const refreshTaskQueue = useCallback(async () => {
    try {
      const q = await AgentCoreBridge.getTaskQueue();
      setTaskQueue(q);
    } catch { /* bridge unavailable in web */ }
  }, []);

  const refreshAppSkills = useCallback(async () => {
    try {
      const skills = await AgentCoreBridge.getAllAppSkills();
      setAppSkills(skills);
    } catch { /* bridge unavailable in web */ }
  }, []);

  // ── Full state hydration (used on mount, start/stop, learning complete) ──────
  const fetchAll = useCallback(async () => {
    try {
      const [state, status, logs, memories, cfg, gameSt] = await Promise.all([
        AgentCoreBridge.getAgentState(),
        AgentCoreBridge.getModuleStatus(),
        AgentCoreBridge.getActionLogs(50),
        AgentCoreBridge.getMemoryEntries(30),
        AgentCoreBridge.getConfig(),
        AgentCoreBridge.getGameLoopStatus(),
      ]);
      setAgentState(state);
      setModuleStatus(status);
      setActionLogs(logs);
      setMemoryEntries(memories);
      setConfig(cfg);
      setGameLoopStatus(gameSt);
      setError(null);
    } catch (e: any) {
      setError(e?.message ?? "Bridge error");
    } finally {
      setIsLoading(false);
    }
    // Phase 16: always sync queue + skills on full refresh
    await Promise.all([refreshTaskQueue(), refreshAppSkills()]);
  }, [refreshTaskQueue, refreshAppSkills]);

  // ── Initial mount — hydrate once ──────────────────────────────────────────
  useEffect(() => {
    fetchAll();
    // Idle background sync every 30 seconds — much less aggressive than the old 2s poll.
    // This handles edge cases: crash-restart, background kill, manual Kotlin-side changes.
    idlePollRef.current = setInterval(fetchAll, 30_000);
    return () => {
      if (idlePollRef.current) clearInterval(idlePollRef.current);
    };
  }, [fetchAll]);

  // ── Phase 10 + 16: Native event subscriptions ─────────────────────────────
  useEffect(() => {
    if (!AgentCoreEmitter) return;

    // agent_status_changed → update agentState directly, no bridge call needed
    const onStatus = AgentCoreEmitter.addListener(
      "agent_status_changed",
      (payload: {
        status: string; currentTask: string; currentApp: string;
        stepCount: number; lastAction: string; lastError: string; gameMode: string;
      }) => {
        setAgentState((prev) => ({
          ...(prev ?? {} as AgentState),
          status: (payload.status ?? "idle") as AgentState["status"],
          currentTask: payload.currentTask ?? null,
          currentApp:  payload.currentApp  ?? null,
          gameMode:    (payload.gameMode ?? "none") as AgentState["gameMode"],
          actionsPerformed: payload.stepCount ?? prev?.actionsPerformed ?? 0,
        }));
      }
    );

    // action_performed → prepend new log entry; no getActionLogs() call needed
    const onAction = AgentCoreEmitter.addListener(
      "action_performed",
      (payload: {
        tool: string; nodeId: string; success: boolean;
        reward: number; stepCount: number; appPackage: string; timestamp: number;
      }) => {
        const logEntry: ActionLog = {
          id:            `evt_${payload.timestamp ?? Date.now()}`,
          timestamp:     payload.timestamp ?? Date.now(),
          type:          toolToLogType(payload.tool ?? ""),
          description:   `${payload.tool ?? "Action"} on ${payload.nodeId || "screen"}`,
          app:           payload.appPackage ?? "",
          success:       payload.success ?? false,
          rewardSignal:  payload.reward ?? 0,
        };
        setActionLogs((prev) => [logEntry, ...prev].slice(0, 100));
        // Also update step indicator to "store" phase momentarily
        setCurrentStep((prev) => prev ? { ...prev, activity: "store" } : null);
      }
    );

    // token_generated → update token rate in agentState (live streaming)
    const onToken = AgentCoreEmitter.addListener(
      "token_generated",
      (payload: { token: string; tokensPerSecond: number }) => {
        setAgentState((prev) =>
          prev ? { ...prev, tokenRate: payload.tokensPerSecond ?? prev.tokenRate } : prev
        );
        // Step indicator transitions to "reason" phase when tokens arrive
        setCurrentStep((prev) => prev ? { ...prev, activity: "reason" } : null);
      }
    );

    // step_started → update live step indicator (no fetch, pure display)
    const onStep = AgentCoreEmitter.addListener(
      "step_started",
      (payload: { stepNumber: number; activity: string }) => {
        setCurrentStep({
          stepNumber: payload.stepNumber ?? 0,
          activity:   (payload.activity ?? "observe") as StepStatus["activity"],
        });
      }
    );

    // learning_cycle_complete → fetchAll to refresh module status (LoRA version changes)
    const onLearning = AgentCoreEmitter.addListener(
      "learning_cycle_complete",
      (payload: { loraVersion: number; policyVersion: number; timestamp: number }) => {
        setLastLearningEvent({
          loraVersion:   payload.loraVersion  ?? 0,
          policyVersion: payload.policyVersion ?? 0,
          timestamp:     payload.timestamp     ?? Date.now(),
        });
        fetchAll(); // Module status shows new adapter version
      }
    );

    // thermal_status_changed → update directly (no fetch needed)
    const onThermal = AgentCoreEmitter.addListener(
      "thermal_status_changed",
      (payload: { level: string; inferenceSafe: boolean; trainingSafe: boolean; emergency: boolean }) => {
        setThermalStatus({
          level:         (payload.level ?? "safe") as ThermalStatus["level"],
          inferenceSafe:  payload.inferenceSafe ?? true,
          trainingSafe:   payload.trainingSafe  ?? true,
          emergency:      payload.emergency     ?? false,
        });
      }
    );

    // model_download_complete → fetchAll so model readiness refreshes
    const onModelDownload = AgentCoreEmitter.addListener(
      "model_download_complete",
      () => { fetchAll(); }
    );

    // game_loop_status → update directly (no fetch)
    const onGameLoop = AgentCoreEmitter.addListener(
      "game_loop_status",
      (payload: {
        isActive: boolean; gameType: string; episodeCount: number; stepCount: number;
        currentScore: number; highScore: number; totalReward: number; lastAction: string; isGameOver: boolean;
      }) => {
        setGameLoopStatus({
          isActive:      payload.isActive      ?? false,
          gameType:      (payload.gameType ?? "none") as GameLoopStatus["gameType"],
          episodeCount:  payload.episodeCount  ?? 0,
          stepCount:     payload.stepCount     ?? 0,
          currentScore:  payload.currentScore  ?? 0,
          highScore:     payload.highScore     ?? 0,
          totalReward:   payload.totalReward   ?? 0,
          lastAction:    payload.lastAction    ?? "",
          isGameOver:    payload.isGameOver    ?? false,
        });
      }
    );

    // ── Phase 16 events ─────────────────────────────────────────────────────

    // skill_updated → merge updated skill record into appSkills list
    const onSkillUpdated = AgentCoreEmitter.addListener(
      "skill_updated",
      (payload: {
        appPackage: string; taskSuccess: number; taskFailure: number; successRate: number;
      }) => {
        setAppSkills((prev) =>
          prev.map((s) =>
            s.appPackage === payload.appPackage
              ? { ...s, taskSuccess: payload.taskSuccess, taskFailure: payload.taskFailure, successRate: payload.successRate }
              : s
          )
        );
        // Also full-refresh skills so all fields (avgSteps, hint, etc.) are in sync
        refreshAppSkills();
      }
    );

    // task_chain_advanced → show notification banner + refresh task queue
    const onChainAdvanced = AgentCoreEmitter.addListener(
      "task_chain_advanced",
      (payload: { taskId: string; goal: string; appPackage: string; queueSize: number }) => {
        setChainedTask({
          taskId:     payload.taskId     ?? "",
          goal:       payload.goal       ?? "",
          appPackage: payload.appPackage ?? "",
          queueSize:  payload.queueSize  ?? 0,
          timestamp:  Date.now(),
        });
        // Queue shrunk by one — refresh so UI removes the item
        refreshTaskQueue();
      }
    );

    return () => {
      onStatus.remove();
      onAction.remove();
      onToken.remove();
      onStep.remove();
      onLearning.remove();
      onThermal.remove();
      onModelDownload.remove();
      onGameLoop.remove();
      onSkillUpdated.remove();
      onChainAdvanced.remove();
    };
  }, [fetchAll, refreshTaskQueue, refreshAppSkills]);

  // ── Agent control ─────────────────────────────────────────────────────────

  const startAgent = useCallback(async (goal: string) => {
    const res = await AgentCoreBridge.startAgent(goal);
    if (res.success) {
      await fetchAll();
    } else {
      setError(res.error ?? "Failed to start agent");
    }
  }, [fetchAll]);

  const stopAgent = useCallback(async () => {
    await AgentCoreBridge.stopAgent();
    setCurrentStep(null);
    await fetchAll();
  }, [fetchAll]);

  const pauseAgent = useCallback(async () => {
    await AgentCoreBridge.pauseAgent();
    setCurrentStep(null);
    await fetchAll();
  }, [fetchAll]);

  const clearMemory = useCallback(async () => {
    await AgentCoreBridge.clearMemory();
    setMemoryEntries([]);
  }, []);

  const updateConfig = useCallback(async (patch: Partial<AgentConfig>) => {
    await AgentCoreBridge.updateConfig(patch);
    const cfg = await AgentCoreBridge.getConfig();
    setConfig(cfg);
  }, []);

  const loadModel = useCallback(async () => {
    setIsLoading(true);
    const ok = await AgentCoreBridge.loadModel();
    if (!ok) setError("Model load failed");
    await fetchAll();
  }, [fetchAll]);

  const requestPermissions = useCallback(async () => {
    await Promise.all([
      AgentCoreBridge.requestAccessibilityPermission(),
      AgentCoreBridge.requestScreenCapturePermission(),
    ]);
    await fetchAll();
  }, [fetchAll]);

  // ── Phase 16: task queue methods ──────────────────────────────────────────

  const enqueueTask = useCallback(async (
    goal: string,
    appPackage = "",
    priority = 0,
  ): Promise<QueuedTask | null> => {
    const task = await AgentCoreBridge.enqueueTask(goal, appPackage, priority);
    await refreshTaskQueue();
    return task;
  }, [refreshTaskQueue]);

  const removeQueuedTask = useCallback(async (taskId: string) => {
    await AgentCoreBridge.removeQueuedTask(taskId);
    setTaskQueue((prev) => prev.filter((t) => t.id !== taskId));
  }, []);

  const clearTaskQueue = useCallback(async () => {
    await AgentCoreBridge.clearTaskQueue();
    setTaskQueue([]);
  }, []);

  // ── Phase 16: app skills methods ──────────────────────────────────────────

  const clearAppSkills = useCallback(async () => {
    await AgentCoreBridge.clearAppSkills();
    setAppSkills([]);
  }, []);

  const dismissChainNotification = useCallback(() => {
    setChainedTask(null);
  }, []);

  return (
    <AgentContext.Provider
      value={{
        agentState,
        moduleStatus,
        actionLogs,
        memoryEntries,
        config,
        isLoading,
        error,
        thermalStatus,
        lastLearningEvent,
        gameLoopStatus,
        currentStep,
        taskQueue,
        appSkills,
        chainedTask,
        startAgent,
        stopAgent,
        pauseAgent,
        clearMemory,
        updateConfig,
        loadModel,
        requestPermissions,
        refresh: fetchAll,
        enqueueTask,
        removeQueuedTask,
        clearTaskQueue,
        refreshTaskQueue,
        refreshAppSkills,
        clearAppSkills,
        dismissChainNotification,
      }}
    >
      {children}
    </AgentContext.Provider>
  );
}

export function useAgent(): AgentContextValue {
  const ctx = useContext(AgentContext);
  if (!ctx) throw new Error("useAgent must be used within AgentProvider");
  return ctx;
}
