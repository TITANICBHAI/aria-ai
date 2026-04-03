package com.ariaagent.mobile.core.agent

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * AppSkillRegistry — SQLite-backed per-app agent skill store.
 *
 * Tracks ARIA's accumulated knowledge and performance for every Android app it interacts with.
 * After every task completion (success or failure) AgentLoop calls [recordTaskOutcome].
 *
 * Two roles:
 *  1. **Prompt injection** — [getPromptHint] returns a concise one-liner injected into every
 *     LLM prompt as an [APP KNOWLEDGE] block. This gives the model app-specific context
 *     (success rate, known elements, past goals) before it reasons about the current screen.
 *  2. **Monitoring** — exposed to JS via AgentCoreModule bridge methods so the Modules screen
 *     can display per-app skill levels and task history.
 *
 * Schema (aria_app_skills.db / app_skills):
 *   app_package       TEXT PRIMARY KEY
 *   app_name          TEXT             — human-readable label (from a11y tree or package suffix)
 *   task_success      INTEGER          — total successful tasks on this app
 *   task_failure      INTEGER          — total failed tasks
 *   total_steps       INTEGER          — cumulative agent steps across all tasks
 *   learned_elements  TEXT             — JSON array: top-10 most-used element names (by frequency)
 *   task_templates    TEXT             — JSON array: last 5 successful goal strings
 *   prompt_hint       TEXT             — auto-generated compact line for LLM injection
 *   last_seen         INTEGER          — unix millis of last task
 *
 * Phase: 15 — App Skill Registry & Task Chaining.
 */
class AppSkillRegistry private constructor(context: Context) {

    companion object {
        private const val TAG      = "AppSkillRegistry"
        private const val DB_NAME  = "aria_app_skills.db"
        private const val DB_VER   = 1
        private const val TABLE    = "app_skills"

        @Volatile private var instance: AppSkillRegistry? = null

        fun getInstance(context: Context): AppSkillRegistry =
            instance ?: synchronized(this) {
                instance ?: AppSkillRegistry(context.applicationContext).also { instance = it }
            }
    }

    // ─── Data model ───────────────────────────────────────────────────────────

    data class AppSkill(
        val appPackage: String,
        val appName: String,
        val taskSuccess: Int,
        val taskFailure: Int,
        val totalSteps: Int,
        val learnedElements: List<String>,
        val taskTemplates: List<String>,
        val promptHint: String,
        val lastSeen: Long,
    ) {
        val successRate: Float
            get() = if (taskSuccess + taskFailure == 0) 0f
                    else taskSuccess.toFloat() / (taskSuccess + taskFailure)

        val avgStepsPerTask: Float
            get() = if (taskSuccess == 0) 0f
                    else totalSteps.toFloat() / taskSuccess
    }

    // ─── DB init ──────────────────────────────────────────────────────────────

    private val db: SQLiteDatabase = object : SQLiteOpenHelper(
        context, DB_NAME, null, DB_VER
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE (
                    app_package       TEXT    PRIMARY KEY,
                    app_name          TEXT    NOT NULL DEFAULT '',
                    task_success      INTEGER NOT NULL DEFAULT 0,
                    task_failure      INTEGER NOT NULL DEFAULT 0,
                    total_steps       INTEGER NOT NULL DEFAULT 0,
                    learned_elements  TEXT    NOT NULL DEFAULT '[]',
                    task_templates    TEXT    NOT NULL DEFAULT '[]',
                    prompt_hint       TEXT    NOT NULL DEFAULT '',
                    last_seen         INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) = Unit
    }.writableDatabase

    // ─── Read ─────────────────────────────────────────────────────────────────

    fun get(appPackage: String): AppSkill? {
        val c = db.rawQuery("SELECT * FROM $TABLE WHERE app_package = ?", arrayOf(appPackage))
        return c.use { if (it.moveToFirst()) fromCursor(it) else null }
    }

    fun getAll(): List<AppSkill> {
        val c = db.rawQuery("SELECT * FROM $TABLE ORDER BY last_seen DESC", null)
        return c.use { cur ->
            val list = mutableListOf<AppSkill>()
            while (cur.moveToNext()) list.add(fromCursor(cur))
            list
        }
    }

    fun count(): Int {
        val c = db.rawQuery("SELECT COUNT(*) FROM $TABLE", null)
        return c.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    /**
     * Returns a compact one-liner describing what ARIA knows about this app.
     * Empty string if no task data exists yet — PromptBuilder skips the block when empty.
     */
    fun getPromptHint(appPackage: String): String = get(appPackage)?.promptHint ?: ""

    // ─── Write ────────────────────────────────────────────────────────────────

    /**
     * Record the outcome of a completed task.
     *
     * Called by AgentLoop immediately after [ProgressPersistence.logTaskEnd].
     *
     * @param appPackage       Foreground app package during the task
     * @param appName          Human-readable app name (from a11y tree; may be empty)
     * @param goal             The goal string the user issued
     * @param succeeded        Whether the task reached "Done" status
     * @param stepsTaken       Agent steps used
     * @param elementsTouched  Node/element names the agent acted on (for frequency tracking)
     */
    fun recordTaskOutcome(
        appPackage: String,
        appName: String,
        goal: String,
        succeeded: Boolean,
        stepsTaken: Int,
        elementsTouched: List<String> = emptyList(),
    ) {
        if (appPackage.isBlank()) return

        val existing = get(appPackage)
        val newSuccess = (existing?.taskSuccess ?: 0) + if (succeeded) 1 else 0
        val newFailure = (existing?.taskFailure ?: 0) + if (!succeeded) 1 else 0
        val newSteps   = (existing?.totalSteps  ?: 0) + stepsTaken

        // Merge element frequency — count occurrences, keep top 10
        val freq = (existing?.learnedElements ?: emptyList())
            .associateWith { 1 }
            .toMutableMap<String, Int>()
        elementsTouched.filter { it.isNotBlank() }.forEach { e ->
            freq[e] = (freq[e] ?: 0) + 1
        }
        val topElements = freq.entries
            .sortedByDescending { it.value }
            .take(10)
            .map { it.key }

        // Keep last 5 successful goal strings as task templates (deduped, newest first)
        val templates = (existing?.taskTemplates ?: emptyList()).toMutableList()
        if (succeeded && goal.isNotBlank()) {
            templates.removeAll { it == goal }
            templates.add(0, goal)
            while (templates.size > 5) templates.removeLastOrNull()
        }

        val resolvedName = appName.ifBlank { appPackage.substringAfterLast('.') }

        val hint = buildPromptHint(
            appName    = resolvedName,
            success    = newSuccess,
            failure    = newFailure,
            avgSteps   = if (newSuccess > 0) newSteps.toFloat() / newSuccess else 0f,
            topElements = topElements,
            templates  = templates,
        )

        db.execSQL(
            """
            INSERT OR REPLACE INTO $TABLE
              (app_package, app_name, task_success, task_failure, total_steps,
               learned_elements, task_templates, prompt_hint, last_seen)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf(
                appPackage, resolvedName,
                newSuccess, newFailure, newSteps,
                JSONArray(topElements).toString(),
                JSONArray(templates).toString(),
                hint,
                System.currentTimeMillis()
            )
        )
        Log.i(TAG, "Recorded outcome for $appPackage — success=$succeeded, hint='$hint'")
    }

    fun clear() {
        db.execSQL("DELETE FROM $TABLE")
        Log.i(TAG, "AppSkillRegistry cleared")
    }

    // ─── Prompt hint ──────────────────────────────────────────────────────────

    private fun buildPromptHint(
        appName: String,
        success: Int,
        failure: Int,
        avgSteps: Float,
        topElements: List<String>,
        templates: List<String>,
    ): String {
        if (success + failure == 0) return ""
        val rate = if (success + failure > 0) (success * 100 / (success + failure)) else 0
        return buildString {
            append("App: $appName | Tasks: ${success + failure} | Success: $rate%")
            if (avgSteps > 0f) append(" | Avg ${String.format("%.1f", avgSteps)} steps/task")
            if (topElements.isNotEmpty()) append(" | Elements: ${topElements.take(5).joinToString(", ")}")
            if (templates.isNotEmpty()) {
                append(" | Past goals: ${templates.take(3).joinToString(" / ") { "\"$it\"" }}")
            }
        }
    }

    // ─── Cursor ───────────────────────────────────────────────────────────────

    private fun fromCursor(c: Cursor): AppSkill {
        fun str(col: String)  = c.getString(c.getColumnIndexOrThrow(col)) ?: ""
        fun int_(col: String) = c.getInt(c.getColumnIndexOrThrow(col))
        fun long_(col: String)= c.getLong(c.getColumnIndexOrThrow(col))
        fun jsonList(col: String): List<String> = runCatching {
            val arr = JSONArray(str(col))
            (0 until arr.length()).map { arr.getString(it) }
        }.getOrDefault(emptyList())

        return AppSkill(
            appPackage      = str("app_package"),
            appName         = str("app_name"),
            taskSuccess     = int_("task_success"),
            taskFailure     = int_("task_failure"),
            totalSteps      = int_("total_steps"),
            learnedElements = jsonList("learned_elements"),
            taskTemplates   = jsonList("task_templates"),
            promptHint      = str("prompt_hint"),
            lastSeen        = long_("last_seen"),
        )
    }
}
