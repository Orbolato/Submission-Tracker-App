package org.manuscriptwatch.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ManuscriptBackupTest {
    private val source = "https://track.authorhub.elsevier.com/?uuid=11111111-2222-4333-8444-555555555555"

    private fun manuscript(id: String = "demo-1"): JSONObject = JSONObject()
        .put("id", id)
        .put("title", "Synthetic manuscript")
        .put("journal", "Example Journal")
        .put("manuscriptId", "EXAMPLE-D-26-00001")
        .put("currentStatus", "Under Review")
        .put("statusUrl", source)
        .put("journalUrl", "https://www.editorialmanager.com/example/")
        .put("lastChecked", 1730000000000L)
        .put("trackingSources", JSONArray().put(
            JSONObject()
                .put("kind", "ELSEVIER_HUB").put("url", source)
                .put("lastStatus", "Under Review").put("lastChecked", 1730000000000L)
                .put("lastError", "")
        ))
        .put("history", JSONArray().put(JSONObject()
            .put("at", 1730000000000L).put("from", "With Editor")
            .put("to", "Under Review").put("source", "Elsevier Author Hub")))
        .put("reviewProgress", JSONObject()
            .put("invited", 2).put("invitedAtLeast", true)
            .put("accepted", 1))
        .put("reviewHistory", JSONArray().put(JSONObject()
            .put("at", 1730000000000L).put("source", "Elsevier Author Hub")
            .put("progress", JSONObject().put("invited", 2)
                .put("invitedAtLeast", true).put("accepted", 1))))

    @Test fun roundTripPreservesManuscriptsSourcesProgressAndHistory() {
        val entries = JSONArray().put(manuscript())
        val backup = ManuscriptBackup.encode(entries.toString(), 360)
        val restored = ManuscriptBackup.decode(backup)
        assertEquals(1, restored.manuscriptCount)
        assertEquals(360, restored.intervalMinutes)
        val saved = JSONArray(restored.entriesJson).getJSONObject(0)
        assertEquals(source, saved.getJSONArray("trackingSources").getJSONObject(0).getString("url"))
        assertEquals(1, saved.getJSONArray("history").length())
        assertEquals(1, saved.getJSONArray("reviewHistory").length())
        assertTrue(saved.getJSONObject("reviewProgress").getBoolean("invitedAtLeast"))
        assertFalse(backup.contains("password", ignoreCase = true))
        assertFalse(backup.contains("cookies", ignoreCase = true))
    }

    @Test fun emptyBackupIsValid() {
        val restored = ManuscriptBackup.decode(ManuscriptBackup.encode("[]", 180))
        assertEquals(0, restored.manuscriptCount)
    }

    @Test fun supportsFifteenThirtyAndCustomMinuteIntervals() {
        for (minutes in listOf(15L, 30L, 45L, 90L, 600L, 10080L)) {
            val backup = ManuscriptBackup.encode("[]", minutes)
            assertEquals(minutes, ManuscriptBackup.decode(backup).intervalMinutes)
            assertEquals(2, JSONObject(backup).getInt("schemaVersion"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ManuscriptBackup.encode("[]", 10081L)
        }
    }

    @Test fun readsV020LegacyHourlyBackup() {
        val old = JSONObject()
            .put("format", "org.manuscriptwatch.backup")
            .put("schemaVersion", 1)
            .put("intervalHours", 3)
            .put("manuscripts", JSONArray())
            .toString()
        val result = ManuscriptBackup.decode(old)
        assertEquals(180L, result.intervalMinutes)
        assertEquals(0, result.manuscriptCount)
    }

    @Test fun rejectsWrongFileOrFormatVersion() {
        assertThrows(IllegalArgumentException::class.java) {
            ManuscriptBackup.decode("""{"manuscripts":[]}""")
        }
        val mutated = JSONObject(ManuscriptBackup.encode("[]", 180))
            .put("schemaVersion", 99).toString()
        assertThrows(IllegalArgumentException::class.java) {
            ManuscriptBackup.decode(mutated)
        }
    }

    @Test fun rejectsMissingRequiredFieldsAndDuplicateRecords() {
        assertThrows(IllegalArgumentException::class.java) {
            ManuscriptBackup.encode(JSONArray().put(JSONObject().put("id", "demo")).toString(), 180)
        }
        val duplicates = JSONArray().put(manuscript()).put(manuscript())
        assertThrows(IllegalArgumentException::class.java) {
            ManuscriptBackup.encode(duplicates.toString(), 180)
        }
    }

    @Test fun rejectsNonHttpsTrackingUrlsAndInvalidCounts() {
        val record = manuscript()
        record.getJSONArray("trackingSources").getJSONObject(0)
            .put("url", "javascript:alert(1)")
        assertThrows(IllegalArgumentException::class.java) {
            ManuscriptBackup.encode(JSONArray().put(record).toString(), 180)
        }
        val another = manuscript()
        another.getJSONObject("reviewProgress").put("accepted", -2)
        assertThrows(IllegalArgumentException::class.java) {
            ManuscriptBackup.encode(JSONArray().put(another).toString(), 180)
        }
    }

    @Test fun rejectsOversizedBackupAndBadCheckIntervals() {
        assertThrows(IllegalArgumentException::class.java) {
            ManuscriptBackup.encode("[]", 14)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ManuscriptBackup.decode(" ".repeat(ManuscriptBackup.MAX_BYTES + 1))
        }
    }
}
