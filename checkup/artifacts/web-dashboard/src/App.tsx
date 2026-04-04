import React, { useState, useEffect, useRef } from "react";
import { Overview }     from "./pages/Overview";
import { ActivityLog }  from "./pages/ActivityLog";
import { RLMetrics }    from "./pages/RLMetrics";
import { LoraVersions } from "./pages/LoraVersions";
import { MemoryStore }  from "./pages/MemoryStore";
import { getDeviceUrl, setDeviceUrl, clearDeviceUrl } from "./lib/api";

type Page = "overview" | "activity" | "rl" | "lora" | "memory";

interface NavItem { id: Page; label: string; icon: string; }

const NAV: NavItem[] = [
  { id: "overview",  label: "Overview",     icon: "◈" },
  { id: "activity",  label: "Activity Log", icon: "⊟" },
  { id: "rl",        label: "RL Metrics",   icon: "⊕" },
  { id: "lora",      label: "LoRA",         icon: "⊛" },
  { id: "memory",    label: "Memory",       icon: "⊗" },
];

const col = {
  bg:      "#0a0f1e",
  surface: "#111827",
  border:  "#1e2a3a",
  primary: "#00d4ff",
  green:   "#10b981",
  red:     "#ef4444",
  muted:   "#9ca3af",
  text:    "#e5e7eb",
};

// ─── Device connection widget ──────────────────────────────────────────────────

function DeviceConnect({ onConnected }: { onConnected: () => void }) {
  const [open,       setOpen]       = useState(false);
  const [input,      setInput]      = useState("");
  const [connected,  setConnected]  = useState(() => !!getDeviceUrl());
  const [testing,    setTesting]    = useState(false);
  const [error,      setError]      = useState<string | null>(null);
  const panelRef = useRef<HTMLDivElement>(null);

  const deviceUrl = getDeviceUrl();

  useEffect(() => {
    function onClickOutside(e: MouseEvent) {
      if (panelRef.current && !panelRef.current.contains(e.target as Node)) {
        setOpen(false);
        setError(null);
      }
    }
    if (open) document.addEventListener("mousedown", onClickOutside);
    return () => document.removeEventListener("mousedown", onClickOutside);
  }, [open]);

  async function handleConnect() {
    const raw = input.trim();
    if (!raw) return;
    const url = raw.startsWith("http") ? raw : `http://${raw}`;
    setTesting(true);
    setError(null);
    try {
      const res = await fetch(`${url}/health`, { signal: AbortSignal.timeout(3000) });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      setDeviceUrl(url);
      setConnected(true);
      setOpen(false);
      setInput("");
      onConnected();
    } catch (e: unknown) {
      setError(`Cannot reach device: ${(e as Error).message}`);
    } finally {
      setTesting(false);
    }
  }

  function handleDisconnect() {
    clearDeviceUrl();
    setConnected(false);
    setInput("");
    setOpen(false);
    onConnected();
  }

  return (
    <div ref={panelRef} style={{ position: "relative", marginLeft: "auto" }}>
      <button
        onClick={() => { setOpen(v => !v); setError(null); }}
        style={{
          display: "flex", alignItems: "center", gap: 6,
          padding: "4px 12px", borderRadius: 6, border: `1px solid ${col.border}`,
          background: connected ? "#10b98115" : "transparent",
          color: connected ? col.green : col.muted,
          fontSize: 12, cursor: "pointer", fontFamily: "inherit",
        }}
      >
        <span style={{
          width: 7, height: 7, borderRadius: "50%",
          background: connected ? col.green : col.muted,
          flexShrink: 0,
        }} />
        {connected ? `Live · ${deviceUrl?.replace("http://", "")}` : "Connect device"}
      </button>

      {open && (
        <div style={{
          position: "absolute", right: 0, top: "calc(100% + 8px)", zIndex: 100,
          background: col.surface, border: `1px solid ${col.border}`,
          borderRadius: 10, padding: 16, minWidth: 300,
          boxShadow: "0 8px 32px rgba(0,0,0,0.5)",
        }}>
          <div style={{ fontSize: 12, fontWeight: 700, color: col.text, marginBottom: 10 }}>
            Connect to ARIA device
          </div>
          <div style={{ fontSize: 11, color: col.muted, marginBottom: 12, lineHeight: 1.6 }}>
            Open <strong style={{ color: col.primary }}>ARIA</strong> on your Galaxy M31, then enter
            the device IP shown on the Settings screen. Port 8765 is added automatically.
          </div>

          <div style={{ display: "flex", gap: 6, marginBottom: 8 }}>
            <input
              value={input}
              onChange={e => setInput(e.target.value)}
              onKeyDown={e => e.key === "Enter" && handleConnect()}
              placeholder="192.168.1.42"
              style={{
                flex: 1, padding: "6px 10px", borderRadius: 6,
                border: `1px solid ${error ? col.red : col.border}`,
                background: col.bg, color: col.text, fontSize: 12,
                fontFamily: "inherit", outline: "none",
              }}
            />
            <button
              onClick={handleConnect}
              disabled={testing || !input.trim()}
              style={{
                padding: "6px 14px", borderRadius: 6, border: "none",
                background: col.primary, color: col.bg,
                fontSize: 12, fontWeight: 700, cursor: "pointer",
                opacity: testing || !input.trim() ? 0.5 : 1,
                fontFamily: "inherit",
              }}
            >
              {testing ? "..." : "Connect"}
            </button>
          </div>

          {error && <div style={{ fontSize: 11, color: col.red, marginBottom: 8 }}>{error}</div>}

          {connected && (
            <button
              onClick={handleDisconnect}
              style={{
                width: "100%", padding: "6px 0", borderRadius: 6,
                border: `1px solid ${col.border}`, background: "transparent",
                color: col.muted, fontSize: 11, cursor: "pointer", fontFamily: "inherit",
              }}
            >
              Disconnect from {deviceUrl}
            </button>
          )}

          <div style={{ marginTop: 12, padding: "10px", background: "#00d4ff08", borderRadius: 6, border: `1px solid ${col.border}` }}>
            <div style={{ fontSize: 10, color: col.muted, lineHeight: 1.7 }}>
              <strong style={{ color: col.primary }}>Device server endpoints:</strong><br />
              /aria/status &nbsp; /aria/thermal &nbsp; /aria/rl<br />
              /aria/lora &nbsp; /aria/memory &nbsp; /aria/activity<br />
              /aria/modules
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

// ─── App ──────────────────────────────────────────────────────────────────────

export function App() {
  const [page,      setPage]      = useState<Page>("overview");
  const [refreshKey, setRefreshKey] = useState(0);

  return (
    <div style={{ display: "flex", flexDirection: "column", minHeight: "100vh", background: col.bg }}>
      {/* Top bar */}
      <header
        style={{
          display: "flex", alignItems: "center", gap: 16,
          padding: "0 24px", height: 52,
          background: col.surface, borderBottom: `1px solid ${col.border}`,
          position: "sticky", top: 0, zIndex: 10,
        }}
      >
        <span style={{ fontSize: 16, fontWeight: 800, letterSpacing: 2, color: col.primary }}>ARIA</span>
        <span style={{ color: col.muted, fontSize: 12 }}>Agent Dashboard</span>
        <DeviceConnect onConnected={() => setRefreshKey(k => k + 1)} />
      </header>

      {/* Main layout */}
      <div style={{ display: "flex", flex: 1 }}>
        {/* Sidebar nav */}
        <nav
          style={{
            width: 180, background: col.surface,
            borderRight: `1px solid ${col.border}`,
            padding: "16px 0", flexShrink: 0,
          }}
        >
          {NAV.map(({ id, label, icon }) => {
            const active = page === id;
            return (
              <button
                key={id}
                onClick={() => setPage(id)}
                style={{
                  display: "flex", alignItems: "center", gap: 10,
                  width: "100%", padding: "10px 20px",
                  background: active ? "#00d4ff15" : "transparent",
                  borderLeft: "none", border: "none",
                  borderRight: "none", borderTop: "none", borderBottom: "none",
                  borderLeftWidth: 3, borderLeftStyle: "solid",
                  borderLeftColor: active ? col.primary : "transparent",
                  color: active ? col.primary : col.muted,
                  fontSize: 13, fontWeight: active ? 600 : 400,
                  cursor: "pointer", textAlign: "left",
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
          {page === "overview" && <Overview key={refreshKey} />}
          {page === "activity" && <ActivityLog key={refreshKey} />}
          {page === "rl"       && <RLMetrics key={refreshKey} />}
          {page === "lora"     && <LoraVersions key={refreshKey} />}
          {page === "memory"   && <MemoryStore key={refreshKey} />}
        </main>
      </div>
    </div>
  );
}
