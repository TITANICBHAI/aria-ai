/**
 * artifacts/web-dashboard/src/lib/api.ts
 *
 * ARIA monitoring dashboard data layer.
 *
 * When a device URL is set (stored in localStorage as "aria_device_url"),
 * every api call fetches live data from the on-device HTTP server:
 *   http://{device-ip}:8765/aria/{endpoint}
 *
 * Falls back to static mock data automatically if the device is unreachable.
 * No cloud, no server, fully local.
 */

import type {
  AgentState,
  ExperienceStats,
  ThermalStatus,
  ModuleStatus,
  ActionLog,
} from "@workspace/schemas";

// ─── Types ────────────────────────────────────────────────────────────────────

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

// ─── Device connection ────────────────────────────────────────────────────────

const DEVICE_URL_KEY = "aria_device_url";

/** Returns the saved device base URL (e.g. "http://192.168.1.42:8765"), or null. */
export function getDeviceUrl(): string | null {
  try { return localStorage.getItem(DEVICE_URL_KEY); } catch { return null; }
}

/** Saves the device base URL so subsequent api calls use the real device. */
export function setDeviceUrl(url: string): void {
  try { localStorage.setItem(DEVICE_URL_KEY, url.replace(/\/$/, "")); } catch { /* ignore */ }
}

/** Clears the saved device URL — api calls revert to mock data. */
export function clearDeviceUrl(): void {
  try { localStorage.removeItem(DEVICE_URL_KEY); } catch { /* ignore */ }
}

/**
 * Fetches a /aria/{endpoint} from the on-device server.
 * Throws if the device is unreachable, times out (3 s), or returns an error.
 */
async function fetchDevice<T>(endpoint: string): Promise<T> {
  const base = getDeviceUrl();
  if (!base) throw new Error("no device configured");
  const res = await fetch(`${base}/aria/${endpoint}`, {
    signal: AbortSignal.timeout(3000),
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  const json = await res.json();
  if (!json.ok) throw new Error(json.error ?? "device error");
  return json.data as T;
}

// ─── Mock fallbacks ───────────────────────────────────────────────────────────

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

function mockRLMetrics(): RLMetrics {
  return {
    episodesRun: 0, loraVersion: 0, adapterLoaded: false,
    untrainedSamples: 0, adamStep: 0, lastPolicyLoss: 0,
    rewardHistory: [], policyLossHistory: [],
  };
}

function mockModules(): ModuleStatus {
  return {
    llm:           { loaded: false, modelName: "Llama-3.2-1B-Instruct", quantization: "Q4_K_M", contextLength: 4096, tokensPerSecond: 0, memoryMb: 0 },
    ocr:           { ready: false, engine: "ML Kit Text Recognition v2" },
    rl:            { ready: false, episodesRun: 0, loraVersion: 0, adapterLoaded: false, untrainedSamples: 0, adamStep: 0, lastPolicyLoss: 0 },
    memory:        { ready: false, embeddingCount: 0, dbSizeKb: 0 },
    accessibility: { granted: false, active: false },
    screenCapture: { granted: false, active: false },
  };
}

// ─── API ──────────────────────────────────────────────────────────────────────

export const api = {
  async getStatus(): Promise<AgentState> {
    try { return await fetchDevice<AgentState>("status"); }
    catch { return mockAgentState(); }
  },

  async getThermal(): Promise<ThermalStatus> {
    try { return await fetchDevice<ThermalStatus>("thermal"); }
    catch { return mockThermal(); }
  },

  async getExperience(): Promise<ExperienceStats> {
    try {
      const mem = await fetchDevice<MemoryStats>("memory");
      return { totalTuples: mem.embeddingCount, successCount: 0, failureCount: 0, edgeCaseCount: mem.edgeCaseCount };
    } catch { return { totalTuples: 0, successCount: 0, failureCount: 0, edgeCaseCount: 0 }; }
  },

  async getRL(): Promise<RLMetrics> {
    try { return await fetchDevice<RLMetrics>("rl"); }
    catch { return mockRLMetrics(); }
  },

  async getLoRA(): Promise<LoRAVersion[]> {
    try { return await fetchDevice<LoRAVersion[]>("lora"); }
    catch { return []; }
  },

  async getMemory(): Promise<MemoryStats> {
    try { return await fetchDevice<MemoryStats>("memory"); }
    catch { return { embeddingCount: 0, dbSizeKb: 0, edgeCaseCount: 0, miniLmReady: false }; }
  },

  async getActivity(_limit = 50): Promise<ActionLog[]> {
    try { return await fetchDevice<ActionLog[]>("activity"); }
    catch { return []; }
  },

  async getModules(): Promise<ModuleStatus> {
    try { return await fetchDevice<ModuleStatus>("modules"); }
    catch { return mockModules(); }
  },
};
