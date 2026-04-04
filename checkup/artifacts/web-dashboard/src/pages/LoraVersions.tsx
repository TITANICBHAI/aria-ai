import React from "react";
import { usePolling } from "../hooks/usePolling";
import { api } from "../lib/api";
import { formatPercent } from "@workspace/shared-utils";

const col = { muted: "#9ca3af", surface: "#111827", border: "#1e2a3a", primary: "#00d4ff", accent: "#7c3aed", success: "#10b981", error: "#ef4444" };

export function LoraVersions() {
  const { data: versions, loading } = usePolling(api.getLoRA, 15000);

  return (
    <div style={{ padding: 24 }}>
      <h2 style={{ fontSize: 16, fontWeight: 700, marginBottom: 20 }}>LoRA Adapter Versions</h2>

      <p style={{ color: col.muted, fontSize: 13, lineHeight: 1.6, marginBottom: 20 }}>
        Each entry is a LoRA adapter trained on-device during idle + charging sessions.
        Adapters are layered on top of Llama 3.2-1B Q4_K_M and make the model better
        at navigating your specific apps and completing your specific tasks over time.
      </p>

      {loading && !versions && (
        <div style={{ textAlign: "center", color: col.muted, padding: 40 }}>Loading…</div>
      )}

      {versions && versions.length === 0 && (
        <div style={{ background: col.surface, borderRadius: 10, border: `1px solid ${col.border}`, padding: 32, textAlign: "center", color: col.muted }}>
          <div style={{ fontSize: 32, marginBottom: 12 }}>🧠</div>
          <div style={{ fontSize: 14, marginBottom: 6 }}>No LoRA adapters trained yet</div>
          <div style={{ fontSize: 12 }}>
            Adapters are generated automatically after the agent accumulates enough successful task traces.
            Plug in the device and leave it idle to trigger the first training cycle.
          </div>
        </div>
      )}

      {versions && versions.map((v) => (
        <div
          key={v.version}
          style={{
            background: col.surface,
            border: `1px solid ${col.border}`,
            borderRadius: 10,
            padding: "14px 16px",
            marginBottom: 10,
            display: "flex",
            alignItems: "center",
            gap: 16,
          }}
        >
          <div style={{ fontSize: 22, fontWeight: 700, color: col.accent, minWidth: 40 }}>
            v{v.version}
          </div>
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: 13, color: col.muted, fontFamily: "monospace" }}>
              {v.path.split("/").pop()}
            </div>
            <div style={{ fontSize: 11, color: col.muted, marginTop: 4 }}>
              Trained {v.trainedAt} · {v.samplesUsed} samples
            </div>
          </div>
          <div style={{ textAlign: "right" }}>
            <div style={{ fontSize: 13, fontWeight: 700, color: v.successRateDelta >= 0 ? col.success : col.error }}>
              {v.successRateDelta >= 0 ? "+" : ""}{formatPercent(v.successRateDelta)}
            </div>
            <div style={{ fontSize: 11, color: col.muted }}>success rate Δ</div>
          </div>
        </div>
      ))}
    </div>
  );
}
