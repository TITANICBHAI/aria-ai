# ARIA — How to Teach and Train

> This guide explains how to improve ARIA's performance over time through teaching, labelling, and on-device training.

---

## Two Ways to Teach ARIA

1. **Object Labelling** — Teach ARIA to recognise specific UI elements by name.
2. **Reinforcement Learning (RL)** — Let ARIA learn from success and failure over many attempts.

---

## Method 1 — Object Labeller

The Object Labeller lets you point at something on screen and give it a name. ARIA stores this in its memory and will recognise it in future sessions.

**When to use:**
- ARIA keeps tapping the wrong button in a specific app.
- A game element is not being identified correctly.
- You want ARIA to always call something by a specific name.

**How to use:**
1. Open the app or game you want to teach ARIA about.
2. In ARIA, go to **Control screen → Object Labeler** (tap the tag icon).
3. ARIA will overlay a grid on your screen.
4. Tap the element you want to label.
5. Type the name you want ARIA to call it (e.g. "Play button", "Health bar", "Send message").
6. Tap **Save Label**.

ARIA immediately stores this label in its memory. The next time it sees that element, it will use the name you gave it.

**Viewing saved labels:**
Activity screen → Memory Browser → filter by **Labels**.

**Deleting a label:**
Activity screen → Memory Browser → tap the label → tap **Delete**.

---

## Method 2 — Reinforcement Learning (RL)

ARIA learns from outcomes. Every time it takes an action, it records whether that action led to progress toward the goal. Over many sessions, it learns which actions work and which do not.

### How RL Works in ARIA

1. ARIA takes an action (e.g. taps a button).
2. It observes the result (did the screen change in the right direction?).
3. It assigns a reward score to the action.
4. Periodically, the PolicyNetwork updates based on accumulated rewards.

This happens automatically. You do not need to do anything — ARIA learns just by being used.

### Accelerating Learning — IRL (Inverse Reinforcement Learning)

IRL lets ARIA learn from watching *you* perform a task.

**How to use IRL:**
1. Switch to **Learn Only** mode.
2. Perform the task you want ARIA to learn (e.g. book a cab, order food).
3. ARIA silently records your actions and the screen states.
4. Switch back to **General** mode.
5. Next time you give ARIA the same goal, it will use what it learned from watching you.

---

## Manual Feedback — Thumbs Up / Down

You can give ARIA explicit feedback on any action it takes.

1. After ARIA completes a task, go to **Activity → Action Log**.
2. Tap any action in the log.
3. Tap **thumbs up** (correct) or **thumbs down** (wrong).

This direct feedback is the fastest way to correct ARIA when it makes a mistake.

---

## LoRA Training (Advanced)

LoRA (Low-Rank Adaptation) allows ARIA to fine-tune its AI model on your device using the experiences it has collected.

> **Note:** LoRA training is compute-intensive. Only run it when your phone is plugged in and cool.

**How to trigger LoRA training:**
1. Go to **Settings → Training**.
2. Tap **Start LoRA Training**.
3. ARIA will use collected experience data to update a small adapter file (~10–50 MB).
4. Once complete, the adapter is loaded automatically on top of the base model.

**What improves:**
- ARIA's responses become more tailored to your apps and habits.
- Action selection accuracy improves for tasks you use often.

**The base model (~870 MB) is never changed.** Only the small adapter file is updated. You can always reset to default by deleting the adapter in Settings.

---

## Resetting Training Data

If ARIA has learned bad habits, you can reset:

**Reset RL data only:**
Settings → Training → **Reset RL Memory** — clears reward history but keeps object labels.

**Reset object labels only:**
Activity → Memory Browser → **Clear Labels**.

**Full reset:**
Settings → Training → **Factory Reset Training** — clears everything and starts fresh.

---

## Tips for Better Training

- Use **Learn Only** mode consistently for a week on tasks you want ARIA to master.
- Give thumbs down feedback immediately when ARIA does something wrong — do not let it continue on a wrong path.
- Run LoRA training once every few days if you are actively teaching ARIA.
- Stick to the same goals — ARIA learns patterns across repetitions.
- The more varied your goals, the more general ARIA becomes. The more focused your goals, the more specialised and accurate ARIA becomes for those specific tasks.
