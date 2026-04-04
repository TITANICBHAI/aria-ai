# Workspace

## Overview

pnpm workspace monorepo — polyglot Android AI agent with Kotlin brain + React Native JS UI shell.
JS is **temporary UI only**. Kotlin owns all logic permanently.

## Architecture

### Layer 1 — Core Brain (Kotlin, permanent)
- `android/core/ai/` — llama.cpp JNI bindings, Llama 3.2-1B Q4_K_M runner
- `android/core/ocr/` — ML Kit text recognition
- `android/core/rl/` — custom reinforcement learning module
- `android/core/memory/` — SQLite + file-based embeddings

### Layer 2 — System Control (Kotlin, permanent)
- `android/system/accessibility/` — AccessibilityService (gesture injection)
- `android/system/screen/` — MediaProjection screen capture
- `android/system/actions/` — tap, swipe, intent dispatch
- JS **never** calls these directly — only via TurboModule bridge

### Layer 3 — Interface (JS → temporary shell)
- `artifacts/mobile/` — React Native (Expo) UI
- `artifacts/mobile/native-bindings/AgentCoreBridge.ts` — TurboModule contract (stubs in Phase 1)
- `artifacts/mobile/context/AgentContext.tsx` — all bridge calls centralized here
- Phase 2: JS thins out. Phase 3: full Kotlin + Jetpack Compose

### Bridge
- `android/bridge/turbo/` — TurboModule JSI bindings (Kotlin)
- `android/bridge/dto/` — data contracts
- Uses New Architecture (JSI, not legacy bridge)

## Target Device

Samsung Galaxy M31 — Exynos 9611 · Mali-G72 MP3 · 6GB LPDDR4X
- Model: Llama 3.2-1B Instruct @ Q4_K_M (~870MB, ~10-15 tok/s)
- Context window: 4096 tokens (practical limit for M31)
- OCR: ML Kit (512×512 downsampled captures)

## Phase Plan

| Phase | Status | Description |
|-------|--------|-------------|
| 1 | Now | New Architecture · JS UI + Kotlin brain · TurboModules |
| 2 | Future | JS becomes thin wrapper · More logic → Kotlin |
| 3 | Future | Full Kotlin · Jetpack Compose replaces JS UI |

## Stack

- **Monorepo tool**: pnpm workspaces
- **Node.js version**: 24
- **Package manager**: pnpm
- **TypeScript version**: 5.9
- **Mobile**: Expo (React Native, New Architecture enabled)
- **LLM**: Llama 3.2-1B via llama.cpp + JNI
- **OCR**: Google ML Kit
- **Object detection**: MediaPipe EfficientDet-Lite0 INT8 (Phase 13)
- **RL**: Custom on-device module
- **Memory**: SQLite + file-based embeddings

## Directory Structure

```
root/
├── android/                    # Full Kotlin app (OWNERSHIP HERE)
│   ├── core/
│   │   ├── ai/                 # llama.cpp bindings, model runner
│   │   ├── ocr/                # ML Kit wrapper
│   │   ├── rl/                 # reinforcement learning
│   │   └── memory/             # SQLite, embeddings
│   ├── system/
│   │   ├── accessibility/      # AccessibilityService
│   │   ├── screen/             # MediaProjection
│   │   └── actions/            # tap, swipe, intents
│   ├── bridge/
│   │   ├── turbo/              # TurboModules (JSI)
│   │   └── dto/                # data contracts
│   └── ui-native/              # future Jetpack Compose
│
├── artifacts/
│   ├── mobile/                 # React Native UI shell (Phase 1)
│   │   ├── app/                # Expo Router screens
│   │   ├── context/            # AgentContext (bridge calls)
│   │   ├── native-bindings/    # AgentCoreBridge.ts stubs
│   │   └── components/         # UI components
│   └── web-dashboard/          # Local Vite+React monitoring UI
│
├── models/
│   ├── llama/                  # GGUF model files
│   └── adapters/               # LoRA weights
│
└── shared/
    └── schemas/                # contracts between JS and Kotlin
```

## Screens (Phase 1 JS UI)

- **Dashboard** (`/`) — agent status, metrics, module health
- **Control** (`/control`) — start/stop/pause agent, goal input, presets
- **Activity** (`/logs`) — action log + memory store browser
- **Modules** (`/modules`) — per-module status with details and bridge info
- **Settings** (`/settings`) — model config, RL settings, architecture info

## Phase 16 — Local Monitoring (No Cloud)

Device serves live data directly over LAN — no external server, no cloud.

- `android/core/monitoring/LocalDeviceServer.kt` — embedded HTTP server on port 8765 (java.net.ServerSocket, zero deps)
- `android/core/monitoring/LocalSnapshotStore.kt` — volatile in-memory snapshot of all agent state
- `android/core/monitoring/MonitoringPusher.kt` — updates LocalSnapshotStore on AgentEventBus events (≤1/3s)

Dashboard connects to `http://{device-LAN-IP}:8765/aria/{endpoint}`. Bridge exposes `getLocalServerUrl()` + `getDeviceIp()` to show address in Settings.

Snapshot file also written to `{filesDir}/monitoring/snapshot.json` atomically for ADB pull.

## Native Build Status

- `llama.cpp` (shallow clone, ~160MB) is at `artifacts/mobile/android/app/src/main/cpp/llama.cpp/`
- All JNI API calls in `llama_jni.cpp` updated to current llama.cpp API:
  - `llama_model_load_from_file` (replaces deprecated `llama_load_model_from_file`)
  - `llama_memory_clear(llama_get_memory(ctx), true)` (replaces `llama_kv_cache_clear`)
  - `llama_vocab_is_eog` / `llama_model_get_vocab` (replaces `llama_token_is_eog(model, tok)`)
  - `llama_tokenize(vocab, ...)` buffer form (replaces convenience overload)
  - `llama_token_to_piece(vocab, ...)` (replaces model-based form)
  - `llama_adapter_lora_init` / `llama_set_adapters_lora` / `llama_adapter_lora_free` (replaces old lora API)
  - `llama_model_free` (replaces deprecated `llama_free_model`)
- `LLAMA_HAS_TRAINING` is NOT defined — `nativeTrainLora()` returns false → Kotlin falls to `stubTrainLora()`
- CMakeLists.txt include paths: `${LLAMA_DIR}/include`, `${LLAMA_DIR}`, `${LLAMA_DIR}/common`, `${LLAMA_DIR}/ggml/include`
- Build target: arm64-v8a only (Exynos 9611 / Galaxy M31)

## Agent Preferences

- **Auto-push to GitHub**: After correcting any error sent by the user, always push the fix to GitHub (`github` remote → `TITANICBHAI/aria-ai`).

## Key Files

- `artifacts/mobile/native-bindings/AgentCoreBridge.ts` — TurboModule contract + Phase 1 stubs
- `artifacts/mobile/context/AgentContext.tsx` — centralized bridge state
- `artifacts/mobile/android/app/src/main/cpp/llama_jni.cpp` — JNI: nativeInfer, nativeLoadModel, nativeLoadLora, nativeTrainLora
- `artifacts/web-dashboard/src/lib/api.ts` — local mock data (replace with `http://device-ip:8765` fetch when on LAN)
