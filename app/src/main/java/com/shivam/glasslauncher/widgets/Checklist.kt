package com.shivam.glasslauncher.widgets

import org.json.JSONArray
import org.json.JSONObject

data class ChecklistItem(val id: String, val text: String, val done: Boolean = false)

/** Bounded local data: commas, emoji and duplicate task labels round-trip without ambiguity. */
object Checklist {
    const val MAX_ITEMS = 20
    const val MAX_TEXT = 160

    fun decode(raw: String?): List<ChecklistItem> = try {
        val array = JSONArray(raw ?: "[]")
        (0 until minOf(array.length(), MAX_ITEMS)).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id")
            val text = item.optString("text").trim().take(MAX_TEXT)
            if (id.isBlank() || text.isBlank()) null else ChecklistItem(id, text, item.optBoolean("done"))
        }.distinctBy { it.id }
    } catch (_: Exception) { emptyList() }

    fun encode(items: List<ChecklistItem>): String = JSONArray().apply {
        items.take(MAX_ITEMS).forEach { put(JSONObject().put("id", it.id).put("text", it.text.trim().take(MAX_TEXT)).put("done", it.done)) }
    }.toString()
}
