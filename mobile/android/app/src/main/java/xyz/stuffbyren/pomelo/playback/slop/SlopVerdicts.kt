/*
 * Copyright (C) 2026 SFG545
 *
 * This file is part of Orchard.
 *
 * Orchard is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * Orchard is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Orchard. If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.stuffbyren.pomelo.playback.slop

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SlopVerdicts(context: Context) : SQLiteOpenHelper(context.applicationContext, "slop-verdicts.db", null, 2) {
    private val mutableProbabilities = MutableStateFlow<Map<String, Float>>(emptyMap())
    val probabilities = mutableProbabilities.asStateFlow()
    // True for YouTube "Made with AI", false for a recent unlabelled check.
    private val mutableLabels = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val labels = mutableLabels.asStateFlow()
    private var loaded = false

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE verdicts (id TEXT PRIMARY KEY, probability REAL NOT NULL, seconds REAL NOT NULL, scored_at INTEGER NOT NULL)")
        createLabels(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        if (old < 2) createLabels(db)
    }

    private fun createLabels(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS labels (id TEXT PRIMARY KEY, ai INTEGER NOT NULL, checked_at INTEGER NOT NULL)")
    }

    // Only the service scanner calls load/record, on its single IO coroutine.
    fun load() {
        if (loaded) return
        loaded = true
        mutableProbabilities.value = runCatching {
            readableDatabase.rawQuery("SELECT id,probability FROM verdicts ORDER BY scored_at ASC LIMIT 5000", null).use { rows ->
                buildMap { while (rows.moveToNext()) put(rows.getString(0), rows.getFloat(1)) }
            }
        }.getOrDefault(emptyMap())
        // Uploaders can add the disclosure later, so a "no" expires.
        val fresh = System.currentTimeMillis() / 1000 - UNLABELLED_TTL_SECONDS
        mutableLabels.value = runCatching {
            readableDatabase.rawQuery("SELECT id,ai FROM labels WHERE ai=1 OR checked_at>? ORDER BY checked_at ASC LIMIT $MAX_LABELS",
                arrayOf(fresh.toString())).use { rows ->
                buildMap { while (rows.moveToNext()) put(rows.getString(0), rows.getInt(1) != 0) }
            }
        }.getOrDefault(emptyMap())
    }

    fun recordLabel(id: String, ai: Boolean) {
        val known = mutableLabels.value.toMutableMap().apply {
            remove(id)
            if (size >= MAX_LABELS) remove(keys.first())
            put(id, ai)
        }
        runCatching {
            writableDatabase.insertWithOnConflict("labels", null, ContentValues().apply {
                put("id", id)
                put("ai", if (ai) 1 else 0)
                put("checked_at", System.currentTimeMillis() / 1000)
            }, SQLiteDatabase.CONFLICT_REPLACE)
            writableDatabase.execSQL("DELETE FROM labels WHERE id NOT IN (SELECT id FROM labels ORDER BY checked_at DESC LIMIT $MAX_LABELS)")
        }
        mutableLabels.value = known
    }

    private companion object {
        const val MAX_LABELS = 20000
        const val UNLABELLED_TTL_SECONDS = 7L * 24 * 3600
    }

    fun record(id: String, probability: Float, seconds: Float) {
        if (!probability.isFinite() || probability !in 0f..1f || seconds < 10f) return
        val known = mutableProbabilities.value.toMutableMap().apply {
            remove(id)
            if (size >= 5000) remove(keys.first())
            put(id, probability)
        }
        runCatching {
            writableDatabase.insertWithOnConflict("verdicts", null, ContentValues().apply {
                put("id", id)
                put("probability", probability)
                put("seconds", seconds)
                put("scored_at", System.currentTimeMillis() / 1000)
            }, SQLiteDatabase.CONFLICT_REPLACE)
            writableDatabase.execSQL("DELETE FROM verdicts WHERE id NOT IN (SELECT id FROM verdicts ORDER BY scored_at DESC LIMIT 5000)")
        }
        mutableProbabilities.value = known
    }
}
