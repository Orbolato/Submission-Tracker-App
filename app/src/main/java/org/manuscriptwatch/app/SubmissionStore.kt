package org.manuscriptwatch.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class StatusEvent(
    val at: Long,
    val from: String,
    val to: String,
    val source: String
)

data class Manuscript(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val journal: String,
    val manuscriptId: String,
    val currentStatus: String = "Not checked",
    val journalUrl: String = "",
    val statusUrl: String = "",
    val cssSelector: String = "",
    val lastChecked: Long = 0L,
    val lastError: String = "",
    val history: List<StatusEvent> = emptyList()
)

data class StatusChange(val manuscript: Manuscript, val before: String, val after: String)

object SubmissionStore {
    private val lock = Any()
    private const val PREFS = "manuscript_watch_data"
    private const val ENTRIES = "entries"
    private const val INTERVAL = "interval_hours"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun intervalHours(context: Context): Long = prefs(context).getLong(INTERVAL, 3L)

    fun setIntervalHours(context: Context, hours: Long) {
        require(hours in listOf(1L, 3L, 6L, 12L, 24L))
        prefs(context).edit().putLong(INTERVAL, hours).apply()
    }

    fun all(context: Context): List<Manuscript> = synchronized(lock) {
        val array = JSONArray(prefs(context).getString(ENTRIES, "[]") ?: "[]")
        (0 until array.length()).mapNotNull { i ->
            runCatching { array.getJSONObject(i).toManuscript() }.getOrNull()
        }
    }

    fun save(context: Context, manuscript: Manuscript) = synchronized(lock) {
        val current = all(context).toMutableList()
        val index = current.indexOfFirst { it.id == manuscript.id }
        if (index >= 0) current[index] = manuscript else current.add(manuscript)
        write(context, current)
    }

    fun delete(context: Context, id: String) = synchronized(lock) {
        write(context, all(context).filterNot { it.id == id })
    }

    fun updateError(context: Context, id: String, error: String) = synchronized(lock) {
        all(context).find { it.id == id }?.let { save(context, it.copy(lastError = error)) }
    }

    fun observe(context: Context, id: String, newStatus: String, source: String): StatusChange? = synchronized(lock) {
        val old = all(context).find { it.id == id } ?: return@synchronized null
        val now = System.currentTimeMillis()
        val status = newStatus.trim().replace(Regex("\\s+"), " ").take(160)
        if (status.isBlank()) return@synchronized null
        val before = old.currentStatus
        val changed = before != status
        val history = if (changed) old.history + StatusEvent(now, before, status, source) else old.history
        val updated = old.copy(
            currentStatus = status, lastChecked = now, lastError = "", history = history.takeLast(250)
        )
        save(context, updated)
        if (changed && before != "Not checked") StatusChange(updated, before, status) else null
    }

    private fun write(context: Context, entries: List<Manuscript>) {
        val json = JSONArray()
        entries.forEach { json.put(it.toJson()) }
        prefs(context).edit().putString(ENTRIES, json.toString()).commit()
    }

    private fun Manuscript.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("journal", journal)
        put("manuscriptId", manuscriptId)
        put("currentStatus", currentStatus)
        put("journalUrl", journalUrl)
        put("statusUrl", statusUrl)
        put("cssSelector", cssSelector)
        put("lastChecked", lastChecked)
        put("lastError", lastError)
        put("history", JSONArray().apply { history.forEach { event ->
            put(JSONObject().apply {
                put("at", event.at); put("from", event.from)
                put("to", event.to); put("source", event.source)
            })
        } })
    }

    private fun JSONObject.toManuscript(): Manuscript {
        val h = optJSONArray("history") ?: JSONArray()
        return Manuscript(
            id = getString("id"),
            title = optString("title"),
            journal = optString("journal"),
            manuscriptId = optString("manuscriptId"),
            currentStatus = optString("currentStatus", "Not checked"),
            journalUrl = optString("journalUrl"),
            statusUrl = optString("statusUrl"),
            cssSelector = optString("cssSelector"),
            lastChecked = optLong("lastChecked"),
            lastError = optString("lastError"),
            history = (0 until h.length()).map { index ->
                h.getJSONObject(index).let {
                    StatusEvent(it.optLong("at"), it.optString("from"), it.optString("to"), it.optString("source"))
                }
            }
        )
    }
}
