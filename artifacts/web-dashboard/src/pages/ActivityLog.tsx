import React, { useState } from "react";
import { AgentLogRow } from "@workspace/ui-core";
import { usePolling } from "../hooks/usePolling";
import { api } from "../lib/api";

const col = { muted: "#9ca3af", surface: "#111827", border: "#1e2a3a", primary: "#00d4ff" };

export function ActivityLog() {
  const [limit, setLimit] = useState(50);
  const fetcher = React.useCallback(() => api.getActivity(limit), [limit]);
  const { data: logs, loading, refresh } = usePolling(fetcher, 3000);

  return (
    <div style={{ padding: 24 }}>
      <div style={{ display: "flex", alignItems: "center", gap: 12, marginBottom: 20 }}>
        <h2 style={{ fontSize: 16, fontWeight: 700 }}>Activity Log</h2>
        <span style={{ color: col.muted, fontSize: 13 }}>
          {logs ? `${logs.length} entries` : "—"}
        </span>
        <div style={{ marginLeft: "auto", display: "flex", gap: 8 }}>
          {[25, 50, 100, 200].map(n => (
            <button
              key={n}
              onClick={() => setLimit(n)}
              style={{
                padding: "3px 10px",
                borderRadius: 6,
                fontSize: 12,
                border: `1px solid ${limit === n ? col.primary : col.border}`,
                background: limit === n ? "#00d4ff22" : "transparent",
                color: limit === n ? col.primary : col.muted,
                cursor: "pointer",
              }}
            >
              {n}
            </button>
          ))}
          <button
            onClick={refresh}
            style={{ padding: "3px 10px", borderRadius: 6, fontSize: 12, border: `1px solid ${col.border}`, background: "transparent", color: col.muted, cursor: "pointer" }}
          >
            ↻ Refresh
          </button>
        </div>
      </div>

      <div style={{ background: col.surface, borderRadius: 10, border: `1px solid ${col.border}`, overflow: "hidden" }}>
        {loading && !logs && (
          <div style={{ padding: 32, textAlign: "center", color: col.muted }}>Loading…</div>
        )}
        {logs && logs.length === 0 && (
          <div style={{ padding: 32, textAlign: "center", color: col.muted }}>
            No activity yet. Start a task on the device to see agent actions here.
          </div>
        )}
        {logs && logs.map(log => (
          <AgentLogRow key={log.id} log={log} />
        ))}
      </div>
    </div>
  );
}
