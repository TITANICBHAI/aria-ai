What's left
So what left now? And does all learning, training, labeling related have access to llm as they need It to understand the context and does RL and other have access to new trained models?
Gap	Severity	Status
llama.cpp NDK native library	🔴 Blocks on-device inference	Code written, needs EAS build + submodule
nativeTrainLora() JNI	🔴 Blocks real LoRA training	stubTrainLora() runs instead
LoRA hot-reload gap	🟠 Bug	Adapter saved to disk, but never loaded into the running LlamaEngine after training
loraAdapterPath not auto-updated	🟠 Bug	SharedPreferences never updated after new adapter trained — next loadModel() won't use it
Phase 6 — Game RL	🟡 Feature	Not started
Phase 10/11 — JS thinning / Compose	🟡 Future	As designed
LLM access by each module — is it correct?

IrlModule ✅ — calls LlamaEngine.infer() directly to reason about screen transitions
LoraTrainer ✅ conceptually correct — it doesn't call the LLM, it trains it by passing a JSONL dataset to llama.cpp's native training API. The distinction: inference = use weights, training = update weights
PolicyNetwork ✅ — its own separate MLP, no LLM needed, REINFORCE is pure math
New adapter → running LlamaEngine ❌ Gap — adapter file saved but never auto-loaded back
Let me fix the two hot-reload bugs right now:

Now fix the runTrainingCycle to auto-persist the adapter path and hot-reload into LlamaEngine:

Remove the now-unused deprecated import that crept in:

Now let me verify the JS side properly handles the learning_cycle_complete event to show the new adapter version:

The JS side doesn't handle the training completion event at all. Let me check the full AgentContext and dashboard screen:

The context polls only when the agent is running — training events never trigger a UI refresh. I need to add proper event listeners to AgentContext for learning_cycle_complete and thermal_status_changed:

Now update AgentContext to listen for the three native events and react to them properly:

Now add the new state and event subscription logic to the Provider body:
