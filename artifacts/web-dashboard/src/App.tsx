import React, { useState } from "react";
import { Overview }     from "./pages/Overview";
import { ActivityLog }  from "./pages/ActivityLog";
import { RLMetrics }    from "./pages/RLMetrics";
import { LoraVersions } from "./pages/LoraVersions";
import { MemoryStore }  from "./pages/MemoryStore";

type Page = "overview" | "activity" | "rl" | "lora" | "memory";

interface NavItem {
  id: Page;
  label: string;
  icon: string;
}

const NAV: NavItem[] = [
  { id: "overview",  label: "Overview",    icon: "◈" },
  { id: "activity",  label: "Activity Log", icon: "⊟" },
  { id: "rl",        label: "RL Metrics",  icon: "⊕" },
  { id: "lora",      label: "LoRA",        icon: "⊛" },
  { id: "memory",    label: "Memory",      icon: "⊗" },
];

const col = {
  bg:      "#0a0f1e",
  surface: "#111827",
  border:  "#1e2a3a",
  primary: "#00d4ff",
  muted:   "#9ca3af",
  text:    "#e5e7eb",
};

export function App() {
  const [page, setPage] = useState<Page>("overview");

  return (
    <div style={{ display: "flex", flexDirection: "column", minHeight: "100vh", background: col.bg }}>
      {/* Top bar */}
      <header
        style={{
          display: "flex",
          alignItems: "center",
          gap: 16,
          padding: "0 24px",
          height: 52,
          background: col.surface,
          borderBottom: `1px solid ${col.border}`,
          position: "sticky",
          top: 0,
          zIndex: 10,
        }}
      >
        <span style={{ fontSize: 16, fontWeight: 800, letterSpacing: 2, color: col.primary }}>ARIA</span>
        <span style={{ color: col.muted, fontSize: 12 }}>Agent Dashboard</span>
        <span style={{ marginLeft: "auto", fontSize: 11, color: col.muted }}>
          No cloud. Ever.
        </span>
      </header>

      {/* Main layout */}
      <div style={{ display: "flex", flex: 1 }}>
        {/* Sidebar nav */}
        <nav
          style={{
            width: 180,
            background: col.surface,
            borderRight: `1px solid ${col.border}`,
            padding: "16px 0",
            flexShrink: 0,
          }}
        >
          {NAV.map(({ id, label, icon }) => {
            const active = page === id;
            return (
              <button
                key={id}
                onClick={() => setPage(id)}
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: 10,
                  width: "100%",
                  padding: "10px 20px",
                  background: active ? "#00d4ff15" : "transparent",
                  borderLeft: `3px solid ${active ? col.primary : "transparent"}`,
                  border: "none",
                  borderRight: "none",
                  borderTop: "none",
                  borderBottom: "none",
                  borderLeftWidth: 3,
                  borderLeftStyle: "solid",
                  borderLeftColor: active ? col.primary : "transparent",
                  color: active ? col.primary : col.muted,
                  fontSize: 13,
                  fontWeight: active ? 600 : 400,
                  cursor: "pointer",
                  textAlign: "left",
                  transition: "background 0.15s, color 0.15s",
                }}
              >
                <span style={{ fontSize: 16 }}>{icon}</span>
                {label}
              </button>
            );
          })}

          <div style={{ padding: "20px 20px 0", borderTop: `1px solid ${col.border}`, marginTop: 16 }}>
            <div style={{ fontSize: 11, color: col.muted, lineHeight: 1.6 }}>
              Samsung Galaxy M31<br />
              Exynos 9611 · 6GB<br />
              Llama 3.2-1B Q4_K_M
            </div>
          </div>
        </nav>

        {/* Page content */}
        <main style={{ flex: 1, overflowY: "auto", minWidth: 0 }}>
          {page === "overview" && <Overview />}
          {page === "activity" && <ActivityLog />}
          {page === "rl"       && <RLMetrics />}
          {page === "lora"     && <LoraVersions />}
          {page === "memory"   && <MemoryStore />}
        </main>
      </div>
    </div>
  );
}
