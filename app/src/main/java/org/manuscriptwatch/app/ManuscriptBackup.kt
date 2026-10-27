package org.manuscriptwatch.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Portable, intentionally UNENCRYPTED user-initiated backup.
 * Contains only manuscript_watch_data / entries and the polling interval.
 * It does not access ORCID credentials, Android Keystore, or WebView cookies.
 */
object ManuscriptBackup {
    const val MAX_BYTES = 2_000_000
    private const val FORMAT = "org.manuscriptwatch.backup"
    private const val VERSION = 1
    private const val PREFS = "manuscript_watch_data"
    private const val ENTRIES = "entries"
    private const val INTERVAL = "interval_hours"
    private val INTERVALS = setOf(1L, 3L, 6L, 12L, 24L)

    data class Snapshot(
        val entriesJson: String,
        val manuscriptCount: Int,
        val intervalHours: Long
    )

    fun encode(entriesJson: String, intervalHours: Long): String {
        val entries = JSONArray(entriesJson)
        validateEntries(entries)
        require(intervalHours in INTERVALS) { "Unsupported monitoring interval." }
        val result = JSONObject()
            .put("format", FORMAT)
            .put("schemaVersion", VERSION)
            .put("exportedAt", System.currentTimeMillis())
            .put("intervalHours", intervalHours)
            .put("manuscripts", entries)
            .toString(2)
        require(result.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) {
            "Backup is too large to export safely."
        }
        return result
    }

    fun decode(text: String): Snapshot {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) {
            "Backup exceeds the 2 MB safety limit."
        }
        val root = JSONObject(text)
        require(root.optString("format") == FORMAT) {
            "This is not a Manuscript Watch backup."
        }
        require(root.optInt("schemaVersion", -1) == VERSION) {
            "Unsupported backup format version."
        }
        require(root.has("intervalHours")) { "Monitoring interval is missing." }
        val interval = root.getLong("intervalHours")
        require(interval in INTERVALS) { "Invalid monitoring interval." }
        val entries = root.getJSONArray("manuscripts")
        validateEntries(entries)
        return Snapshot(entries.toString(), entries.length(), interval)
    }

    private fun validateEntries(entries: JSONArray) {
        require(entries.length() <= 500) { "Too many manuscripts in backup." }
        val ids = mutableSetOf<String>()
        for (index in 0 until entries.length()) {
            val manuscript = entries.getJSONObject(index)
            val id = requiredString(manuscript, "id", 128)
            require(ids.add(id)) { "Duplicate manuscript in backup." }
            requiredString(manuscript, "title", 500)
            requiredString(manuscript, "journal", 300)
            requiredString(manuscript, "manuscriptId", 200)

            listOf("journalUrl", "statusUrl", "editorialMenuUrl").forEach { field ->
                optionalUrl(manuscript, field)
            }
            optionalString(manuscript, "currentStatus", 200)
            optionalString(manuscript, "lastError", 500)
            optionalString(manuscript, "currentFolder", 200)
            optionalString(manuscript, "cssSelector", 1000)
            optionalNumber(manuscript, "lastChecked")
            arrayOfObjects(manuscript, "trackingSources", 24) { source ->
                optionalString(source, "kind", 80)
                optionalUrl(source, "url", required = true)
                optionalString(source, "lastStatus", 200)
                optionalString(source, "folder", 200)
                optionalString(source, "lastError", 500)
                optionalNumber(source, "lastChecked")
                optionalNumber(source, "lastAttempted")
            }
            arrayOfObjects(manuscript, "history", 250) { event ->
                optionalNumber(event, "at")
                optionalString(event, "from", 200)
                optionalString(event, "to", 200)
                optionalString(event, "source", 100)
            }
            manuscript.optJSONObject("reviewProgress")?.let { validateProgress(it) }
            arrayOfObjects(manuscript, "reviewHistory", 250) { event ->
                optionalNumber(event, "at")
                optionalString(event, "source", 100)
                event.optJSONObject("progress")?.let { validateProgress(it) }
            }
        }
    }

    private fun validateProgress(obj: JSONObject) {
        listOf("invited", "accepted", "reports").forEach { key ->
            if (obj.has(key) && !obj.isNull(key)) {
                require(obj.get(key) is Number && obj.getInt(key) in 0..10000) {
                    "Invalid peer-review count."
                }
            }
        }
        if (obj.has("invitedAtLeast")) require(obj.get("invitedAtLeast") is Boolean) {
            "Invalid reviewer-count qualifier."
        }
    }

    private fun requiredString(obj: JSONObject, key: String, max: Int): String {
        require(obj.has(key) && obj.get(key) is String) { "Missing $key in backup." }
        return (obj.getString(key)).also {
            require(it.isNotBlank() && it.length <= max) { "Invalid $key in backup." }
        }
    }

    private fun optionalString(obj: JSONObject, key: String, max: Int) {
        if (!obj.has(key) || obj.isNull(key)) return
        require(obj.get(key) is String && obj.getString(key).length <= max) {
            "Invalid $key in backup."
        }
    }

    private fun optionalNumber(obj: JSONObject, key: String) {
        if (!obj.has(key) || obj.isNull(key)) return
        require(obj.get(key) is Number && obj.getLong(key) >= 0L) {
            "Invalid $key in backup."
        }
    }

    private fun optionalUrl(obj: JSONObject, key: String, required: Boolean = false) {
        optionalString(obj, key, 4096)
        if (!obj.has(key) || obj.isNull(key)) {
            require(!required) { "Missing tracking URL." }
            return
        }
        val url = obj.getString(key)
        if (url.isBlank()) {
            require(!required) { "Missing tracking URL." }
            return
        }
        val uri = runCatching { java.net.URI(url) }.getOrNull()
        require(uri != null && uri.scheme.equals("https", true) &&
            !uri.host.isNullOrBlank() && uri.userInfo == null &&
            uri.port in setOf(-1, 443)) { "Invalid HTTPS tracking URL." }
    }

    private fun arrayOfObjects(obj: JSONObject, key: String, max: Int,
                               validate: (JSONObject) -> Unit) {
        if (!obj.has(key)) return
        val array = obj.getJSONArray(key)
        require(array.length() <= max) { "Too many $key entries." }
        for (index in 0 until array.length()) validate(array.getJSONObject(index))
    }

    fun export(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return encode(prefs.getString(ENTRIES, "[]") ?: "[]",
            prefs.getLong(INTERVAL, 3L))
    }

    /** Validates the entire file before touching local data. Replaces both
     * keys in one SharedPreferences commit; returns false if commit fails.
     */
    fun restore(context: Context, snapshot: Snapshot): Boolean {
        val validated = decode(encode(snapshot.entriesJson, snapshot.intervalHours))
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.edit()
            .putString(ENTRIES, validated.entriesJson)
            .putLong(INTERVAL, validated.intervalHours)
            .commit()
    }
}
