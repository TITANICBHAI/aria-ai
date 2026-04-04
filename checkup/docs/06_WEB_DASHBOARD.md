# ARIA — Web Dashboard Guide

> Monitor ARIA in real time from any browser on your local network — no cloud, no internet.

---

## What is the Web Dashboard?

The Web Dashboard is a local monitoring interface that runs directly on your phone and serves a live view to any browser on the same Wi-Fi network. It shows the same data as the ARIA app Dashboard screen — agent status, metrics, action log, and module health.

It is useful when you want to:
- Monitor ARIA from your PC while your phone runs a long task.
- Share ARIA's status on a bigger screen.
- Debug issues without looking at the phone.

---

## Getting Started

### 1. Enable the Local Server

1. Open ARIA on your phone.
2. Go to **Settings**.
3. Enable **Local Monitoring Server** (toggle it ON).
4. Note the **Device IP** shown — for example `192.168.1.105`.

### 2. Open the Dashboard in a Browser

On any device on the same Wi-Fi:

```
http://192.168.1.105:8765
```

Use your actual device IP, not this example.

> Use `http://` not `https://` — the local server runs without TLS on LAN.

---

## Dashboard Sections

### Status Bar
- Agent running state (Running / Paused / Idle)
- Uptime
- Current goal text

### Metrics Panel
- Tokens per second (AI inference speed)
- Actions per minute
- Memory usage (RAM)
- Thermal state

### Module Health
Real-time status of each ARIA module:
- LLM Engine
- OCR Engine
- Accessibility Service
- Screen Capture
- Object Detector
- Memory Store
- RL Policy

### Action Log
Live feed of every action ARIA takes, auto-updating every second.

### Memory Snapshot
A read-only view of ARIA's stored experiences and object labels.

---

## API Endpoints

The local server also exposes raw JSON endpoints if you want to build your own tools on top:

| Endpoint | Description |
|---|---|
| `GET /aria/status` | Agent state, uptime, current goal |
| `GET /aria/metrics` | Tokens/sec, actions/min, RAM, thermal |
| `GET /aria/modules` | Health status of all modules |
| `GET /aria/log` | Last 100 action log entries |
| `GET /aria/memory` | Memory store snapshot |
| `GET /aria/snapshot` | Full JSON snapshot (all of the above combined) |

Example:
```
http://192.168.1.105:8765/aria/snapshot
```

---

## ADB Access (Advanced)

If you have Android Debug Bridge (ADB) set up, you can also pull the snapshot file directly without needing Wi-Fi:

```bash
adb pull /data/user/0/com.ariaagent.mobile/files/monitoring/snapshot.json
```

This gives you the same full JSON snapshot as `/aria/snapshot`.

---

## Troubleshooting

**Dashboard not loading:**
- Confirm phone and PC are on the same Wi-Fi network.
- Confirm Local Monitoring Server is enabled in ARIA Settings.
- Use `http://` (not `https://`).
- Try disabling AP isolation on your router.

**Data not updating:**
- The dashboard auto-refreshes every second. If it stops updating, ARIA's local server may have been killed by Android. Toggle the server OFF and ON in Settings to restart it.

**Cannot reach the IP:**
- Your phone's IP may have changed if you reconnected to Wi-Fi. Check the current IP in ARIA Settings.
