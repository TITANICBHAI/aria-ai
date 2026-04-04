# ARIA — Troubleshooting Guide

> Common problems and how to fix them.

---

## ARIA is Not Tapping Correctly

**Symptom:** ARIA taps the wrong area of the screen.

**Fix 1:** Use the Object Labeller to label the correct element (see `04_TEACH_AND_TRAIN.md`).

**Fix 2:** Verify the screen resolution is set correctly on your device.
- Samsung Galaxy M31: Settings → Display → Screen resolution → set to **HD+** or **FHD+**. ARIA is calibrated for FHD+ (2340×1080).

**Fix 3:** Check that no screen overlay (blue light filter, screen dimmer) is interfering with screen capture.

---

## ARIA Stops After a Few Actions

**Symptom:** ARIA starts but stops mid-task without completing the goal.

**Cause 1 — Thermal throttling:** Check the thermal badge in the Dashboard. If it shows **Hot** or **Throttled**, let the phone cool down for 5 minutes, then try again.

**Cause 2 — Memory pressure:** Close background apps to free RAM. ARIA needs at least 2 GB free.

**Cause 3 — Accessibility service was killed:** Go to Settings → Accessibility → ARIA Agent and confirm it is still ON. Some Android skins kill accessibility services aggressively.

**Fix for Samsung One UI (Galaxy M31):**
- Settings → Apps → ARIA → Battery → select **Unrestricted**.
- Settings → Device care → Battery → Background app limits → make sure ARIA is not on the sleeping list.

---

## AI Model Not Loaded / LLM Shows Error

**Symptom:** Modules screen shows LLM Engine as **Error** or **Not Loaded**.

**Step 1:** Go to **Settings → Download Model** and check if the model downloaded fully. The file should be ~870 MB. If it is smaller, the download was interrupted — tap Download again.

**Step 2:** Check storage. ARIA needs ~1 GB free for the model plus ~200 MB working space.

**Step 3:** If the model file exists but still shows error, tap **Reload Model** in Settings.

---

## Accessibility Service Keeps Getting Disabled

**Symptom:** ARIA loses accessibility permission after a day or after reboot.

**Cause:** Samsung One UI aggressively kills accessibility services to save battery.

**Fix:**
1. Settings → Apps → ARIA → Battery → **Unrestricted**.
2. Settings → Device care → Memory → Never sleeping → Add ARIA.
3. Settings → General management → Reset → Reset all settings — only if the above does not work.

---

## Screen Capture Not Working

**Symptom:** OCR shows no text or vision module shows error.

**Fix:** The MediaProjection permission needs to be re-granted after a reboot on some Android versions.

1. Stop ARIA.
2. Reopen ARIA.
3. It will ask for screen capture permission again — tap **Start now**.

---

## ARIA Is Very Slow (1–2 tokens/second)

**Symptom:** ARIA takes 30+ seconds to think about each action.

**Cause:** CPU thermal throttling or too many background processes.

**Fix 1:** Close all background apps.
**Fix 2:** Let the phone cool down (if thermal indicator is red).
**Fix 3:** Settings → GPU layers → increase to 32 (all layers on GPU = faster inference).

**Expected speed on Galaxy M31:** 8–15 tokens/second at normal temperature with GPU layers = 32.

---

## Web Dashboard Not Connecting

**Symptom:** Typing the device IP into a browser shows nothing.

**Step 1:** Make sure your PC and phone are on the **same Wi-Fi network**.
**Step 2:** Open ARIA Settings and confirm the local server is enabled (toggle **Local Monitoring Server** on).
**Step 3:** Use the exact IP shown in ARIA Settings — `http://<ip>:8765`. Make sure to use `http://` not `https://`.
**Step 4:** Check if your router has AP isolation enabled (blocks device-to-device LAN traffic). Disable it or use a different network.

---

## App Crashes on Launch

**Step 1:** Clear app data — Settings → Apps → ARIA → Storage → Clear data.  
> This resets all settings but does not delete the downloaded AI model.

**Step 2:** Reinstall the APK.

**Step 3:** Ensure Android version is 11 or higher. Settings → About phone → Android version.

---

## Reporting a Bug

Open the **Activity** screen, tap the **Export Logs** button, and share the generated file. This log includes all actions, errors, and module states needed to diagnose the issue.
