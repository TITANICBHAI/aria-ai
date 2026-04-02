/**
 * artifacts/api-server/src/routes/aria.ts
 *
 * ARIA Agent monitoring endpoints — Phase 0.4 (Web Dashboard).
 *
 * These endpoints are consumed by artifacts/web-dashboard/ to display:
 *   - Agent status (current task, thermal, step count)
 *   - Experience store (action log, RL stats)
 *   - RL metrics (reward history, policy loss, episodes)
 *   - LoRA adapter versions
 *   - Memory store summary
 *
 * Data source:
 *   On a real Android device, the Kotlin brain writes periodic JSON snapshots
 *   to a shared directory that these endpoints read from.
 *   In the Replit dev environment these endpoints return mock data so the
 *   dashboard can be developed and demonstrated without a device.
 *
 * Snapshot path convention (on-device):
 *   /data/user/0/com.ariaagent.mobile/files/monitoring/
 *     status.json          — AgentState snapshot (written every step)
 *     experience_stats.json — ExperienceStats (written after each step)
 *     rl_metrics.json      — RLMetrics history (written after each training cycle)
 *     lora_versions.json   — LoRA adapter version list
 *     memory_stats.json    — EmbeddingStore stats
 */

import { Router } from "express";
import type {
  AgentState,
  ExperienceStats,
  ThermalStatus,
  ModuleStatus,
  ActionLog,
} from "@workspace/schemas";

const router = Router();

// ─── Mock data (used when no device snapshot is present) ─────────────────────

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

function mockExperienceStats(): ExperienceStats {
  return { totalTuples: 0, successCount: 0, failureCount: 0, edgeCaseCount: 0 };
}

function mockThermal(): ThermalStatus {
  return { level: "safe", inferenceSafe: true, trainingSafe: true, throttleCapture: false, emergency: false };
}

interface RLMetricsSnapshot {
  episodesRun: number;
  loraVersion: number;
  adapterLoaded: boolean;
  untrainedSamples: number;
  adamStep: number;
  lastPolicyLoss: number;
  rewardHistory: { step: number; reward: number }[];
  policyLossHistory: { step: number; loss: number }[];
}

function mockRLMetrics(): RLMetricsSnapshot {
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

interface LoRAVersion {
  version: number;
  path: string;
  trainedAt: string;
  samplesUsed: number;
  successRateDelta: number;
}

function mockLoRAVersions(): LoRAVersion[] {
  return [];
}

interface MemoryStats {
  embeddingCount: number;
  dbSizeKb: number;
  edgeCaseCount: number;
  miniLmReady: boolean;
}

function mockMemoryStats(): MemoryStats {
  return { embeddingCount: 0, dbSizeKb: 0, edgeCaseCount: 0, miniLmReady: false };
}

// ─── Snapshot loader (tries to read device snapshot, falls back to mock) ─────

async function loadSnapshot<T>(filename: string, fallback: () => T): Promise<T> {
  // In a production device setup, snapshots are written by the Kotlin brain
  // to a shared location. For now, always use mock data.
  return fallback();
}

// ─── Routes ──────────────────────────────────────────────────────────────────

/** GET /api/aria/status — current agent state snapshot */
router.get("/api/aria/status", async (_req, res) => {
  const state = await loadSnapshot("status.json", mockAgentState);
  res.json({ ok: true, data: state });
});

/** GET /api/aria/thermal — thermal status */
router.get("/api/aria/thermal", async (_req, res) => {
  const thermal = await loadSnapshot("thermal.json", mockThermal);
  res.json({ ok: true, data: thermal });
});

/** GET /api/aria/experience — experience store stats */
router.get("/api/aria/experience", async (_req, res) => {
  const stats = await loadSnapshot("experience_stats.json", mockExperienceStats);
  res.json({ ok: true, data: stats });
});

/** GET /api/aria/rl — RL metrics (reward history, policy loss, episodes) */
router.get("/api/aria/rl", async (_req, res) => {
  const rl = await loadSnapshot("rl_metrics.json", mockRLMetrics);
  res.json({ ok: true, data: rl });
});

/** GET /api/aria/lora — LoRA adapter version history */
router.get("/api/aria/lora", async (_req, res) => {
  const versions = await loadSnapshot("lora_versions.json", mockLoRAVersions);
  res.json({ ok: true, data: versions });
});

/** GET /api/aria/memory — embedding store + edge case stats */
router.get("/api/aria/memory", async (_req, res) => {
  const memory = await loadSnapshot("memory_stats.json", mockMemoryStats);
  res.json({ ok: true, data: memory });
});

/** GET /api/aria/activity?limit=50 — recent action logs */
router.get("/api/aria/activity", async (req, res) => {
  const limit = Math.min(parseInt(String(req.query.limit ?? "50"), 10) || 50, 200);
  const logs: ActionLog[] = [];
  res.json({ ok: true, data: logs, total: 0, limit });
});

/** GET /api/aria/modules — full module status */
router.get("/api/aria/modules", async (_req, res) => {
  const status: ModuleStatus = {
    llm: { loaded: false, modelName: "Llama-3.2-1B-Instruct", quantization: "Q4_K_M", contextLength: 4096, tokensPerSecond: 0, memoryMb: 0 },
    ocr: { ready: false, engine: "ML Kit Text Recognition v2" },
    rl: { ready: false, episodesRun: 0, loraVersion: 0, adapterLoaded: false, untrainedSamples: 0, adamStep: 0, lastPolicyLoss: 0 },
    memory: { ready: false, embeddingCount: 0, dbSizeKb: 0 },
    accessibility: { granted: false, active: false },
    screenCapture: { granted: false, active: false },
  };
  res.json({ ok: true, data: status });
});

export default router;
