# ARIA Agent — UI ↔ Backend Connectivity Audit

Generated: 2026-04-05
Scope: all interactive controls across 8 Compose screens AND all backend services.

---

## Legend

| Symbol | Meaning |
|--------|---------|
| ✅ | Fully wired end-to-end |
| ⚠️  | Partially wired — backend exists and runs, but feedback to UI is broken or missing |
| ❌ | Gap — one side exists, the other does not |

---

# Part A — UI exists, tracing backend connection

## Control Screen

| Control | ViewModel call | Real backend | Status | Notes |
|---------|---------------|--------------|--------|-------|
| Goal text field | feeds `startAgent` / `startLearnOnly` | `AgentLoop` | ✅ | |
| Target app field | feeds `startAgent` | `AgentLoop` | ✅ | |
| Learn-only toggle | switches call path | `AgentForegroundService` | ✅ | |
| START AGENT button | `vm.startAgent()` | `AgentLoop.start()` | ✅ | |
| START LEARNING button | `vm.startLearnOnly()` | `AgentForegroundService.startLearnOnly()` | ✅ | |
| PAUSE / RESUME button | `vm.pauseAgent()` / `vm.startAgent()` | `AgentLoop.pause()` | ✅ | |
| STOP button | `vm.stopAgent()` | `AgentLoop.stop()` | ✅ | |
| Load LLM Engine button | `vm.loadModel()` | `LlamaEngine.load()` | ✅ | |
| Task queue goal field | feeds `enqueueTask` | `TaskQueueManager` | ✅ | |
| Task queue app field | feeds `enqueueTask` | `TaskQueueManager` | ✅ | |
| Add to Queue button | `vm.enqueueTask()` | `TaskQueueManager.enqueue()` | ✅ | |
| Clear Queue button | `vm.clearTaskQueue()` | `TaskQueueManager.clear()` | ✅ | |
| Per-task remove button | `vm.removeQueuedTask()` | `TaskQueueManager.remove()` | ✅ | |
| Dismiss chain notification | `vm.dismissChainNotification()` | state only | ✅ | |

## Chat Screen

| Control | ViewModel call | Real backend | Status | Notes |
|---------|---------------|--------------|--------|-------|
| Message text field | feeds `sendChatMessage` | `LlamaEngine.infer()` | ✅ | |
| Send button | `vm.sendChatMessage()` | `LlamaEngine.infer()` | ✅ | Guards against unloaded model |
| Preset prompt chips | `vm.sendChatMessage(preset)` | `LlamaEngine.infer()` | ✅ | |
| Clear chat icon | `vm.clearChat()` | state clear | ✅ | |

## Modules Screen

| Control | ViewModel call | Real backend | Status | Notes |
|---------|---------------|--------------|--------|-------|
| Download — Llama 3.2-1B | `vm.downloadLlmModel()` | `ModelDownloadService` | ⚠️ | Service runs, emits `model_download_progress` events every 2 MB, but ViewModel resets `_llmDownloading` after a fake 2-second delay instead of listening to events. No progress % shown in UI. |
| Download — EfficientDet | `vm.downloadDetectorModel()` | `ObjectDetectorEngine.ensureModel()` | ✅ | Small file, flag reset on coroutine completion |
| GRANT — Screen Capture | `vm.requestScreenCapturePermission()` | `MediaProjectionManager` chooser | ✅ | Fixed this session |
| GRANT — Accessibility | ❌ no button | `Settings.ACTION_ACCESSIBILITY_SETTINGS` deep-link | ❌ | Row shows DENIED but no action |
| OCR status chip | `refreshModuleState()` | — | ⚠️ | `ocrReady` hardcoded `true` — never checks ML Kit init |

## Train Screen

| Control | ViewModel call | Real backend | Status | Notes |
|---------|---------------|--------------|--------|-------|
| Run RL Cycle button | `vm.runRlCycle()` | `LoraTrainer.train()` | ✅ | Real on-device LoRA fine-tuning |
| Auto-schedule RL toggle | `vm.setAutoScheduleRl()` | `ExperienceStore` + `runRlCycle()` | ✅ | Triggers if > 50 untrained successes |
| IRL video picker | → `vm.processIrlVideo()` | `IrlModule.processVideo()` | ✅ | |
| IRL goal / app fields | feed `processIrlVideo` | `IrlModule` | ✅ | |
| Run IRL button | `vm.processIrlVideo()` | `IrlModule.processVideo()` | ✅ | |
| Refresh header button | `vm.refreshLearningStatus()` | `LoraTrainer` + `ExperienceStore` | ✅ | |
| Navigate to Labeler | nav callback | — | ✅ | |

## Labeler Screen

| Control | ViewModel call | Real backend | Status | Notes |
|---------|---------------|--------------|--------|-------|
| Capture Screen button | `vm.captureScreenForLabeling()` | `ScreenCaptureService` + `OcrEngine` | ✅ | |
| Grant Screen Capture button | `onRequestScreenCapture()` | `MediaProjectionManager` | ✅ | Fixed this session |
| Import from Gallery button | gallery launcher → `vm.loadImageFromGallery()` | `OcrEngine.run()` | ✅ | Fixed this session |
| Tap to place pin | `vm.addLabelerPin()` | state | ✅ | |
| Drag pin | `vm.updateLabelerLabel()` | state | ✅ | |
| Auto-detect button | `vm.autoDetectLabelerPins()` | `ObjectDetectorEngine.detectFromPath()` | ✅ | |
| Enrich button | `vm.enrichAllLabelerPins()` | `LlamaEngine.infer()` per pin | ✅ | |
| Save button | `vm.saveLabelerLabels()` | `ObjectLabelStore` | ✅ | |
| Delete label | `vm.deleteLabelerLabel()` | state | ✅ | |
| Dismiss error | `vm.dismissLabelerError()` | state | ✅ | Auto-dismissed via LaunchedEffect |

## Activity Screen

| Control | ViewModel call | Real backend | Status | Notes |
|---------|---------------|--------------|--------|-------|
| Clear Memory button | `vm.clearMemory()` | `ExperienceStore.clearAll()` | ✅ | Confirmation dialog |
| (memory list auto-loads) | `vm.refreshMemoryEntries()` | `ExperienceStore.getRecent(200)` | ✅ | LaunchedEffect on enter |

## Settings Screen

| Control | ViewModel call | Real backend | Status | Notes |
|---------|---------------|--------------|--------|-------|
| Model path field | → `saveConfig` | `ConfigStore` | ✅ | |
| Quantization selector | → `saveConfig` | `ConfigStore` | ✅ | |
| Context window selector | → `saveConfig` | `ConfigStore` | ✅ | |
| GPU layers selector | → `saveConfig` | `ConfigStore` | ✅ | |
| Temperature slider | → `saveConfig` | `ConfigStore` | ✅ | |
| RL enabled switch | → `saveConfig` | `ConfigStore` | ✅ | |
| LoRA adapter path field | → `saveConfig` | `ConfigStore` | ✅ | |
| Save Configuration button | `vm.saveConfig()` | `ConfigStore.save()` + `AgentEventBus` | ✅ | |
| Clear Memory button | `vm.clearMemory()` | `ExperienceStore.clearAll()` | ✅ | |
| Reset Agent button | `vm.resetAgent()` | full state wipe | ✅ | |

## Dashboard Screen

| Control | ViewModel call | Real backend | Status | Notes |
|---------|---------------|--------------|--------|-------|
| Dismiss chain notification | `vm.dismissChainNotification()` | state | ✅ | |
| (all other content is read-only display) | — | — | — | |

---

# Part B — Backend exists, NO UI

These services and functions exist in Kotlin and work correctly, but the user has no button, toggle, or status display to see or control them.

## 1. Unload LLM from RAM

| Item | File | What it does |
|------|------|--------------|
| `LlamaEngine.unload()` | `core/ai/LlamaEngine.kt:103` | Frees the JNI model handle and ~800 MB of RAM |
| `LlamaEngine.loadLora()` | `core/ai/LlamaEngine.kt:156` | Hot-swaps a LoRA adapter into a loaded model |

**Gap:** No button to unload the model when idle (save battery/RAM) or to manually apply a different LoRA adapter. The LoRA adapter path is stored in config but never actually passed to `loadLora()` from the UI — only the `AgentLoop` does it automatically.

---

## 2. Embedding Model (MiniLM) — no status, no download button

| Item | File | What it does |
|------|------|--------------|
| `EmbeddingModelManager.isModelReady()` | `core/memory/EmbeddingModelManager.kt:63` | Checks if the ~23 MB ONNX MiniLM model is on disk |
| `EmbeddingModelManager.isVocabReady()` | `core/memory/EmbeddingModelManager.kt:68` | Checks if the tokenizer vocab file is present |
| `EmbeddingModelManager.download()` | `core/memory/EmbeddingModelManager.kt:88` | Downloads model + vocab from HuggingFace |
| `EmbeddingModelManager.cancelDownload()` | `core/memory/EmbeddingModelManager.kt:205` | Cancels an in-progress download |
| `EmbeddingEngine.isModelAvailable()` | `core/memory/EmbeddingEngine.kt:51` | Combined readiness check |

**Gap:** The Modules screen shows LLM and EfficientDet status, but the embedding model (used for semantic memory search) is completely invisible. If it's not downloaded, memory search silently fails. No card, no download button, no status.

---

## 3. Local Web Server — toggle exists in comment only

| Item | File | What it does |
|------|------|--------------|
| `LocalDeviceServer.start(port)` | `core/monitoring/LocalDeviceServer.kt:56` | Starts an HTTP server on the device (default port 8080) |
| `LocalDeviceServer.stop()` | `core/monitoring/LocalDeviceServer.kt:81` | Stops the server |
| `LocalDeviceServer.serverUrl()` | `core/monitoring/LocalDeviceServer.kt:110` | Returns `http://<device-ip>:8080` |
| `MonitoringPusher.start()` | `core/monitoring/MonitoringPusher.kt:65` | Pushes live agent state to the local server |
| `MonitoringPusher.stop()` | `core/monitoring/MonitoringPusher.kt:84` | Stops the push loop |

**Gap:** `SettingsScreen.kt` has a comment at line 65: `LocalDeviceServer.kt already exists — add a toggle here to start/stop it`. The toggle, the URL display, and the MonitoringPusher start/stop are all missing from the UI.

---

## 4. Learning Scheduler — runs hidden, no status

| Item | File | What it does |
|------|------|--------------|
| `LearningScheduler.start()` | `core/rl/LearningScheduler.kt:57` | Starts an auto-training background loop |
| `LearningScheduler.stop()` | `core/rl/LearningScheduler.kt:64` | Stops it |
| `LearningScheduler.isTrainingRunning()` | `core/rl/LearningScheduler.kt:187` | Whether a training pass is currently running |

**Gap:** The scheduler appears to run inside the RL cycle, but there is no on/off switch in the Train screen separate from the "Run RL Cycle" button, and no indicator showing whether automatic background training is currently running.

---

## 5. Object Label browse and stats — data exists, no viewer

| Item | File | What it does |
|------|------|--------------|
| `ObjectLabelStore.getAllEnriched()` | `core/memory/ObjectLabelStore.kt:212` | Returns all enriched UI element labels |
| `ObjectLabelStore.countEnriched()` | `core/memory/ObjectLabelStore.kt:228` | How many labels have been LLM-enriched |
| `ObjectLabelStore.getByScreen()` | `core/memory/ObjectLabelStore.kt:182` | Labels for a specific screen hash |
| `ObjectLabelStore.clearAll()` | `core/memory/ObjectLabelStore.kt:171` | Wipes all stored UI element labels |
| `ObjectLabelStore.getEnrichedByApp()` | `core/memory/ObjectLabelStore.kt:197` | Labels for a specific app |

**Gap:** The Modules screen shows total label count only. There is no way to browse, search, or delete stored labels outside the Labeler screen (which only works on a live capture). The `clearAll()` has no UI button.

---

## 6. ExperienceStore edge-case count — computed, never shown

| Item | File | What it does |
|------|------|--------------|
| `ExperienceStore.edgeCaseCount()` | `core/memory/ExperienceStore.kt:112` | Count of experiences flagged as edge cases |
| `ExperienceStore.countByResult("failure")` | `core/memory/ExperienceStore.kt:107` | Failure episode count |

**Gap:** The Activity screen shows raw experience tuples, but success/failure/edge-case breakdowns are not displayed. `edgeCaseCount()` is never called by the VM or any screen.

---

## 7. LLM download progress — service emits it, VM ignores it

| Item | File | What it does |
|------|------|--------------|
| `AgentEventBus "model_download_progress"` | `ModelDownloadService.kt:137` | Emits `{percent, downloadedMb, totalMb, speedMbps}` every 2 MB |
| `AgentEventBus "model_download_error"` | `ModelDownloadService.kt:150` | Emits `{error}` on failure |

**Gap:** The ViewModel's `AgentEventBus` collector handles `model_download_complete` (line 374) but has no case for `model_download_progress` or `model_download_error`. The Download button shows a spinner for exactly 2 seconds then goes blank regardless of actual download state. Real progress, speed, and errors are broadcast but silently dropped.

---

# Part C — Gaps (all closed)

| # | Gap | Status | Where fixed |
|---|-----|--------|-------------|
| 1 | LLM download: real progress bar + error handling | ✅ Done | `AgentViewModel.handleLlmDownloadProgress()` + `ModulesScreen` progress bar |
| 2 | Accessibility Service: GRANT button | ✅ Done | `ModulesScreen` `onGrantAccessibility` + `ARIAComposeApp` wires `ACTION_ACCESSIBILITY_SETTINGS` |
| 3 | OCR: real readiness check | ✅ Done | `AgentViewModel.refreshModuleState()` calls `OcrEngine.isAvailable()` |
| 4 | Embedding model: status card + download button | ✅ Done | `ModulesScreen` MiniLM card + `AgentViewModel.downloadEmbeddingModel()` |
| 5 | Unload LLM button | ✅ Done | `ModulesScreen` "Free RAM (Unload)" button + `AgentViewModel.unloadLlmModel()` |
| 6 | Local web server toggle + URL display | ✅ Done | `SettingsScreen` "Web Dashboard" section + toggle + copy-URL button |
| 7 | Object Label browser + clear-all | ✅ Done | `ActivityScreen` Labels tab + `LabelsList` + `LabelRow` + clear dialog |
| 8 | ExperienceStore breakdown (success/fail/edge) | ✅ Done | `ActivityScreen` Memory tab stats bar (Total / Success / Fail / Edge / Untrained) |
