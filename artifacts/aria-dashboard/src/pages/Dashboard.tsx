import { useState } from "react";

// ── Migration data (mirrors migration.md) ────────────────────────────────────

const PHASES = [
  {
    id: 0,
    title: "Environment + Baseline Build",
    status: "done",
    items: [
      "Firebase Studio setup — dual-mode build (native-only / hybrid)",
      "settings.gradle, build.gradle, app/build.gradle updated",
      "local.properties + .gitignore created",
      ".idx/dev.nix provisions JDK17, SDK35, NDK r27.1, arm64-v8a emulator",
    ],
  },
  {
    id: 1,
    title: "Promote Compose as Launcher",
    status: "done",
    items: [
      "ComposeMainActivity promoted — MAIN + LAUNCHER intent moved",
      "MainActivity stripped to stub (no launcher intent)",
      "MainApplication cleaned — SoLoader removed, ReactApplication gone",
      "ExpoModulesPackageList.kt empty stub created",
    ],
  },
  {
    id: 2,
    title: "Fill SettingsScreen.kt Gaps",
    status: "written",
    items: [
      "794-line implementation written",
      "Model config, quantization chips, GPU layers, temperature presets",
      "Permissions section — Accessibility + Notifications + Screen Capture",
      "Clear Memory + Reset Agent with confirmation dialogs",
      "Needs emulator verification",
    ],
  },
  {
    id: 3,
    title: "Fill ActivityScreen.kt Gaps",
    status: "written",
    items: [
      "530-line implementation written",
      "Step timeline, action log, reward history",
      "Filter tabs (All / Actions / Rewards / Errors)",
      "Needs emulator verification",
    ],
  },
  {
    id: 4,
    title: "Fill ControlScreen.kt Gaps",
    status: "written",
    items: [
      "824-line implementation written",
      "Agent start/stop, target app picker, task input",
      "Live status panel, game mode toggle, gesture log",
      "Needs emulator verification",
    ],
  },
  {
    id: 5,
    title: "ChatScreen.kt",
    status: "written",
    items: [
      "491-line implementation written",
      "Token streaming via AgentEventBus flow",
      "Message history with timestamps",
      "Needs emulator verification",
    ],
  },
  {
    id: 6,
    title: "TrainScreen.kt",
    status: "written",
    items: [
      "545-line implementation written",
      "LoRA training trigger, policy version tracker",
      "Adam optimizer metrics, reward curve placeholder",
      "Needs emulator verification",
    ],
  },
  {
    id: 7,
    title: "LabelerScreen.kt",
    status: "written",
    items: [
      "641-line implementation written",
      "Screen element labeler with bounding-box overlay",
      "Label store CRUD, export to JSON",
      "Needs emulator verification",
    ],
  },
  {
    id: 8,
    title: "Delete RN Layer",
    status: "pending",
    items: [
      "All 8 screens must be [x] verified first",
      "Delete bridge/, app/(tabs)/, components/, hooks/",
      "Delete index.js, metro.config.js, babel.config.js, app.json",
      "Delete MainActivity.kt stub",
    ],
  },
  {
    id: 9,
    title: "Strip Build System",
    status: "pending",
    items: [
      "Remove apply plugin: com.facebook.react",
      "Remove react-android + hermes-android deps",
      "Remove all RN/Expo include from settings.gradle",
      "Version bump to 2, assemble release, tag v2.0-native",
    ],
  },
];

const SCREENS = [
  { name: "DashboardScreen.kt", lines: 476, status: "done", phase: "Phase 1" },
  { name: "ComposeMainActivity.kt", lines: 54, status: "done", phase: "Phase 1" },
  { name: "ARIATheme.kt", lines: 139, status: "done", phase: "Phase 1" },
  { name: "ARIAComposeApp.kt", lines: 186, status: "written", phase: "Phase 1" },
  { name: "AgentViewModel.kt", lines: 1095, status: "written", phase: "Phase 1" },
  { name: "SettingsScreen.kt", lines: 794, status: "written", phase: "Phase 2" },
  { name: "ActivityScreen.kt", lines: 530, status: "written", phase: "Phase 3" },
  { name: "ControlScreen.kt", lines: 824, status: "written", phase: "Phase 4" },
  { name: "ChatScreen.kt", lines: 491, status: "written", phase: "Phase 5" },
  { name: "TrainScreen.kt", lines: 545, status: "written", phase: "Phase 6" },
  { name: "LabelerScreen.kt", lines: 641, status: "written", phase: "Phase 7" },
  { name: "ModulesScreen.kt", lines: 456, status: "written", phase: "Phase 11" },
];

const STATS = [
  { label: "Total Kotlin Lines", value: "5,231" },
  { label: "Compose Screens", value: "9" },
  { label: "Phases Complete", value: "2 / 10" },
  { label: "Screens Written", value: "12" },
  { label: "Screens Verified", value: "3" },
  { label: "NDK Version", value: "r27.1" },
];

// ── Status helpers ────────────────────────────────────────────────────────────

function statusColor(status: string) {
  if (status === "done")    return "text-emerald-400";
  if (status === "written") return "text-blue-400";
  return "text-zinc-500";
}

function statusBg(status: string) {
  if (status === "done")    return "bg-emerald-400/10 border-emerald-500/30 text-emerald-400";
  if (status === "written") return "bg-blue-400/10 border-blue-500/30 text-blue-400";
  return "bg-zinc-700/20 border-zinc-600/30 text-zinc-500";
}

function statusLabel(status: string) {
  if (status === "done")    return "✓ DONE";
  if (status === "written") return "~ WRITTEN";
  return "○ PENDING";
}

function phaseIcon(status: string) {
  if (status === "done")    return "✓";
  if (status === "written") return "~";
  return "○";
}

// ── Component ─────────────────────────────────────────────────────────────────

export default function Dashboard() {
  const [activeTab, setActiveTab] = useState<"phases" | "screens" | "info">("phases");
  const [expandedPhase, setExpandedPhase] = useState<number | null>(null);

  const totalLines = SCREENS.reduce((s, sc) => s + sc.lines, 0);
  const doneScreens = SCREENS.filter(s => s.status === "done").length;
  const writtenScreens = SCREENS.filter(s => s.status !== "pending").length;

  return (
    <div className="min-h-screen bg-[#0A0E1A] text-[#E8EAF6] font-sans">
      {/* ── Header ── */}
      <header className="border-b border-[#1E2940] px-6 py-4 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-blue-500/20 border border-blue-500/40 flex items-center justify-center">
            <span className="text-blue-400 text-sm font-bold">A</span>
          </div>
          <div>
            <h1 className="text-sm font-bold tracking-widest text-blue-400">ARIA AGENT</h1>
            <p className="text-xs text-zinc-500">Kotlin Migration Dashboard</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-xs px-2 py-1 rounded-full bg-amber-500/10 border border-amber-500/30 text-amber-400">
            Migration in Progress
          </span>
        </div>
      </header>

      {/* ── Stats row ── */}
      <div className="grid grid-cols-3 sm:grid-cols-6 gap-px bg-[#1E2940] border-b border-[#1E2940]">
        {STATS.map(s => (
          <div key={s.label} className="bg-[#0A0E1A] px-4 py-3 text-center">
            <p className="text-lg font-bold text-blue-400">{s.value}</p>
            <p className="text-xs text-zinc-500 mt-0.5 leading-tight">{s.label}</p>
          </div>
        ))}
      </div>

      {/* ── Progress bar ── */}
      <div className="px-6 py-4 border-b border-[#1E2940]">
        <div className="flex items-center justify-between mb-1.5">
          <span className="text-xs text-zinc-500">Overall Migration Progress</span>
          <span className="text-xs text-blue-400 font-mono">Phase 2 of 10</span>
        </div>
        <div className="h-1.5 bg-[#1E2940] rounded-full overflow-hidden">
          <div
            className="h-full bg-gradient-to-r from-blue-500 to-cyan-400 rounded-full transition-all"
            style={{ width: "20%" }}
          />
        </div>
        <div className="flex mt-2 gap-1">
          {PHASES.map(p => (
            <div
              key={p.id}
              title={`Phase ${p.id}: ${p.title}`}
              className={`flex-1 h-1 rounded-sm ${
                p.status === "done"    ? "bg-emerald-400" :
                p.status === "written" ? "bg-blue-400" :
                "bg-[#1E2940]"
              }`}
            />
          ))}
        </div>
      </div>

      {/* ── Tabs ── */}
      <div className="flex border-b border-[#1E2940] px-6">
        {(["phases", "screens", "info"] as const).map(tab => (
          <button
            key={tab}
            onClick={() => setActiveTab(tab)}
            className={`px-4 py-3 text-xs font-medium tracking-wider uppercase border-b-2 transition-colors ${
              activeTab === tab
                ? "border-blue-500 text-blue-400"
                : "border-transparent text-zinc-500 hover:text-zinc-300"
            }`}
          >
            {tab}
          </button>
        ))}
      </div>

      {/* ── Tab content ── */}
      <main className="px-6 py-5 max-w-4xl mx-auto">

        {/* Phases tab */}
        {activeTab === "phases" && (
          <div className="space-y-2">
            {PHASES.map(phase => (
              <div
                key={phase.id}
                className="rounded-xl border border-[#1E2940] bg-[#0D1221] overflow-hidden"
              >
                <button
                  onClick={() => setExpandedPhase(expandedPhase === phase.id ? null : phase.id)}
                  className="w-full flex items-center gap-3 px-4 py-3 hover:bg-[#111827] transition-colors text-left"
                >
                  <span className={`font-mono text-sm w-5 text-center ${statusColor(phase.status)}`}>
                    {phaseIcon(phase.status)}
                  </span>
                  <span className="text-xs text-zinc-600 font-mono w-14 shrink-0">Phase {phase.id}</span>
                  <span className="flex-1 text-sm font-medium text-zinc-200">{phase.title}</span>
                  <span className={`text-[10px] px-2 py-0.5 rounded-full border ${statusBg(phase.status)}`}>
                    {statusLabel(phase.status)}
                  </span>
                  <span className={`text-zinc-600 text-xs transition-transform ${expandedPhase === phase.id ? "rotate-180" : ""}`}>
                    ▾
                  </span>
                </button>

                {expandedPhase === phase.id && (
                  <ul className="px-5 pb-3 space-y-1.5 border-t border-[#1E2940]">
                    {phase.items.map((item, i) => (
                      <li key={i} className="flex items-start gap-2 pt-1.5">
                        <span className={`mt-0.5 text-xs ${statusColor(phase.status)}`}>·</span>
                        <span className="text-xs text-zinc-400">{item}</span>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            ))}
          </div>
        )}

        {/* Screens tab */}
        {activeTab === "screens" && (
          <div className="space-y-2">
            <div className="flex items-center gap-4 mb-4 text-xs text-zinc-500">
              <span className="flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full bg-emerald-400 inline-block" />
                {doneScreens} verified on emulator
              </span>
              <span className="flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full bg-blue-400 inline-block" />
                {writtenScreens - doneScreens} written, needs verify
              </span>
              <span className="flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full bg-zinc-600 inline-block" />
                {SCREENS.length - writtenScreens} pending
              </span>
            </div>
            {SCREENS.map(screen => (
              <div
                key={screen.name}
                className="rounded-xl border border-[#1E2940] bg-[#0D1221] flex items-center gap-3 px-4 py-3"
              >
                <span className={`font-mono text-sm w-5 text-center ${statusColor(screen.status)}`}>
                  {phaseIcon(screen.status)}
                </span>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-mono text-zinc-200 truncate">{screen.name}</p>
                  <p className="text-xs text-zinc-600">{screen.phase}</p>
                </div>
                <span className="text-xs text-zinc-500 font-mono w-20 text-right">
                  {screen.lines.toLocaleString()}L
                </span>
                <span className={`text-[10px] px-2 py-0.5 rounded-full border w-24 text-center ${statusBg(screen.status)}`}>
                  {statusLabel(screen.status)}
                </span>
              </div>
            ))}
            <div className="rounded-xl border border-[#1E2940] bg-[#0D1221] flex items-center justify-between px-4 py-3 mt-2">
              <span className="text-xs text-zinc-500 font-mono">Total</span>
              <span className="text-sm font-bold text-blue-400 font-mono">{totalLines.toLocaleString()} lines</span>
            </div>
          </div>
        )}

        {/* Info tab */}
        {activeTab === "info" && (
          <div className="space-y-4">
            <InfoCard title="Target Device">
              <Row label="Device" value="Samsung Galaxy M31" />
              <Row label="SoC" value="Exynos 9611 (8-core, 2×2.3GHz + 6×1.7GHz)" />
              <Row label="GPU" value="Mali-G72 MP3 · Vulkan 1.1" />
              <Row label="RAM" value="6 GB LPDDR4X" />
              <Row label="ABI" value="arm64-v8a only" />
            </InfoCard>

            <InfoCard title="Android Build">
              <Row label="AGP" value="8.8" />
              <Row label="Kotlin" value="2.0.21" />
              <Row label="Compose BOM" value="2024.10.00" />
              <Row label="compileSdk / targetSdk" value="35" />
              <Row label="NDK" value="r27.1 (27.1.12297006)" />
              <Row label="CMake" value="3.22.1+" />
              <Row label="Gradle wrapper" value="8.13" />
            </InfoCard>

            <InfoCard title="AI / Native">
              <Row label="LLM" value="Llama 3.2-1B Instruct @ Q4_K_M" />
              <Row label="Model size" value="~870 MB" />
              <Row label="Context" value="4096 tokens" />
              <Row label="Throughput" value="~10–15 tok/s on M31" />
              <Row label="Native lib" value="llama-jni (System.loadLibrary)" />
              <Row label="OCR" value="ML Kit TextRecognition" />
              <Row label="Object detection" value="EfficientDet-Lite0 INT8 (~4.4 MB)" />
              <Row label="Embeddings" value="MiniLM ONNX (~22 MB)" />
            </InfoCard>

            <InfoCard title="Architecture Rules">
              <Row label="ViewModel bridge" value="AgentViewModel only — screens never call services directly" />
              <Row label="Event bus" value="AgentEventBus (SharedFlow) → AgentViewModel → StateFlow → Compose" />
              <Row label="Deletion rule" value="RN .tsx files locked until Kotlin replacement is [x] on emulator" />
              <Row label="GitHub (monorepo)" value="github → aria-ai.git" />
              <Row label="GitHub (Android)" value="ai-android → Ai-android.git" />
            </InfoCard>

            <InfoCard title="Open Items">
              <ul className="space-y-1.5">
                {[
                  "Open android/ in Firebase Studio — Gradle sync in native-only mode",
                  "Run ./gradlew assembleDebug — verify it compiles",
                  "Boot arm64-v8a emulator, adb-launch ComposeMainActivity",
                  "Emulator-verify all 9 written screens (phases 2–7)",
                  "Push android/ subtree to github.com/TITANICBHAI/Ai-android",
                  "After all screens [x]: run Phase 8 (delete RN layer)",
                  "Phase 9: strip build system, bump versionCode to 2",
                  "Tag commit v2.0-native on real device",
                ].map((item, i) => (
                  <li key={i} className="flex items-start gap-2">
                    <span className="text-amber-500 text-xs mt-0.5">○</span>
                    <span className="text-xs text-zinc-400">{item}</span>
                  </li>
                ))}
              </ul>
            </InfoCard>
          </div>
        )}
      </main>
    </div>
  );
}

function InfoCard({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="rounded-xl border border-[#1E2940] bg-[#0D1221] overflow-hidden">
      <div className="px-4 py-2 border-b border-[#1E2940]">
        <h3 className="text-xs font-medium tracking-widest text-zinc-500 uppercase">{title}</h3>
      </div>
      <div className="px-4 py-3 space-y-2">{children}</div>
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-start gap-3">
      <span className="text-xs text-zinc-600 w-40 shrink-0">{label}</span>
      <span className="text-xs text-zinc-300 font-mono">{value}</span>
    </div>
  );
}
