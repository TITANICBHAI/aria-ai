What's left
So what left now? And does all learning, training, labeling related have access to llm as they need It to understand the context and does RL and other have access to new trained models?
Gap     Severity        Status
llama.cpp NDK native library    🔴 Blocks on-device inference    Code written, needs EAS build + submodule
nativeTrainLora() JNI   🔴 Blocks real LoRA training     stubTrainLora() runs instead
LoRA hot-reload gap     ✅ Fixed   LearningScheduler + runRlCycle() both hot-reload into running LlamaEngine after training
loraAdapterPath not auto-updated        ✅ Fixed   Both LearningScheduler and runRlCycle() now persist to ConfigStore (DataStore). loadModel() reads from ConfigStore. No SharedPreferences for config anymore.
Phase 6 — Game RL       ✅ Complete   GameDetector.kt + GameLoop.kt implemented and wired into AgentLoop
Phase 10/11 — JS thinning / Compose     🟡 Future        As designed (needs EAS + device test before launcher switch)
LLM access by each module — is it correct?

IrlModule ✅ — calls LlamaEngine.infer() directly to reason about screen transitions
LoraTrainer ✅ conceptually correct — it doesn't call the LLM, it trains it by passing a JSONL dataset to llama.cpp's native training API. The distinction: inference = use weights, training = update weights
PolicyNetwork ✅ — its own separate MLP, no LLM needed, REINFORCE is pure math
New adapter → running LlamaEngine ✅ Fixed — both LearningScheduler (automatic/charging) and runRlCycle() (manual trigger) now: 1) persist adapter path to ConfigStore, 2) hot-reload into running LlamaEngine immediately, 3) emit learning_cycle_complete event to JS

Config store unification ✅ Fixed — AgentCoreModule.loadModel(), getConfig(), updateConfig() all use ConfigStore (DataStore) exclusively. SharedPreferences is no longer written to for config; legacy values are migrated on first boot via migrateFromSharedPrefs().
