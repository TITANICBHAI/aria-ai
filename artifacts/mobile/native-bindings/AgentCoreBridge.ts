/**
 * AgentCoreBridge — the ONLY permitted channel between JS and the Kotlin brain.
 *
 * On Android: calls NativeModules.AgentCore (the real Kotlin TurboModule)
 * On Web/iOS: returns stub data so the UI can be developed and previewed
 *
 * JS never calls AccessibilityService, MediaProjection, or GestureEngine directly.
 * JS never owns logic. JS only displays what Kotlin tells it.
 *
 * Kotlin registration:
 *   android/app/src/main/kotlin/com/ariaagent/mobile/bridge/AgentCoreModule.kt
 *
 * Events FROM Kotlin (listen with NativeEventEmitter):
 *   model_download_progress  { percent, downloadedMb, totalMb, speedMbps }
 *   model_download_complete  { path }
 *   model_download_error     { error }
 *   token_generated          { token }
 *   agent_status_changed     { status, currentTask, currentApp }
 *   learning_cycle_complete  { loraVersion, policyVersion }
 */

import { NativeEventEmitter, NativeModules, Platform } from "react-native";

const isAndroid = Platform.OS === "android";
const AgentCore = isAndroid ? NativeModules.AgentCore : null;

export const AgentCoreEmitter =
  isAndroid && AgentCore ? new NativeEventEmitter(AgentCore) : null;

// ─── Types ────────────────────────────────────────────────────────────────────

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
  modelReady: boolean;
  llmLoaded: boolean;
  accessibilityActive: boolean;
  screenCaptureActive: boolean;
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

export interface ModelInfo {
  modelName: string;
  quantization: string;
  contextLength: number;
  downloadUrl: string;
  diskSizeMb: number;
  ramSizeMb: number;
  isReady: boolean;
  path: string;
}

export interface DownloadProgress {
  percent: number;
  downloadedMb: number;
  totalMb: number;
  speedMbps: number;
}

export interface ExperienceStats {
  totalTuples: number;
  successCount: number;
  failureCount: number;
  edgeCaseCount: number;
}

// ─── Bridge ───────────────────────────────────────────────────────────────────

export const AgentCoreBridge = {

  async checkModelReady(): Promise<boolean> {
    if (AgentCore) return AgentCore.checkModelReady();
    return false;
  },

  async getModelInfo(): Promise<ModelInfo> {
    if (AgentCore) return AgentCore.getModelInfo();
    return {
      modelName: "Llama-3.2-1B-Instruct",
      quantization: "Q4_K_M",
      contextLength: 4096,
      downloadUrl: "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF",
      diskSizeMb: 870,
      ramSizeMb: 1700,
      isReady: false,
      path: "/data/user/0/com.ariaagent.mobile/files/models/llama-3.2-1b-q4_k_m.gguf",
    };
  },

  async startModelDownload(): Promise<boolean> {
    if (AgentCore) return AgentCore.startModelDownload();
    return false;
  },

  async cancelModelDownload(): Promise<boolean> {
    if (AgentCore) return AgentCore.cancelModelDownload();
    return false;
  },

  async loadModel(): Promise<boolean> {
    if (AgentCore) return AgentCore.loadModel();
    return false;
  },

  async runInference(prompt: string, maxTokens: number = 512): Promise<string> {
    if (AgentCore) return AgentCore.runInference(prompt, maxTokens);
    return '{"tool":"stub","reason":"web preview mode"}';
  },

  async getLlmStatus(): Promise<LLMStatus> {
    if (AgentCore) return AgentCore.getLlmStatus();
    return { loaded: false, modelName: "Llama-3.2-1B-Instruct", quantization: "Q4_K_M", contextLength: 4096, tokensPerSecond: 0, memoryMb: 0 };
  },

  async observeScreen(): Promise<string> {
    if (AgentCore) return AgentCore.observeScreen();
    return "(web preview — no screen observation)";
  },

  async runOcr(imagePath: string): Promise<string> {
    if (AgentCore) return AgentCore.runOcr(imagePath);
    return "";
  },

  async getAccessibilityTree(): Promise<string> {
    if (AgentCore) return AgentCore.getAccessibilityTree();
    return "(web preview — no accessibility service)";
  },

  async executeAction(actionJson: string): Promise<boolean> {
    if (AgentCore) return AgentCore.executeAction(actionJson);
    return false;
  },

  async getAgentState(): Promise<AgentState> {
    if (AgentCore) {
      const raw = await AgentCore.getAgentStatus();
      return {
        status: raw.status ?? "idle",
        currentTask: raw.currentTask ?? null,
        currentApp: raw.currentApp ?? null,
        tokenRate: raw.tokensPerSecond ?? 0,
        memoryUsedMb: raw.memoryMb ?? 0,
        sessionStartedAt: null,
        actionsPerformed: 0,
        successRate: 0,
        modelReady: raw.modelReady ?? false,
        llmLoaded: raw.llmLoaded ?? false,
        accessibilityActive: raw.accessibilityActive ?? false,
        screenCaptureActive: raw.screenCaptureActive ?? false,
      };
    }
    return { status: "idle", currentTask: null, currentApp: null, tokenRate: 0, memoryUsedMb: 0, sessionStartedAt: null, actionsPerformed: 0, successRate: 0, modelReady: false, llmLoaded: false, accessibilityActive: false, screenCaptureActive: false };
  },

  async startAgent(_goal: string): Promise<{ success: boolean; error?: string }> {
    return { success: false, error: "Agent loop not yet implemented (Phase 3)" };
  },

  async stopAgent(): Promise<{ success: boolean }> { return { success: true }; },
  async pauseAgent(): Promise<{ success: boolean }> { return { success: true }; },

  async getModuleStatus(): Promise<ModuleStatus> {
    const llm = await AgentCoreBridge.getLlmStatus().catch(() => null);
    return {
      llm: llm ?? { loaded: false, modelName: "Llama-3.2-1B-Instruct", quantization: "Q4_K_M", contextLength: 4096, tokensPerSecond: 0, memoryMb: 0 },
      ocr: { ready: false, engine: "ML Kit Text Recognition" },
      rl: { ready: false, episodesRun: 0 },
      memory: { ready: false, embeddingCount: 0, dbSizeKb: 0 },
      accessibility: { granted: false, active: false },
      screenCapture: { granted: false, active: false },
    };
  },

  async getExperienceStats(): Promise<ExperienceStats> {
    if (AgentCore) return AgentCore.getExperienceStats();
    return { totalTuples: 0, successCount: 0, failureCount: 0, edgeCaseCount: 0 };
  },

  async getActionLogs(_limit: number = 50): Promise<ActionLog[]> {
    if (AgentCore) {
      const raw = await AgentCore.getRecentExperiences(_limit).catch(() => []);
      return raw.map((e: any) => ({
        id: e.id, timestamp: e.timestamp,
        type: e.taskType ?? "observe",
        description: e.actionJson ?? "",
        app: e.appPackage ?? "unknown",
        success: e.result === "success",
        rewardSignal: e.reward,
      }));
    }
    return [];
  },

  async getMemoryEntries(_limit: number = 30): Promise<MemoryEntry[]> { return []; },

  async clearMemory(): Promise<{ success: boolean }> {
    if (AgentCore) await AgentCore.clearMemory();
    return { success: true };
  },

  async getConfig(): Promise<AgentConfig> {
    return { modelPath: "/data/user/0/com.ariaagent.mobile/files/models/llama-3.2-1b-q4_k_m.gguf", quantization: "Q4_K_M", contextWindow: 4096, maxTokensPerTurn: 512, temperatureX100: 70, loraAdapterPath: null, rlEnabled: false, learningRate: 1 };
  },

  async updateConfig(_config: Partial<AgentConfig>): Promise<{ success: boolean }> { return { success: true }; },

  async isAccessibilityEnabled(): Promise<boolean> {
    if (AgentCore) return AgentCore.isAccessibilityEnabled();
    return false;
  },

  async openAccessibilitySettings(): Promise<boolean> {
    if (AgentCore) return AgentCore.openAccessibilitySettings();
    return false;
  },

  async requestAccessibilityPermission(): Promise<{ granted: boolean }> {
    if (AgentCore) { await AgentCore.openAccessibilitySettings(); return { granted: false }; }
    return { granted: false };
  },

  async requestScreenCapturePermission(): Promise<{ granted: boolean }> {
    return { granted: false };
  },
};
