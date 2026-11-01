package com.shivam.glasslauncher.search

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shivam.glasslauncher.data.settingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Spotlight's recent searches: the last [MAX] queries sent to Google, newest first, one per wording. */
class SearchHistory(private val context: Context, private val scope: CoroutineScope) {

    // New key: the old list mixed in half-typed app searches ("se", "boo").
    private val key = stringPreferencesKey("search_history_web")

    val recent: StateFlow<List<String>> = context.settingsStore.data
        .map { decode(it[key].orEmpty()) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun record(query: String) {
        val q = query.trim().replace('\n', ' ')
        if (q.length < 2) return
        edit { add(q, it) }
    }

    fun remove(query: String) = edit { list -> list.filterNot { it.equals(query, ignoreCase = true) } }

    fun clear() = edit { emptyList() }

    private fun edit(transform: (List<String>) -> List<String>) {
        scope.launch {
            context.settingsStore.edit { prefs -> prefs[key] = transform(decode(prefs[key].orEmpty())).joinToString("\n") }
        }
    }

    companion object {
        const val MAX = 3

        fun add(query: String, list: List<String>): List<String> =
            (listOf(query) + list.filterNot { it.equals(query, ignoreCase = true) }).take(MAX)

        private fun decode(s: String): List<String> = s.split('\n').filter { it.isNotBlank() }
    }
}
