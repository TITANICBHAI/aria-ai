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
- **API framework**: Express 5
- **Database**: PostgreSQL + Drizzle ORM
- **Validation**: Zod (`zod/v4`)
- **API codegen**: Orval (from OpenAPI spec)
- **Mobile**: Expo (React Native, New Architecture enabled)
- **LLM**: Llama 3.2-1B via llama.cpp + JNI
- **OCR**: Google ML Kit
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
│   ├── api-server/             # Express API server
│   └── mobile/                 # React Native UI shell (Phase 1)
│       ├── app/                # Expo Router screens
│       ├── context/            # AgentContext (bridge calls)
│       ├── native-bindings/    # AgentCoreBridge.ts stubs
│       └── components/         # UI components
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

## Key Files

- `artifacts/mobile/native-bindings/AgentCoreBridge.ts` — TurboModule contract + Phase 1 stubs
- `artifacts/mobile/context/AgentContext.tsx` — centralized bridge state
- `artifacts/api-server/src/` — Express routes
- `lib/api-spec/openapi.yaml` — OpenAPI spec
