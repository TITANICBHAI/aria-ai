# ARIA — First-Time Setup Guide

> Target device: Samsung Galaxy M31 · Android 11+  
> Estimated setup time: 5–10 minutes

---

## Step 1 — Install the APK

1. Download the latest ARIA `.apk` from the releases page or EAS build link.
2. On your phone go to **Settings → Apps → Special app access → Install unknown apps**.
3. Allow your browser or file manager to install unknown apps.
4. Open the downloaded `.apk` and tap **Install**.

---

## Step 2 — Grant Required Permissions

When you open ARIA for the first time, it will ask for several permissions. **All of them are required** for ARIA to function.

| Permission | Why it is needed |
|---|---|
| **Accessibility Service** | Lets ARIA read the screen and perform taps/swipes |
| **Display over other apps** | Lets ARIA show its overlay while you use other apps |
| **Screen recording / MediaProjection** | Lets ARIA capture what is on screen for OCR and vision |
| **Storage** | Lets ARIA save the AI model file to your device |
| **Notifications** | Lets ARIA show its status in the notification bar |

**How to grant Accessibility:**
1. Tap the **Enable Accessibility** button on the setup screen.
2. You will be taken to **Settings → Accessibility → Installed services**.
3. Find **ARIA Agent** and toggle it **ON**.
4. Tap **Allow** on the confirmation dialog.
5. Return to the ARIA app — the status indicator should turn green.

---

## Step 3 — Download the AI Model

ARIA's brain is a local AI model (~870 MB). It never leaves your device.

1. Open the **Settings** screen in ARIA (bottom-right tab).
2. Tap **Download Model**.
3. Wait for the download to complete — this takes 2–5 minutes on Wi-Fi.
4. Once downloaded the status shows **Model: Ready**.

> **Tip:** Download over Wi-Fi. The model is ~870 MB.

---

## Step 4 — Verify All Modules Are Green

Go to the **Modules** screen. You should see:

- LLM Engine — **Ready**
- OCR Engine — **Ready**
- Accessibility — **Connected**
- Screen Capture — **Ready**
- Memory Store — **Ready**

If any module shows **Error**, see the Troubleshooting guide.

---

## Step 5 — Run Your First Task

1. Go to the **Control** screen.
2. Type a simple goal, for example: `Open the clock app and set a timer for 1 minute`.
3. Tap **Start**.
4. Watch ARIA read the screen and act on it.

That is all. ARIA is now set up and running.

---

## What Happens in the Background

- ARIA runs as a **foreground service** — it stays active even when you switch apps.
- The notification bar shows ARIA's current status.
- No data is ever sent to any server. Everything runs on your device.

---

## Next Steps

- Read `02_HOW_TO_OPERATE.md` to understand all the controls.
- Read `04_TEACH_AND_TRAIN.md` to teach ARIA your specific apps.
