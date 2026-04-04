/**
 * @workspace/schemas — Canonical data contracts for ARIA Agent.
 *
 * Single source of truth for all types shared between:
 *   - React Native mobile app (JS layer)
 *   - Web dashboard (monitoring UI)
 *   - API server (REST bridge)
 *
 * Kotlin counterparts are in:
 *   - android/app/src/main/kotlin/com/ariaagent/mobile/bridge/AgentCoreModule.kt
 *   - android/app/src/main/kotlin/com/ariaagent/mobile/core/persistence/ProgressPersistence.kt
 */

export * from "./agent";
export * from "./events";
