package com.shivam.glasslauncher.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.pow

/**
 * Remembers the last launches (key + time) to rank "Siri Suggestions"-style rows. Each launch
 * contributes a weight that halves every [HALF_LIFE_HOURS], so frequent and recent apps rise.
 */
class UsageTracker(private val context: Context, private val scope: CoroutineScope) {

    private val key = stringPreferencesKey("launch_history")

    private val history: StateFlow<List<Pair<String, Long>>> = context.settingsStore.data
        .map { decode(it[key].orEmpty()) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun recordLaunch(appKey: String) {
        scope.launch {
            context.settingsStore.edit { prefs ->
                val list = decode(prefs[key].orEmpty()).toMutableList()
                list += appKey to System.currentTimeMillis()
                prefs[key] = encode(list.takeLast(MAX_EVENTS))
            }
        }
    }

    /** App keys ordered by decayed launch weight. */
    fun ranked(now: Long = System.currentTimeMillis()): List<String> {
        val scores = HashMap<String, Double>()
        for ((k, t) in history.value) {
            val ageHours = (now - t).coerceAtLeast(0) / 3_600_000.0
            scores[k] = (scores[k] ?: 0.0) + 0.5.pow(ageHours / HALF_LIFE_HOURS)
        }
        return scores.entries.sortedByDescending { it.value }.map { it.key }
    }

    fun scoreBoost(appKey: String): Int {
        val now = System.currentTimeMillis()
        var s = 0.0
        for ((k, t) in history.value) if (k == appKey) s += 0.5.pow(((now - t) / 3_600_000.0) / HALF_LIFE_HOURS)
        return (s * 15).toInt().coerceAtMost(90)
    }

    val changes = history

    private fun decode(s: String): List<Pair<String, Long>> =
        s.split('\n').mapNotNull { line ->
            val i = line.lastIndexOf('|')
            if (i <= 0) null else line.substring(i + 1).toLongOrNull()?.let { line.substring(0, i) to it }
        }

    private fun encode(list: List<Pair<String, Long>>) = list.joinToString("\n") { "${it.first}|${it.second}" }

    companion object {
        private const val MAX_EVENTS = 200
        private const val HALF_LIFE_HOURS = 72.0
    }
}
