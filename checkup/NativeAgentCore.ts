/**
 * NativeAgentCore — TurboModule codegen specification.
 *
 * This file is the source of truth for the JS ↔ Kotlin bridge contract.
 * React Native's New Architecture (JSI) reads this file during codegen to:
 *   1. Generate C++ TurboModule host objects (zero-copy crossing the bridge)
 *   2. Generate TypeScript types for all native methods
 *   3. Validate that the Kotlin module implements every declared method
 *
 * To run codegen:
 *   cd artifacts/mobile
 *   pnpm react-native codegen
 *   # Or it runs automatically on: eas build --profile development
 *
 * Rules:
 *   - Every method here MUST have a @ReactMethod implementation in AgentCoreModule.kt
 *   - Return types use JSI types: Promise<T>, void, string, boolean, number, Object, Array
 *   - Never add methods here without wiring Kotlin — codegen will fail the build
 *   - Events are NOT declared here — they go via DeviceEventEmitter (AgentCoreModule.emitEvent)
 *
 * Events (pushed from Kotlin → JS via DeviceEventEmitter):
 *   "model_download_progress"  → { percent: number, downloadedMb: number, totalMb: number, speedMbps: number }
 *   "model_download_complete"  → { path: string }
 *   "model_download_error"     → { error: string }
 *   "agent_status_changed"     → { status: string, currentTask: string, currentApp: string, stepCount: number,
 *                                   lastAction: string, lastError: string, gameMode: string }
 *   "token_generated"          → { token: string, tokensPerSecond: number }
 *   "action_performed"         → { tool: string, nodeId: string, success: boolean, reward: number,
 *                                   stepCount: number, appPackage: string, timestamp: number }
 *   "step_started"             → { stepNumber: number, activity: "observe" | "reason" | "act" | "store" | "idle" }
 *                                   Emitted at the START of each agent loop iteration, before any work
 *                                   begins. Lets the UI show the current phase spinner immediately.
 *                                   Phase 10: JS subscribes to this instead of polling getAgentState().
 *   "thermal_status_changed"   → { level: string, inferenceSafe: boolean, trainingSafe: boolean,
 *                                   throttleCapture: boolean, emergency: boolean }
 *   "learning_cycle_complete"  → { loraVersion: number, policyVersion: number }
 */

import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

// ─── Shared return types ──────────────────────────────────────────────────────

export interface ModelInfo {
  modelName: string;
  quantization: string;
  contextLength: number;
  maxTokensPerTurn: number;
  downloadUrl: string;
  diskSizeMb: number;
  ramSizeMb: number;
  isReady: boolean;
  path: string;
}

export interface LlmStatus {
  loaded: boolean;
  tokensPerSecond: number;
  memoryMb: number;
  modelName: string;
  quantization: string;
  contextLength: number;
}

export interface AgentStatus {
  status: string;          // "idle" | "running" | "paused" | "done" | "error"
  modelReady: boolean;
  llmLoaded: boolean;
  accessibilityActive: boolean;
  screenCaptureActive: boolean;
  tokensPerSecond: number;
  memoryMb: number;
}

export interface AgentLoopStatus {
  status: string;          // "idle" | "running" | "paused" | "done" | "error"
  goal: string;
  appPackage: string;
  stepCount: number;
  lastAction: string;
  lastError: string;
}

export interface ExperienceStats {
  totalTuples: number;
  successCount: number;
  failureCount: number;
  edgeCaseCount: number;
}

export interface ExperienceEntry {
  id: string;
  timestamp: number;
  appPackage: string;
  taskType: string;
  actionJson: string;
  result: string;
  reward: number;
  isEdgeCase: boolean;
}

export interface RlCycleResult {
  success: boolean;
  samplesUsed: number;
  adapterPath: string;
  loraVersion: number;
  errorMessage: string;
}

export interface IrlResult {
  videoPath: string;
  framesProcessed: number;
  tuplesExtracted: number;
  errorMessage: string;
}

export interface LearningStatus {
  loraVersion: number;
  latestAdapterPath: string;
  adapterExists: boolean;
  untrainedSamples: number;
  policyReady: boolean;
}

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

export interface ThermalStatus {
  level: string;           // "safe" | "light" | "moderate" | "severe" | "critical"
  inferenceSafe: boolean;
  trainingSafe: boolean;
  throttleCapture: boolean;
  emergency: boolean;
}

export interface PermissionsStatus {
  accessibility: boolean;
  screenCapture: boolean;
  notifications: boolean;
}

export interface GameLoopStatus {
  isActive: boolean;
  gameType: string;
  episodeCount: number;
  stepCount: number;
  currentScore: number;
  highScore: number;
  totalReward: number;
  lastAction: string;
  isGameOver: boolean;
}

// ─── TurboModule spec ─────────────────────────────────────────────────────────

export interface Spec extends TurboModule {
  // ── Model readiness ────────────────────────────────────────────────────────
  checkModelReady(): Promise<boolean>;
  getModelInfo(): Promise<ModelInfo>;

  // ── Model download ─────────────────────────────────────────────────────────
  startModelDownload(): Promise<boolean>;
  cancelModelDownload(): Promise<boolean>;

  // ── LLM inference ──────────────────────────────────────────────────────────
  loadModel(): Promise<boolean>;
  runInference(prompt: string, maxTokens: number): Promise<string>;
  getLlmStatus(): Promise<LlmStatus>;

  // ── Perception ─────────────────────────────────────────────────────────────
  observeScreen(): Promise<string>;
  runOcr(imagePath: string): Promise<string>;
  getAccessibilityTree(): Promise<string>;

  // ── Actions ────────────────────────────────────────────────────────────────
  executeAction(actionJson: string): Promise<boolean>;

  // ── Agent loop control ─────────────────────────────────────────────────────
  startAgent(goal: string, appPackage: string): Promise<boolean>;
  startAgentLearnOnly(goal: string, appPackage: string): Promise<boolean>;
  stopAgent(): Promise<boolean>;
  pauseAgent(): Promise<boolean>;
  getAgentLoopStatus(): Promise<AgentLoopStatus>;

  // ── Agent status (combined) ────────────────────────────────────────────────
  getAgentStatus(): Promise<AgentStatus>;

  // ── Module status (all subsystem health in one call) ──────────────────────
  getModuleStatus(): Promise<Object>;

  // ── Memory / experience ────────────────────────────────────────────────────
  getExperienceStats(): Promise<ExperienceStats>;
  getRecentExperiences(limit: number): Promise<ExperienceEntry[]>;
  clearMemory(): Promise<boolean>;

  // ── RL / Learning ──────────────────────────────────────────────────────────
  runRlCycle(): Promise<RlCycleResult>;
  processIrlVideo(videoPath: string, taskGoal: string, appPackage: string): Promise<IrlResult>;
  getLearningStatus(): Promise<LearningStatus>;

  // ── Config ─────────────────────────────────────────────────────────────────
  getConfig(): Promise<AgentConfig>;
  updateConfig(config: Object): Promise<boolean>;

  // ── Thermal guard ──────────────────────────────────────────────────────────
  getThermalStatus(): Promise<ThermalStatus>;

  // ── MiniLM embedding model ─────────────────────────────────────────────────
  getMiniLmStatus(): Promise<Object>;
  downloadMiniLm(): Promise<boolean>;

  // ── Object detector model ──────────────────────────────────────────────────
  isDetectorModelReady(): Promise<boolean>;
  downloadDetectorModel(): Promise<boolean>;
  detectObjectsInImage(imagePath: string): Promise<Object>;

  // ── Permissions ────────────────────────────────────────────────────────────
  isAccessibilityEnabled(): Promise<boolean>;
  openAccessibilitySettings(): Promise<boolean>;
  getPermissionsStatus(): Promise<PermissionsStatus>;
  openNotificationSettings(): Promise<boolean>;

  // ── Screen labeler ─────────────────────────────────────────────────────────
  captureScreenForLabeling(): Promise<Object>;
  getObjectLabels(appPackage: string, screenHash: string): Promise<Object[]>;
  getAllLabels(): Promise<Object[]>;
  saveObjectLabels(appPackage: string, screenHash: string, labelsJson: string): Promise<boolean>;
  deleteObjectLabel(labelId: string): Promise<boolean>;
  enrichLabelsWithLLM(labelsJson: string, screenContext: string): Promise<Object>;
  getLabelStats(): Promise<Object>;

  // ── Progress / goal tracking ───────────────────────────────────────────────
  getProgressContext(): Promise<string>;
  clearProgress(): Promise<boolean>;
  initGoals(goal: string, subTasksJson: string): Promise<boolean>;
  getGoalSummary(): Promise<Object>;
  markSubTaskPassed(taskId: string): Promise<boolean>;

  // ── Task queue ─────────────────────────────────────────────────────────────
  enqueueTask(goal: string, appPackage: string, priority: number): Promise<Object>;
  dequeueTask(): Promise<Object>;
  getTaskQueue(): Promise<Object[]>;
  removeQueuedTask(taskId: string): Promise<boolean>;
  clearTaskQueue(): Promise<boolean>;

  // ── App skill store ────────────────────────────────────────────────────────
  getAppSkill(appPackage: string): Promise<Object>;
  getAllAppSkills(): Promise<Object[]>;
  clearAppSkills(): Promise<boolean>;

  // ── Local inference server ─────────────────────────────────────────────────
  getSnapshotPath(): Promise<string>;
  getLocalServerUrl(): Promise<string>;
  getDeviceIp(): Promise<string>;
  isLocalServerRunning(): Promise<boolean>;
  startLocalServer(port: number): Promise<string>;
  stopLocalServer(): Promise<boolean>;

  // ── Game loop ──────────────────────────────────────────────────────────────
  getGameLoopStatus(): Promise<GameLoopStatus>;

  // ── Chat context builder ───────────────────────────────────────────────────
  // Reads agent state, memory, task queue, and app skills in Kotlin and returns
  // the fully-formatted system prompt. Reduces 4× bridge round-trips to 1.
  buildChatContext(userMessage: string, historyJson: string): Promise<string>;

  // ── Event emitter boilerplate (required by React Native event system) ──────
  addListener(eventName: string): void;
  removeListeners(count: number): void;
}

export default TurboModuleRegistry.getEnforcing<Spec>('AgentCore');
