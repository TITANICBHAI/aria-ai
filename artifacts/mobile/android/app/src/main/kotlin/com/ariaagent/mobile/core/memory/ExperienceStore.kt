package com.ariaagent.mobile.core.memory

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues
import java.util.UUID

/**
 * ExperienceStore — SQLite database for the agent's learning pipeline.
 *
 * Every observe→reason→act loop produces one ExperienceTuple.
 * These tuples are the raw material for:
 *   - RL training (REINFORCE policy gradient update)
 *   - LoRA fine-tuning (successful traces → adapter weights)
 *   - Edge case memory (failures that eventually succeeded)
 *
 * The learning loop ONLY runs during idle + charging.
 * Collection happens continuously during active use.
 *
 * Phase: 4 (Data Collection)
 */
class ExperienceStore private constructor(context: Context) :
    SQLiteOpenHelper(context, "aria_experience.db", null, 1) {

    companion object {
        @Volatile
        private var instance: ExperienceStore? = null

        fun getInstance(context: Context): ExperienceStore =
            instance ?: synchronized(this) {
                instance ?: ExperienceStore(context.applicationContext).also { instance = it }
            }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS experience (
                id TEXT PRIMARY KEY,
                timestamp INTEGER NOT NULL,
                app_package TEXT NOT NULL,
                task_type TEXT NOT NULL,
                screen_summary TEXT NOT NULL,
                action_json TEXT NOT NULL,
                result TEXT NOT NULL,
                reward REAL NOT NULL,
                is_edge_case INTEGER DEFAULT 0,
                edge_case_notes TEXT,
                session_id TEXT,
                used_for_training INTEGER DEFAULT 0
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS edge_cases (
                id TEXT PRIMARY KEY,
                screen_pattern TEXT NOT NULL,
                resolution TEXT NOT NULL,
                app_package TEXT NOT NULL,
                recall_count INTEGER DEFAULT 0,
                last_seen INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_app ON experience(app_package)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_result ON experience(result)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_timestamp ON experience(timestamp DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    data class ExperienceTuple(
        val id: String = UUID.randomUUID().toString(),
        val timestamp: Long = System.currentTimeMillis(),
        val appPackage: String,
        val taskType: String,
        val screenSummary: String,
        val actionJson: String,
        val result: String,
        val reward: Double,
        val isEdgeCase: Boolean = false,
        val edgeCaseNotes: String? = null,
        val sessionId: String? = null
    )

    fun save(tuple: ExperienceTuple) {
        writableDatabase.insert("experience", null, ContentValues().apply {
            put("id", tuple.id)
            put("timestamp", tuple.timestamp)
            put("app_package", tuple.appPackage)
            put("task_type", tuple.taskType)
            put("screen_summary", tuple.screenSummary)
            put("action_json", tuple.actionJson)
            put("result", tuple.result)
            put("reward", tuple.reward)
            put("is_edge_case", if (tuple.isEdgeCase) 1 else 0)
            put("edge_case_notes", tuple.edgeCaseNotes)
            put("session_id", tuple.sessionId)
        })
    }

    fun count(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM experience", null).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }

    fun countByResult(result: String): Int =
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM experience WHERE result=?", arrayOf(result)
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun edgeCaseCount(): Int =
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM experience WHERE is_edge_case=1", null
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun getRecent(limit: Int): List<ExperienceTuple> {
        val list = mutableListOf<ExperienceTuple>()
        readableDatabase.rawQuery(
            "SELECT * FROM experience ORDER BY timestamp DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                list.add(ExperienceTuple(
                    id = c.getString(c.getColumnIndexOrThrow("id")),
                    timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp")),
                    appPackage = c.getString(c.getColumnIndexOrThrow("app_package")),
                    taskType = c.getString(c.getColumnIndexOrThrow("task_type")),
                    screenSummary = c.getString(c.getColumnIndexOrThrow("screen_summary")),
                    actionJson = c.getString(c.getColumnIndexOrThrow("action_json")),
                    result = c.getString(c.getColumnIndexOrThrow("result")),
                    reward = c.getDouble(c.getColumnIndexOrThrow("reward")),
                    isEdgeCase = c.getInt(c.getColumnIndexOrThrow("is_edge_case")) == 1
                ))
            }
        }
        return list
    }

    fun getUntrainedSuccesses(limit: Int = 100): List<ExperienceTuple> {
        val list = mutableListOf<ExperienceTuple>()
        readableDatabase.rawQuery(
            "SELECT * FROM experience WHERE result='success' AND used_for_training=0 LIMIT ?",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                list.add(ExperienceTuple(
                    id = c.getString(c.getColumnIndexOrThrow("id")),
                    timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp")),
                    appPackage = c.getString(c.getColumnIndexOrThrow("app_package")),
                    taskType = c.getString(c.getColumnIndexOrThrow("task_type")),
                    screenSummary = c.getString(c.getColumnIndexOrThrow("screen_summary")),
                    actionJson = c.getString(c.getColumnIndexOrThrow("action_json")),
                    result = c.getString(c.getColumnIndexOrThrow("result")),
                    reward = c.getDouble(c.getColumnIndexOrThrow("reward"))
                ))
            }
        }
        return list
    }

    fun markAsTrained(ids: List<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            ids.forEach { id ->
                db.execSQL("UPDATE experience SET used_for_training=1 WHERE id=?", arrayOf(id))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun clearAll() {
        writableDatabase.execSQL("DELETE FROM experience")
        writableDatabase.execSQL("DELETE FROM edge_cases")
    }
}
