/**
 * TurboModule bridge stub — Phase 1 (JS UI Shell)
 *
 * This file defines the contract between the JS UI and the Kotlin brain.
 * In Phase 1, all methods return mock data so the UI can be developed and tested.
 * In Phase 2, replace each stub with the actual TurboModule call:
 *
 *   import { NativeModules } from 'react-native';
 *   const { AgentCore } = NativeModules;
 *
 * TurboModule registration in Kotlin:
 *   android/bridge/turbo/AgentCoreModule.kt
 *   → implements TurboModule, exposes @ReactMethod annotated functions
 *   → Registered in ReactPackage → ReactNativeHost
 */

export type AgentStatus = "idle" | "running" | "paused" | "error";

export interface AgentState {
  status: AgentStatus;
  currentTask: string | null;
  currentApp: string | null;
  tokenRate: number;
  memoryUsedMb: number;
  sessionStartedAt: number | null;
  actionsPerformed: number;
  successRate: number;
}

export interface LLMStatus {
  loaded: boolean;
  modelName: string;
  quantization: string;
  contextLength: number;
  tokensPerSecond: number;
  memoryMb: number;
}

export interface ModuleStatus {
  llm: LLMStatus;
  ocr: { ready: boolean; engine: string };
  rl: { ready: boolean; episodesRun: number };
  memory: { ready: boolean; embeddingCount: number; dbSizeKb: number };
  accessibility: { granted: boolean; active: boolean };
  screenCapture: { granted: boolean; active: boolean };
}

export interface ActionLog {
  id: string;
  timestamp: number;
  type: "tap" | "swipe" | "text" | "scroll" | "intent" | "observe";
  description: string;
  app: string;
  success: boolean;
  rewardSignal?: number;
}

export interface MemoryEntry {
  id: string;
  createdAt: number;
  summary: string;
  app: string;
  confidence: number;
  usageCount: number;
}

export interface AgentConfig {
  modelPath: string;
  quantization: string;
  contextWindow: number;
  maxTokensPerTurn: number;
  temperatureX100: number;
  loraAdapterPath: string | null;
  rlEnabled: boolean;
  learningRate: number;
}

// ─── Stub implementations ────────────────────────────────────────────────────
// Replace with: NativeModules.AgentCore.<methodName>(args)

export const AgentCoreBridge = {
  async getAgentState(): Promise<AgentState> {
    await delay(120);
    return {
      status: "idle",
      currentTask: null,
      currentApp: null,
      tokenRate: 0,
      memoryUsedMb: 0,
      sessionStartedAt: null,
      actionsPerformed: 0,
      successRate: 0,
    };
  },

  async startAgent(goal: string): Promise<{ success: boolean; error?: string }> {
    await delay(300);
    return { success: true };
  },

  async stopAgent(): Promise<{ success: boolean }> {
    await delay(200);
    return { success: true };
  },

  async pauseAgent(): Promise<{ success: boolean }> {
    await delay(100);
    return { success: true };
  },

  async getModuleStatus(): Promise<ModuleStatus> {
    await delay(150);
    return {
      llm: {
        loaded: false,
        modelName: "Llama-3.2-1B-Instruct",
        quantization: "Q4_K_M",
        contextLength: 4096,
        tokensPerSecond: 0,
        memoryMb: 0,
      },
      ocr: { ready: false, engine: "ML Kit" },
      rl: { ready: false, episodesRun: 0 },
      memory: { ready: false, embeddingCount: 0, dbSizeKb: 0 },
      accessibility: { granted: false, active: false },
      screenCapture: { granted: false, active: false },
    };
  },

  async getActionLogs(limit: number = 50): Promise<ActionLog[]> {
    await delay(100);
    return [];
  },

  async getMemoryEntries(limit: number = 30): Promise<MemoryEntry[]> {
    await delay(100);
    return [];
  },

  async clearMemory(): Promise<{ success: boolean }> {
    await delay(200);
    return { success: true };
  },

  async getConfig(): Promise<AgentConfig> {
    await delay(80);
    return {
      modelPath: "/data/user/0/com.mobile/files/models/llama-3.2-1b-q4_k_m.gguf",
      quantization: "Q4_K_M",
      contextWindow: 4096,
      maxTokensPerTurn: 512,
      temperatureX100: 70,
      loraAdapterPath: null,
      rlEnabled: false,
      learningRate: 1,
    };
  },

  async updateConfig(config: Partial<AgentConfig>): Promise<{ success: boolean }> {
    await delay(200);
    return { success: true };
  },

  async loadModel(): Promise<{ success: boolean; error?: string }> {
    await delay(500);
    return { success: true };
  },

  async requestAccessibilityPermission(): Promise<{ granted: boolean }> {
    await delay(100);
    return { granted: false };
  },

  async requestScreenCapturePermission(): Promise<{ granted: boolean }> {
    await delay(100);
    return { granted: false };
  },
};

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
