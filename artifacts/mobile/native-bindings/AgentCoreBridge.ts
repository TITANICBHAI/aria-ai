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
 *   thermal_status_changed   { level, inferenceSafe, trainingSafe, emergency }
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
  rl: {
    ready: boolean;
    episodesRun: number;
    loraVersion: number;
    adapterLoaded: boolean;
    untrainedSamples: number;
  };
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
  nGpuLayers: number;        // GPU offload layers — 0=CPU only, 32=full GPU (Exynos Mali-G72)
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

  async startAgent(goal: string, appPackage: string = ""): Promise<{ success: boolean; error?: string }> {
    if (AgentCore) {
      try {
        await AgentCore.startAgent(goal, appPackage);
        return { success: true };
      } catch (e: any) {
        return { success: false, error: e.message };
      }
    }
    return { success: false, error: "web preview — agent loop requires Android device" };
  },

  async stopAgent(): Promise<{ success: boolean }> {
    if (AgentCore) await AgentCore.stopAgent();
    return { success: true };
  },

  async pauseAgent(): Promise<{ success: boolean }> {
    if (AgentCore) await AgentCore.pauseAgent();
    return { success: true };
  },

  async getAgentLoopStatus(): Promise<{
    status: string; goal: string; appPackage: string;
    stepCount: number; lastAction: string; lastError: string;
  }> {
    if (AgentCore) return AgentCore.getAgentLoopStatus();
    return { status: "idle", goal: "", appPackage: "", stepCount: 0, lastAction: "", lastError: "" };
  },

  async runRlCycle(): Promise<{
    success: boolean; samplesUsed: number; adapterPath: string;
    loraVersion: number; errorMessage: string;
  }> {
    if (AgentCore) return AgentCore.runRlCycle();
    return { success: false, samplesUsed: 0, adapterPath: "", loraVersion: 0, errorMessage: "web preview" };
  },

  async processIrlVideo(videoPath: string, taskGoal: string, appPackage: string): Promise<{
    videoPath: string; framesProcessed: number; tuplesExtracted: number; errorMessage: string;
  }> {
    if (AgentCore) return AgentCore.processIrlVideo(videoPath, taskGoal, appPackage);
    return { videoPath, framesProcessed: 0, tuplesExtracted: 0, errorMessage: "web preview" };
  },

  async getLearningStatus(): Promise<{
    loraVersion: number; latestAdapterPath: string; adapterExists: boolean;
    untrainedSamples: number; policyReady: boolean;
  }> {
    if (AgentCore) return AgentCore.getLearningStatus();
    return { loraVersion: 0, latestAdapterPath: "", adapterExists: false, untrainedSamples: 0, policyReady: false };
  },

  async getModuleStatus(): Promise<ModuleStatus> {
    if (AgentCore) {
      try {
        return await AgentCore.getModuleStatus();
      } catch (_) {}
    }
    return {
      llm: { loaded: false, modelName: "Llama-3.2-1B-Instruct", quantization: "Q4_K_M", contextLength: 4096, tokensPerSecond: 0, memoryMb: 0 },
      ocr: { ready: false, engine: "ML Kit Text Recognition v2" },
      rl: { ready: false, episodesRun: 0, loraVersion: 0, adapterLoaded: false, untrainedSamples: 0 },
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
    if (AgentCore) {
      try {
        return await AgentCore.getConfig();
      } catch (_) {}
    }
    return {
      modelPath: "/data/user/0/com.ariaagent.mobile/files/models/llama-3.2-1b-q4_k_m.gguf",
      quantization: "Q4_K_M",
      contextWindow: 4096,
      maxTokensPerTurn: 512,
      temperatureX100: 70,
      nGpuLayers: 32,
      loraAdapterPath: null,
      rlEnabled: true,
      learningRate: 1,
    };
  },

  async updateConfig(config: Partial<AgentConfig>): Promise<{ success: boolean }> {
    if (AgentCore) {
      try {
        await AgentCore.updateConfig(config);
        return { success: true };
      } catch (_) {}
    }
    return { success: true };
  },

  async getThermalStatus(): Promise<{
    level: string; inferenceSafe: boolean; trainingSafe: boolean;
    throttleCapture: boolean; emergency: boolean;
  }> {
    if (AgentCore) return AgentCore.getThermalStatus();
    return { level: "safe", inferenceSafe: true, trainingSafe: true, throttleCapture: false, emergency: false };
  },

  async getMiniLmStatus(): Promise<{ ready: boolean; path: string; downloadedBytes: number }> {
    if (AgentCore) return AgentCore.getMiniLmStatus();
    return { ready: false, path: "", downloadedBytes: 0 };
  },

  async downloadMiniLm(): Promise<boolean> {
    if (AgentCore) return AgentCore.downloadMiniLm();
    return false;
  },

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
