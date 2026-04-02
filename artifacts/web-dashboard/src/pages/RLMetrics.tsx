import React from "react";
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend,
} from "recharts";
import { MetricCard } from "@workspace/ui-core";
import { usePolling } from "../hooks/usePolling";
import { api } from "../lib/api";

const col = { muted: "#9ca3af", surface: "#111827", border: "#1e2a3a", primary: "#00d4ff", accent: "#7c3aed", success: "#10b981", warning: "#f59e0b" };

export function RLMetrics() {
  const { data: rl, loading } = usePolling(api.getRL, 8000);

  if (loading && !rl) {
    return <div style={{ padding: 40, textAlign: "center", color: col.muted }}>Loading RL metrics…</div>;
  }

  const rewardHistory  = rl?.rewardHistory  ?? [];
  const policyHistory  = rl?.policyLossHistory ?? [];
  const hasReward      = rewardHistory.length > 0;
  const hasPolicyLoss  = policyHistory.length > 0;

  return (
    <div style={{ padding: 24 }}>
      <h2 style={{ fontSize: 16, fontWeight: 700, marginBottom: 20 }}>RL Metrics</h2>

      {/* KPI cards */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(130px, 1fr))", gap: 12, marginBottom: 28 }}>
        <MetricCard label="Episodes Run"      value={rl?.episodesRun      ?? 0} accent={col.primary} />
        <MetricCard label="LoRA Version"      value={`v${rl?.loraVersion  ?? 0}`} accent={col.accent} />
        <MetricCard label="Adam Step"         value={rl?.adamStep         ?? 0} accent={col.muted} />
        <MetricCard label="Last Policy Loss"  value={(rl?.lastPolicyLoss  ?? 0).toFixed(4)} accent={col.warning} />
        <MetricCard label="Untrained Samples" value={rl?.untrainedSamples ?? 0} accent={col.muted} />
        <MetricCard label="Adapter Loaded"    value={rl?.adapterLoaded ? "Yes" : "No"} accent={rl?.adapterLoaded ? col.success : col.muted} />
      </div>

      {/* Reward history chart */}
      <div style={{ background: col.surface, borderRadius: 10, border: `1px solid ${col.border}`, padding: 16, marginBottom: 16 }}>
        <h3 style={{ fontSize: 13, color: col.muted, marginBottom: 14, textTransform: "uppercase", letterSpacing: 0.7 }}>
          Reward History (per step)
        </h3>
        {hasReward ? (
          <ResponsiveContainer width="100%" height={200}>
            <LineChart data={rewardHistory} margin={{ top: 0, right: 8, left: -20, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke={col.border} />
              <XAxis dataKey="step" tick={{ fill: col.muted, fontSize: 11 }} />
              <YAxis tick={{ fill: col.muted, fontSize: 11 }} />
              <Tooltip contentStyle={{ background: "#111827", border: `1px solid ${col.border}`, borderRadius: 6 }} />
              <Line type="monotone" dataKey="reward" stroke={col.primary} dot={false} strokeWidth={1.5} />
            </LineChart>
          </ResponsiveContainer>
        ) : (
          <div style={{ height: 200, display: "flex", alignItems: "center", justifyContent: "center", color: col.muted, fontSize: 13 }}>
            No reward data yet. Run tasks on the device to accumulate experience.
          </div>
        )}
      </div>

      {/* Policy loss chart */}
      <div style={{ background: col.surface, borderRadius: 10, border: `1px solid ${col.border}`, padding: 16 }}>
        <h3 style={{ fontSize: 13, color: col.muted, marginBottom: 14, textTransform: "uppercase", letterSpacing: 0.7 }}>
          Policy Loss (per training step)
        </h3>
        {hasPolicyLoss ? (
          <ResponsiveContainer width="100%" height={200}>
            <LineChart data={policyHistory} margin={{ top: 0, right: 8, left: -20, bottom: 0 }}>
              <CartesianGrid strokeDasharray="3 3" stroke={col.border} />
              <XAxis dataKey="step" tick={{ fill: col.muted, fontSize: 11 }} />
              <YAxis tick={{ fill: col.muted, fontSize: 11 }} />
              <Tooltip contentStyle={{ background: "#111827", border: `1px solid ${col.border}`, borderRadius: 6 }} />
              <Line type="monotone" dataKey="loss" stroke={col.accent} dot={false} strokeWidth={1.5} />
            </LineChart>
          </ResponsiveContainer>
        ) : (
          <div style={{ height: 200, display: "flex", alignItems: "center", justifyContent: "center", color: col.muted, fontSize: 13 }}>
            No policy loss data yet. Training runs during idle + charging.
          </div>
        )}
      </div>
    </div>
  );
}
