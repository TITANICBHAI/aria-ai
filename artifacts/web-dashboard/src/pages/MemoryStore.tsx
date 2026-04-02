import React from "react";
import { MetricCard } from "@workspace/ui-core";
import { formatBytes } from "@workspace/shared-utils";
import { usePolling } from "../hooks/usePolling";
import { api } from "../lib/api";

const col = { muted: "#9ca3af", surface: "#111827", border: "#1e2a3a", primary: "#00d4ff", accent: "#7c3aed", success: "#10b981", warning: "#f59e0b" };

export function MemoryStore() {
  const { data: mem, loading } = usePolling(api.getMemory, 10000);

  return (
    <div style={{ padding: 24 }}>
      <h2 style={{ fontSize: 16, fontWeight: 700, marginBottom: 8 }}>Memory Store</h2>
      <p style={{ color: col.muted, fontSize: 13, lineHeight: 1.6, marginBottom: 20 }}>
        The memory store holds embeddings of past task observations (MiniLM-L6-v2, ONNX Runtime).
        When the agent observes a new screen, it retrieves the top-K most similar past observations
        from this store and injects them into the LLM prompt — giving the model memory across sessions.
      </p>

      {loading && !mem && (
        <div style={{ textAlign: "center", color: col.muted, padding: 40 }}>Loading…</div>
      )}

      {mem && (
        <>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(140px, 1fr))", gap: 12, marginBottom: 28 }}>
            <MetricCard label="Embeddings"    value={mem.embeddingCount} accent={col.primary} />
            <MetricCard label="DB Size"       value={formatBytes(mem.dbSizeKb * 1024)} accent={col.muted} />
            <MetricCard label="Edge Cases"    value={mem.edgeCaseCount} accent={col.warning} />
            <MetricCard label="MiniLM Model"  value={mem.miniLmReady ? "Ready" : "Not loaded"} accent={mem.miniLmReady ? col.success : col.muted} />
          </div>

          <div style={{ background: col.surface, border: `1px solid ${col.border}`, borderRadius: 10, padding: 20 }}>
            <h3 style={{ fontSize: 13, color: col.muted, marginBottom: 12, textTransform: "uppercase", letterSpacing: 0.7 }}>
              Architecture
            </h3>
            <ul style={{ color: col.muted, fontSize: 13, lineHeight: 2, paddingLeft: 18 }}>
              <li><strong style={{ color: "#e5e7eb" }}>Embedding model:</strong> MiniLM-L6-v2 (ONNX Runtime) — 22M params, ~23MB on disk</li>
              <li><strong style={{ color: "#e5e7eb" }}>Similarity:</strong> Cosine similarity, top-3 retrieved per observation</li>
              <li><strong style={{ color: "#e5e7eb" }}>Storage:</strong> SQLite (device internal storage) — no cloud sync</li>
              <li><strong style={{ color: "#e5e7eb" }}>Edge cases:</strong> Observations where agent recovered from failure — highest retrieval priority</li>
              <li><strong style={{ color: "#e5e7eb" }}>Fallback:</strong> MD5 hash similarity when MiniLM is not yet downloaded</li>
            </ul>
          </div>

          {mem.embeddingCount === 0 && (
            <div style={{ marginTop: 16, padding: "10px 16px", background: "#f59e0b11", border: `1px solid ${col.warning}44`, borderRadius: 8, color: col.warning, fontSize: 12 }}>
              Memory store is empty. The agent will populate it as it completes tasks on the device.
            </div>
          )}
        </>
      )}
    </div>
  );
}
