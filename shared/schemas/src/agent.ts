/**
 * shared/schemas/src/agent.ts
 *
 * Canonical data contracts for ARIA Agent.
 * These types are the single source of truth shared between:
 *   - artifacts/mobile/ (React Native JS layer, via AgentCoreBridge.ts)
 *   - artifacts/web-dashboard/ (local monitoring web UI)
 *
 * Kotlin side maps to these via AgentCoreModule.kt bridge methods.
 * Changes here must be mirrored in the corresponding Kotlin data maps.
 */

// ─── Agent status ─────────────────────────────────────────────────────────────

export type AgentStatus = "idle" | "running" | "paused" | "error";
export type GameType    = "none" | "arcade" | "puzzle" | "strategy";
export type ThermalLevel = "safe" | "light" | "moderate" | "severe" | "critical";

export interface AgentState {
  status: AgentStatus;
  currentTask: string | null;
  currentApp: string | null;
  tokenRate: number;           // tokens/sec from last inference
  memoryUsedMb: number;
  sessionStartedAt: number | null;
  actionsPerformed: number;
  successRate: number;         // 0–1
  modelReady: boolean;
  llmLoaded: boolean;
  accessibilityActive: boolean;
  screenCaptureActive: boolean;
  gameMode: GameType;
}

// ─── Module status ─────────────────────────────────────────────────────────────

export interface LLMStatus {
  loaded: boolean;
  modelName: string;
  quantization: string;
  contextLength: number;
  tokensPerSecond: number;
  memoryMb: number;
}

export interface RLStatus {
  ready: boolean;
  episodesRun: number;
  loraVersion: number;
  adapterLoaded: boolean;
  untrainedSamples: number;
  adamStep: number;
  lastPolicyLoss: number;
}

export interface ModuleStatus {
  llm: LLMStatus;
  ocr: { ready: boolean; engine: string };
  rl: RLStatus;
  memory: { ready: boolean; embeddingCount: number; dbSizeKb: number };
  accessibility: { granted: boolean; active: boolean };
  screenCapture: { granted: boolean; active: boolean };
}

// ─── Experience / action log ──────────────────────────────────────────────────

export type ActionType = "tap" | "swipe" | "text" | "scroll" | "intent" | "observe";

export interface ActionLog {
  id: string;
  timestamp: number;
  type: ActionType;
  description: string;
  app: string;
  success: boolean;
  rewardSignal?: number;
}

export interface ExperienceStats {
  totalTuples: number;
  successCount: number;
  failureCount: number;
  edgeCaseCount: number;
}

// ─── Model info ───────────────────────────────────────────────────────────────

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

// ─── Download progress ────────────────────────────────────────────────────────

export interface DownloadProgress {
  percent: number;
  downloadedMb: number;
  totalMb: number;
  speedMbps: number;
}

// ─── Config ───────────────────────────────────────────────────────────────────

export interface AgentConfig {
  modelPath: string;
  quantization: string;
  contextWindow: number;
  maxTokensPerTurn: number;
  temperatureX100: number;
  nGpuLayers: number;
  loraAdapterPath: string | null;
  rlEnabled: boolean;
  learningRate: number;
}

// ─── Thermal ──────────────────────────────────────────────────────────────────

export interface ThermalStatus {
  level: ThermalLevel;
  inferenceSafe: boolean;
  trainingSafe: boolean;
  throttleCapture: boolean;
  emergency: boolean;
}

// ─── Progress persistence (Phase 14.4 — Ralph Loop) ──────────────────────────

export interface SubTask {
  id: string;
  label: string;
  passed: boolean;
}

export interface GoalState {
  goal: string;
  subTasks: SubTask[];
  updatedAt: string;
}

// ─── Game loop ────────────────────────────────────────────────────────────────

export interface GameLoopStatus {
  isActive: boolean;
  gameType: GameType;
  episodeCount: number;
  stepCount: number;
  currentScore: number;
  highScore: number;
  totalReward: number;
  lastAction: string;
  isGameOver: boolean;
}

// ─── Object labeler ───────────────────────────────────────────────────────────

export type ElementType =
  | "button" | "text" | "input" | "icon"
  | "image" | "container" | "toggle" | "link" | "unknown";

export interface ObjectLabel {
  id: string;
  appPackage: string;
  screenHash: string;
  x: number;
  y: number;
  name: string;
  context: string;
  elementType: ElementType;
  ocrText: string;
  meaning: string;
  interactionHint: string;
  reasoningContext: string;
  importanceScore: number;
  additionalFields: Record<string, string>;
  isEnriched: boolean;
  createdAt: number;
  updatedAt: number;
}

export interface LabelStats {
  total: number;
  enriched: number;
}

// ─── Object detection ─────────────────────────────────────────────────────────

export interface DetectedObject {
  label: string;
  confidence: number;
  normX: number;
  normY: number;
  normW: number;
  normH: number;
}
