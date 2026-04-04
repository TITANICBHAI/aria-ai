# ARIA — How to Operate

> This guide explains every screen, button, and mode in the ARIA app.

---

## The Four Screens

### Dashboard (Home)

The first screen you see. It shows live status at a glance.

| Indicator | What it means |
|---|---|
| **Agent Status** | Running / Paused / Idle / Error |
| **LLM** | Whether the AI model is loaded and responding |
| **OCR** | Whether text recognition is active |
| **Actions/min** | How many screen actions ARIA is taking per minute |
| **Memory items** | How many things ARIA has learned and stored |
| **Thermal** | CPU temperature (Cool / Warm / Hot / Throttled) |

---

### Control Screen

This is where you command ARIA.

**Goal input box** — Type what you want ARIA to do in plain English.  
Examples:
- `Open YouTube and play the top trending video`
- `Reply to the last WhatsApp message with "I'll call you back"`
- `Open my email and summarize the unread messages`

**Start / Stop / Pause buttons:**
- **Start** — Begins executing the current goal.
- **Pause** — Freezes ARIA mid-task. It will resume from the same point when you tap Resume.
- **Stop** — Ends the current task and clears the goal.

**Mode selector:**
- **General** — ARIA operates any app on your phone.
- **Gaming** — Optimized for games. Uses faster screenshot analysis and coordinate-based tapping since games have no accessibility tree.
- **Learn Only** — ARIA watches but does not act. It only observes and builds memory. Use this to teach ARIA a new workflow without risk.

**Presets** — Saved goals you use often. Tap the bookmark icon to save a goal as a preset.

---

### Activity Screen (Logs)

A live feed of everything ARIA is doing.

- **Action log** — Every tap, swipe, and type ARIA performs, with a timestamp.
- **Memory browser** — Browse what ARIA has stored from past sessions.
- **Filter bar** — Filter by action type (Tap, Swipe, Type, Think, Learn).

---

### Modules Screen

Shows the health of every internal component.

Tap any module card to see its details:
- Last activity time
- Error messages if any
- Bridge status (whether JS can talk to Kotlin for that module)

---

### Settings Screen

Configure ARIA's behaviour:

| Setting | What it does |
|---|---|
| **Context window** | How much conversation history the AI holds (default 4096 tokens) |
| **Max response tokens** | How long ARIA's thinking step can be (default 512) |
| **Threads** | CPU threads for inference (default 4 — matches Exynos big cores) |
| **GPU layers** | How many model layers run on the GPU (default 32 = all layers) |
| **Learning rate** | How fast ARIA updates from reinforcement feedback |
| **Reward shaping** | Whether the LLM helps score actions during RL |
| **Device IP** | Shows your phone's LAN IP for the Web Dashboard |

---

## Starting and Stopping ARIA Safely

**To start:**
1. Go to Control screen.
2. Enter a goal.
3. Tap Start.

**To stop mid-task:**
- Tap **Stop** in the Control screen at any time.
- Or pull down the notification and tap **Stop ARIA**.

**To pause without losing progress:**
- Tap **Pause** — ARIA freezes but remembers where it was.
- Tap **Resume** to continue.

**Emergency stop:**
- Pull down the notification bar and tap **Stop ARIA**.
- This works even if the app UI is not visible.

---

## Thermal Management

ARIA monitors CPU temperature automatically.

| State | What ARIA does |
|---|---|
| **Cool** (< 37°C) | Full speed — all features active |
| **Warm** (37–42°C) | Slight slowdown in screenshot rate |
| **Hot** (42–47°C) | Reduces inference threads, slows action loop |
| **Throttled** (> 47°C) | Pauses training, reduces to 1 thread |

You will see the thermal badge change colour in the Dashboard. ARIA never crashes from heat — it slows down gracefully.

---

## Web Dashboard (Remote Monitoring)

If you are on the same Wi-Fi network as your phone, you can monitor ARIA from a browser.

1. Open ARIA **Settings** and note the **Device IP** shown.
2. On your PC/laptop browser, go to `http://<device-ip>:8765`.
3. You will see live metrics, the action log, and module status — identical to the app Dashboard.

No internet required. This is a direct LAN connection to your phone.
