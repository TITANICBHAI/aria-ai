/**
 * @workspace/ui-core — Shared React UI components for ARIA Agent dashboards.
 *
 * Used by:
 *   - artifacts/web-dashboard/ (monitoring web UI)
 *   - potentially artifacts/mobile JS layer for any shared web-compatible components
 *
 * Design tokens match ARIATheme.kt (Phase 11 Jetpack Compose).
 * All components are pure React — no React Native imports allowed here.
 */

export { StatusPill }   from "./components/StatusPill";
export { MetricCard }   from "./components/MetricCard";
export { ThermalBadge } from "./components/ThermalBadge";
export { AgentLogRow }  from "./components/AgentLogRow";
export { ARIAColors, ARIACssVars } from "./theme";
export type { ARIAColorKey } from "./theme";
