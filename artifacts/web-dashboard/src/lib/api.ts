/**
 * artifacts/web-dashboard/src/lib/api.ts
 *
 * Local mock data for the ARIA monitoring dashboard.
 * All data originates on the Android device (Kotlin brain).
 * No server, no cloud — fully local.
 *
 * When connected to a real device, replace these with direct
 * local-network fetch calls (e.g. http://device-ip:PORT/aria/*).
 */

import type {
  AgentState,
  ExperienceStats,
  ThermalStatus,
  ModuleStatus,
  ActionLog,
} from "@workspace/schemas";

export interface RLMetrics {
  episodesRun: number;
  loraVersion: number;
  adapterLoaded: boolean;
  untrainedSamples: number;
  adamStep: number;
  lastPolicyLoss: number;
  rewardHistory: { step: number; reward: number }[];
  policyLossHistory: { step: number; loss: number }[];
}

export interface LoRAVersion {
  version: number;
  path: string;
  trainedAt: string;
  samplesUsed: number;
  successRateDelta: number;
}

export interface MemoryStats {
  embeddingCount: number;
  dbSizeKb: number;
  edgeCaseCount: number;
  miniLmReady: boolean;
}

function mockAgentState(): AgentState {
  return {
    status: "idle",
    currentTask: null,
    currentApp: null,
    tokenRate: 0,
    memoryUsedMb: 0,
    sessionStartedAt: null,
    actionsPerformed: 0,
    successRate: 0,
    modelReady: false,
    llmLoaded: false,
    accessibilityActive: false,
    screenCaptureActive: false,
    gameMode: "none",
  };
}

function mockThermal(): ThermalStatus {
  return { level: "safe", inferenceSafe: true, trainingSafe: true, throttleCapture: false, emergency: false };
}

function mockExperienceStats(): ExperienceStats {
  return { totalTuples: 0, successCount: 0, failureCount: 0, edgeCaseCount: 0 };
}

function mockRLMetrics(): RLMetrics {
  return {
    episodesRun: 0,
    loraVersion: 0,
    adapterLoaded: false,
    untrainedSamples: 0,
    adamStep: 0,
    lastPolicyLoss: 0,
    rewardHistory: [],
    policyLossHistory: [],
  };
}

function mockLoRAVersions(): LoRAVersion[] {
  return [];
}

function mockMemoryStats(): MemoryStats {
  return { embeddingCount: 0, dbSizeKb: 0, edgeCaseCount: 0, miniLmReady: false };
}

function mockModules(): ModuleStatus {
  return {
    llm: { loaded: false, modelName: "Llama-3.2-1B-Instruct", quantization: "Q4_K_M", contextLength: 4096, tokensPerSecond: 0, memoryMb: 0 },
    ocr: { ready: false, engine: "ML Kit Text Recognition v2" },
    rl: { ready: false, episodesRun: 0, loraVersion: 0, adapterLoaded: false, untrainedSamples: 0, adamStep: 0, lastPolicyLoss: 0 },
    memory: { ready: false, embeddingCount: 0, dbSizeKb: 0 },
    accessibility: { granted: false, active: false },
    screenCapture: { granted: false, active: false },
  };
}

export const api = {
  getStatus:     (): Promise<AgentState>      => Promise.resolve(mockAgentState()),
  getThermal:    (): Promise<ThermalStatus>   => Promise.resolve(mockThermal()),
  getExperience: (): Promise<ExperienceStats> => Promise.resolve(mockExperienceStats()),
  getRL:         (): Promise<RLMetrics>       => Promise.resolve(mockRLMetrics()),
  getLoRA:       (): Promise<LoRAVersion[]>   => Promise.resolve(mockLoRAVersions()),
  getMemory:     (): Promise<MemoryStats>     => Promise.resolve(mockMemoryStats()),
  getActivity:   (_limit = 50): Promise<ActionLog[]> => Promise.resolve([]),
  getModules:    (): Promise<ModuleStatus>    => Promise.resolve(mockModules()),
};
