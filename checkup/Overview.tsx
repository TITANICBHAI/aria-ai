import React from "react";
import { StatusPill, MetricCard, ThermalBadge } from "@workspace/ui-core";
import { formatTokenRate, formatPercent, formatMb } from "@workspace/shared-utils";
import { usePolling } from "../hooks/usePolling";
import { api } from "../lib/api";

const col = {
  primary: "#00d4ff",
  accent:  "#7c3aed",
  success: "#10b981",
  warning: "#f59e0b",
  error:   "#ef4444",
  muted:   "#9ca3af",
  surface: "#111827",
  border:  "#1e2a3a",
};

export function Overview() {
  const { data: state,   loading: sl } = usePolling(api.getStatus,   4000);
  const { data: thermal, loading: tl } = usePolling(api.getThermal,  6000);
  const { data: exp,     loading: el } = usePolling(api.getExperience, 8000);
  const { data: modules, loading: ml } = usePolling(api.getModules,  10000);

  const loading = sl || tl || el || ml;

  if (loading && !state) {
    return (
      <div style={{ padding: 40, textAlign: "center", color: col.muted }}>
        Connecting to ARIA Agent…
      </div>
    );
  }

  const status = state?.status ?? "idle";
  const successRate = state?.successRate ?? 0;
  const memMb = state?.memoryUsedMb ?? 0;
  const tokRate = state?.tokenRate ?? 0;
  const actions = state?.actionsPerformed ?? 0;
  const thermalLevel = thermal?.level ?? "safe";

  return (
    <div style={{ padding: 24 }}>
      {/* Header row */}
      <div style={{ display: "flex", alignItems: "center", gap: 16, marginBottom: 24 }}>
        <StatusPill status={status} pulsing />
        <span style={{ color: col.muted, fontSize: 13 }}>
          {state?.currentTask
            ? `↳ ${state.currentTask}`
            : "No active task"}
        </span>
        <span style={{ marginLeft: "auto" }}>
          <ThermalBadge level={thermalLevel} />
        </span>
      </div>

      {/* Metric grid */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(140px, 1fr))", gap: 12, marginBottom: 28 }}>
        <MetricCard label="Token Rate"   value={formatTokenRate(tokRate)}        accent={col.primary} />
        <MetricCard label="Actions"      value={actions}                         accent={col.accent} />
        <MetricCard label="Success Rate" value={formatPercent(successRate)}       accent={col.success} />
        <MetricCard label="Memory"       value={formatMb(memMb)}                 accent={col.muted} />
        <MetricCard label="Experience"   value={exp?.totalTuples ?? 0} unit="tuples" accent={col.warning} />
        <MetricCard label="LLM"          value={modules?.llm.loaded ? "Loaded" : "Not loaded"}
                    accent={modules?.llm.loaded ? col.success : col.error} />
      </div>

      {/* Module status row */}
      <h3 style={{ fontSize: 12, textTransform: "uppercase", letterSpacing: 0.8, color: col.muted, marginBottom: 12 }}>
        Modules
      </h3>
      <div style={{ display: "flex", flexWrap: "wrap", gap: 10 }}>
        {[
          { label: "LLM",         ok: modules?.llm.loaded ?? false },
          { label: "OCR",         ok: modules?.ocr.ready  ?? false },
          { label: "RL Policy",   ok: modules?.rl.ready   ?? false },
          { label: "MiniLM",      ok: modules?.memory.ready ?? false },
          { label: "A11y",        ok: modules?.accessibility.active ?? false },
          { label: "Screen Cap",  ok: modules?.screenCapture.active ?? false },
        ].map(({ label, ok }) => (
          <span
            key={label}
            style={{
              padding: "4px 12px",
              borderRadius: 8,
              fontSize: 12,
              fontWeight: 600,
              background: ok ? "#10b98122" : "#ef444422",
              border:    `1px solid ${ok ? "#10b98155" : "#ef444455"}`,
              color:     ok ? col.success : col.error,
            }}
          >
            {ok ? "✓" : "✗"} {label}
          </span>
        ))}
      </div>

      {/* Thermal warning banners */}
      {thermalLevel === "severe" && (
        <div style={{ marginTop: 20, padding: "10px 16px", background: "#f59e0b22", border: "1px solid #f59e0b55", borderRadius: 8, color: col.warning, fontSize: 13 }}>
          ⚠ Cooling down — agent throttled (thermal: severe). Capture reduced to 0.5 FPS.
        </div>
      )}
      {thermalLevel === "critical" && (
        <div style={{ marginTop: 20, padding: "10px 16px", background: "#ef444422", border: "1px solid #ef444455", borderRadius: 8, color: col.error, fontSize: 13 }}>
          !! Device critical — inference suspended. Agent paused until temperature drops.
        </div>
      )}
    </div>
  );
}
