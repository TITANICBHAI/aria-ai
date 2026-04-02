/**
 * artifacts/web-dashboard/src/lib/api.ts
 *
 * HTTP client for the ARIA monitoring API (artifacts/api-server).
 * All calls proxy through /api/aria/ — Vite proxies to port 8080 in dev.
 *
 * On a real Android device the api-server reads from Kotlin brain snapshots.
 * In the Replit dev environment all endpoints return empty/mock data.
 */

import type {
  AgentState,
  ExperienceStats,
  ThermalStatus,
  ModuleStatus,
  ActionLog,
} from "@workspace/schemas";

const BASE = "/api/aria";

async function get<T>(path: string): Promise<T> {
  const res = await fetch(`${BASE}${path}`);
  if (!res.ok) throw new Error(`API ${path} → ${res.status}`);
  const body = await res.json() as { ok: boolean; data: T };
  return body.data;
}

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

export const api = {
  getStatus:     ()            => get<AgentState>("/status"),
  getThermal:    ()            => get<ThermalStatus>("/thermal"),
  getExperience: ()            => get<ExperienceStats>("/experience"),
  getRL:         ()            => get<RLMetrics>("/rl"),
  getLoRA:       ()            => get<LoRAVersion[]>("/lora"),
  getMemory:     ()            => get<MemoryStats>("/memory"),
  getActivity:   (limit = 50) => get<ActionLog[]>(`/activity?limit=${limit}`),
  getModules:    ()            => get<ModuleStatus>("/modules"),
};
