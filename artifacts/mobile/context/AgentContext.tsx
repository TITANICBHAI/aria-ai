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
  MemoryEntry,
  ModuleStatus,
} from "@/native-bindings/AgentCoreBridge";

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

  startAgent: (goal: string) => Promise<void>;
  stopAgent: () => Promise<void>;
  pauseAgent: () => Promise<void>;
  clearMemory: () => Promise<void>;
  updateConfig: (patch: Partial<AgentConfig>) => Promise<void>;
  loadModel: () => Promise<void>;
  requestPermissions: () => Promise<void>;
  refresh: () => Promise<void>;
}

const AgentContext = createContext<AgentContextValue | null>(null);

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
  const pollingRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const fetchAll = useCallback(async () => {
    try {
      const [state, status, logs, memories, cfg] = await Promise.all([
        AgentCoreBridge.getAgentState(),
        AgentCoreBridge.getModuleStatus(),
        AgentCoreBridge.getActionLogs(50),
        AgentCoreBridge.getMemoryEntries(30),
        AgentCoreBridge.getConfig(),
      ]);
      setAgentState(state);
      setModuleStatus(status);
      setActionLogs(logs);
      setMemoryEntries(memories);
      setConfig(cfg);
      setError(null);
    } catch (e: any) {
      setError(e?.message ?? "Bridge error");
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchAll();
    pollingRef.current = setInterval(() => {
      if (agentState?.status === "running") {
        fetchAll();
      }
    }, 2000);
    return () => {
      if (pollingRef.current) clearInterval(pollingRef.current);
    };
  }, [fetchAll, agentState?.status]);

  // Native event subscriptions — react immediately without waiting for the poll cycle
  useEffect(() => {
    if (!AgentCoreEmitter) return;

    const onLearningComplete = AgentCoreEmitter.addListener(
      "learning_cycle_complete",
      (payload: { loraVersion: number; policyVersion: number; timestamp: number }) => {
        setLastLearningEvent({
          loraVersion: payload.loraVersion ?? 0,
          policyVersion: payload.policyVersion ?? 0,
          timestamp: payload.timestamp ?? Date.now(),
        });
        // Refresh everything so moduleStatus shows the new adapter version
        fetchAll();
      }
    );

    const onThermal = AgentCoreEmitter.addListener(
      "thermal_status_changed",
      (payload: {
        level: string;
        inferenceSafe: boolean;
        trainingSafe: boolean;
        emergency: boolean;
      }) => {
        setThermalStatus({
          level: (payload.level ?? "safe") as ThermalStatus["level"],
          inferenceSafe: payload.inferenceSafe ?? true,
          trainingSafe: payload.trainingSafe ?? true,
          emergency: payload.emergency ?? false,
        });
      }
    );

    const onModelDownload = AgentCoreEmitter.addListener(
      "model_download_complete",
      () => {
        // Model finished downloading — refresh so the UI shows it loaded
        fetchAll();
      }
    );

    return () => {
      onLearningComplete.remove();
      onThermal.remove();
      onModelDownload.remove();
    };
  }, [fetchAll]);

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
    await fetchAll();
  }, [fetchAll]);

  const pauseAgent = useCallback(async () => {
    await AgentCoreBridge.pauseAgent();
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
    const res = await AgentCoreBridge.loadModel();
    if (!res.success) setError(res.error ?? "Model load failed");
    await fetchAll();
  }, [fetchAll]);

  const requestPermissions = useCallback(async () => {
    await Promise.all([
      AgentCoreBridge.requestAccessibilityPermission(),
      AgentCoreBridge.requestScreenCapturePermission(),
    ]);
    await fetchAll();
  }, [fetchAll]);

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
        startAgent,
        stopAgent,
        pauseAgent,
        clearMemory,
        updateConfig,
        loadModel,
        requestPermissions,
        refresh: fetchAll,
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
