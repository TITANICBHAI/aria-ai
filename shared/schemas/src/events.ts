/**
 * shared/schemas/src/events.ts
 *
 * Event payload types for all events emitted from Kotlin → JS via DeviceEventEmitter.
 * These match the maps built in AgentCoreModule.kt and AgentEventBus.
 *
 * Used by:
 *   - artifacts/mobile/app/context/AgentContext.tsx (NativeEventEmitter subscriptions)
 *   - artifacts/web-dashboard/ (local monitoring web UI)
 */

import type { ThermalLevel, GameType } from "./agent";

export interface ModelDownloadProgressEvent {
  percent: number;
  downloadedMb: number;
  totalMb: number;
  speedMbps: number;
}

export interface ModelDownloadCompleteEvent {
  path: string;
}

export interface ModelDownloadErrorEvent {
  error: string;
}

export interface AgentStatusChangedEvent {
  status: "idle" | "running" | "paused" | "error";
  currentTask: string;
  currentApp: string;
  gameMode?: GameType;
}

export interface TokenGeneratedEvent {
  token: string;
  tokensPerSecond?: number;
}

export interface ActionPerformedEvent {
  tool: string;
  nodeId: string;
  success: boolean;
  reward: number;
  stepCount?: number;
  appPackage?: string;
}

export interface StepStartedEvent {
  stepCount: number;
  phase: "observe" | "detect" | "retrieve" | "reason" | "act" | "store";
  goal: string;
  app: string;
}

export interface LearningCycleCompleteEvent {
  loraVersion: number;
  policyVersion: number;
}

export interface ThermalStatusChangedEvent {
  level: ThermalLevel;
  inferenceSafe: boolean;
  trainingSafe: boolean;
  emergency: boolean;
}

export interface GameLoopStatusEvent {
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

export interface ConfigUpdatedEvent {
  key: string;
  value: string | number | boolean;
}

/** Union of all possible event payloads, keyed by event name */
export type ARIAEventMap = {
  model_download_progress:  ModelDownloadProgressEvent;
  model_download_complete:  ModelDownloadCompleteEvent;
  model_download_error:     ModelDownloadErrorEvent;
  agent_status_changed:     AgentStatusChangedEvent;
  token_generated:          TokenGeneratedEvent;
  action_performed:         ActionPerformedEvent;
  step_started:             StepStartedEvent;
  learning_cycle_complete:  LearningCycleCompleteEvent;
  thermal_status_changed:   ThermalStatusChangedEvent;
  game_loop_status:         GameLoopStatusEvent;
  config_updated:           ConfigUpdatedEvent;
};

export type ARIAEventName = keyof ARIAEventMap;
