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
    val history: List<StatusEvent> = emptyList(),
    val trackingSources: List<TrackingSource> = emptyList(),
    val editorialMenuUrl: String = "",
    val currentFolder: String = "",
    val reviewProgress: ReviewProgress = ReviewProgress(),
    val reviewHistory: List<ReviewEvent> = emptyList()
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

    /** Update an individual endpoint while selecting the highest-priority known status.
     * Legacy records remain readable because new fields default to empty values.
     */
    fun observeSource(
        context: Context, id: String, endpoint: TrackingSource,
        result: ExtractResult.Found, folder: String = ""
    ): List<StatusChange> = synchronized(lock) {
        val old = all(context).find { it.id == id } ?: return@synchronized emptyList()
        val now = System.currentTimeMillis()
        val sources = TrackingSources.effective(old).toMutableList()
        val updated = endpoint.copy(
            lastStatus = result.status.take(160), lastChecked = now,
            folder = folder.ifBlank { endpoint.folder }, lastError = "", lastAttempted = now
        )
        sources.removeAll { it.url == updated.url }
        sources.add(updated)
        val preferred = TrackingSources.preferred(sources)
        val newStatus = preferred?.lastStatus ?: old.currentStatus
        val statusChanged = newStatus != old.currentStatus
        val newProgress = result.reviewProgress?.let { old.reviewProgress.merge(it) } ?: old.reviewProgress
        val metricsChanged = newProgress != old.reviewProgress
        val events = if (statusChanged) old.history + StatusEvent(
            now, old.currentStatus, newStatus, preferred?.kind?.title ?: "Journal webpage"
        ) else old.history
        val reviewEvents = if (metricsChanged) old.reviewHistory + ReviewEvent(now, endpoint.kind.title, newProgress)
            else old.reviewHistory
        val changed = old.copy(
            currentStatus = newStatus, trackingSources = sources,
            lastChecked = now, lastError = "", history = events.takeLast(250),
            currentFolder = folder.ifBlank { old.currentFolder },
            reviewProgress = newProgress, reviewHistory = reviewEvents.takeLast(250)
        )
        save(context, changed)
        buildList {
            if (statusChanged && old.currentStatus != "Not checked")
                add(StatusChange(changed, old.currentStatus, newStatus))
            // Notify only when previously known counters change, not on first discovery.
            if (metricsChanged && !old.reviewProgress.isEmpty()) {
                fun changedCount(label: String, before: Int?, after: Int?) {
                    if (before != null && after != null && before != after)
                        add(StatusChange(changed, "$label: $before", "$label: $after"))
                }
                changedCount("Reviewers invited", old.reviewProgress.invited, newProgress.invited)
                if (old.reviewProgress.invited != null && newProgress.invited != null &&
                    old.reviewProgress.invitedAtLeast != newProgress.invitedAtLeast &&
                    old.reviewProgress.invited == newProgress.invited) {
                    add(StatusChange(changed,
                        "Invited: ${old.reviewProgress.invitedLabel()}",
                        "Invited: ${newProgress.invitedLabel()}"))
                }
                changedCount("Reviewers accepted", old.reviewProgress.accepted, newProgress.accepted)
                changedCount("Reports received", old.reviewProgress.reports, newProgress.reports)
            }
        }
    }

    /** Store a check error on the exact tracking source, preserving statuses from other sources. */
    fun recordSourceError(context: Context, id: String, source: TrackingSource, reason: String) = synchronized(lock) {
        all(context).find { it.id == id }?.let { item ->
            val sources = TrackingSources.effective(item).toMutableList()
            val previous = sources.firstOrNull { it.url == source.url } ?: source
            sources.removeAll { it.url == source.url }
            sources.add(previous.copy(lastError = reason.take(240), lastAttempted = System.currentTimeMillis()))
            save(context, item.copy(trackingSources = sources))
        }
    }

    /** Record publisher-provided reviewer numbers even when no editorial status is identifiable. */
    fun observeReviewProgress(context: Context, id: String, source: TrackingSource,
                              partial: ReviewProgress) = synchronized(lock) {
        if (partial.isEmpty()) return@synchronized
        all(context).find { it.id == id }?.let { item ->
            val progress = item.reviewProgress.merge(partial)
            if (progress != item.reviewProgress) {
                val now = System.currentTimeMillis()
                save(context, item.copy(reviewProgress = progress,
                    reviewHistory = (item.reviewHistory + ReviewEvent(now, source.kind.title, progress)).takeLast(250)))
            }
        }
    }

    fun addTrackingSource(context: Context, id: String, url: String) = synchronized(lock) {
        require(TrackingSources.isSupportedUrl(url))
        all(context).find { it.id == id }?.let { item ->
            val clean = JournalPageClassifier.canonicalStatusUrl(url)
            val existing = TrackingSources.effective(item).toMutableList()
            if (existing.none { it.url == clean })
                existing.add(TrackingSource(TrackingKind.fromUrl(clean), clean))
            save(context, item.copy(
                statusUrl = item.statusUrl.ifBlank { clean },
                trackingSources = existing
            ))
        }
    }

    fun removeTrackingSource(context: Context, id: String, url: String) = synchronized(lock) {
        all(context).find { it.id == id }?.let { item ->
            val remaining = TrackingSources.effective(item).filterNot { it.url == url }
            save(context, item.copy(
                statusUrl = if (item.statusUrl == url) remaining.firstOrNull()?.url.orEmpty() else item.statusUrl,
                trackingSources = remaining
            ))
        }
    }

    fun setEditorialMenu(context: Context, id: String, url: String) = synchronized(lock) {
        all(context).find { it.id == id }?.let { item ->
            // Earlier builds mistakenly saved the Author Main Menu as a
            // manuscript tracking source. Remove only this exact menu URL.
            val remaining = TrackingSources.effective(item).filterNot { it.url == url }
            save(context, item.copy(
                editorialMenuUrl = url,
                statusUrl = if (item.statusUrl == url) remaining.firstOrNull()?.url.orEmpty()
                    else item.statusUrl,
                trackingSources = remaining
            ))
        }
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
        put("editorialMenuUrl", editorialMenuUrl)
        put("currentFolder", currentFolder)
        put("trackingSources", JSONArray().apply { trackingSources.forEach { source ->
            put(JSONObject().apply {
                put("kind", source.kind.name); put("url", source.url)
                put("lastStatus", source.lastStatus); put("lastChecked", source.lastChecked)
                put("folder", source.folder)
                put("lastError", source.lastError)
                put("lastAttempted", source.lastAttempted)
            })
        } })
        put("reviewProgress", reviewProgress.toJson())
        put("reviewHistory", JSONArray().apply { reviewHistory.forEach { event ->
            put(JSONObject().apply {
                put("at", event.at); put("source", event.source)
                put("progress", event.progress.toJson())
            })
        } })
        put("history", JSONArray().apply { history.forEach { event ->
            put(JSONObject().apply {
                put("at", event.at); put("from", event.from)
                put("to", event.to); put("source", event.source)
            })
        } })
    }

    private fun ReviewProgress.toJson(): JSONObject = JSONObject().apply {
        if (invited != null) { put("invited", invited); put("invitedAtLeast", invitedAtLeast) }
        if (accepted != null) put("accepted", accepted)
        if (reports != null) put("reports", reports)
    }

    private fun JSONObject.toProgress(): ReviewProgress = ReviewProgress(
        invited = if (has("invited") && !isNull("invited")) optInt("invited") else null,
        invitedAtLeast = optBoolean("invitedAtLeast", false),
        accepted = if (has("accepted") && !isNull("accepted")) optInt("accepted") else null,
        reports = if (has("reports") && !isNull("reports")) optInt("reports") else null
    )

    private fun JSONObject.toManuscript(): Manuscript {
        val h = optJSONArray("history") ?: JSONArray()
        val links = optJSONArray("trackingSources") ?: JSONArray()
        val reviewEvents = optJSONArray("reviewHistory") ?: JSONArray()
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
            editorialMenuUrl = optString("editorialMenuUrl"),
            currentFolder = optString("currentFolder"),
            trackingSources = (0 until links.length()).mapNotNull { index ->
                runCatching {
                    val entry = links.getJSONObject(index)
                    val url = entry.getString("url")
                    TrackingSource(
                        TrackingKind.entries.firstOrNull { it.name == entry.optString("kind") }
                            ?: TrackingKind.fromUrl(url), url,
                        entry.optString("lastStatus"), entry.optLong("lastChecked"),
                        entry.optString("folder"),
                        entry.optString("lastError"), entry.optLong("lastAttempted")
                    )
                }.getOrNull()
            },
            reviewProgress = optJSONObject("reviewProgress")?.toProgress() ?: ReviewProgress(),
            reviewHistory = (0 until reviewEvents.length()).mapNotNull { index ->
                runCatching {
                    val event = reviewEvents.getJSONObject(index)
                    ReviewEvent(event.optLong("at"), event.optString("source"),
                        event.optJSONObject("progress")?.toProgress() ?: ReviewProgress())
                }.getOrNull()
            },
            history = (0 until h.length()).map { index ->
                h.getJSONObject(index).let {
                    StatusEvent(it.optLong("at"), it.optString("from"), it.optString("to"), it.optString("source"))
                }
            }
        )
    }
}
