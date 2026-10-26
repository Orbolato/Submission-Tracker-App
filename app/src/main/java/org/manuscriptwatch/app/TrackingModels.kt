package org.manuscriptwatch.app

import java.net.URI

enum class TrackingKind(val title: String, val priority: Int) {
    JOURNAL("Journal status", 1),
    EDITORIAL_MANAGER("Editorial Manager", 2),
    SPRINGER("Springer Nature", 3),
    ELSEVIER_HUB("Elsevier Author Hub", 4),
    ELSEVIER_PRODUCTION("Elsevier article production", 5);

    companion object {
        fun fromUrl(raw: String): TrackingKind {
            val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return JOURNAL
            val host = uri.host?.lowercase().orEmpty()
            val path = uri.path?.lowercase().orEmpty()
            return when {
                uri.scheme != "https" || uri.userInfo != null -> JOURNAL
                host == "track.authorhub.elsevier.com" && !uri.rawQuery.isNullOrBlank() -> ELSEVIER_HUB
                host == "authors.elsevier.com" && path == "/tracking/article/details.do" -> ELSEVIER_PRODUCTION
                JournalPageClassifier.isSpringerSubmissionDetail(raw) -> SPRINGER
                host == "editorialmanager.com" || host.endsWith(".editorialmanager.com") -> EDITORIAL_MANAGER
                else -> JOURNAL
            }
        }
    }
}

data class TrackingSource(
    val kind: TrackingKind,
    val url: String,
    val lastStatus: String = "",
    val lastChecked: Long = 0L,
    val folder: String = "",
    val lastError: String = "",
    val lastAttempted: Long = 0L
)

data class ReviewProgress(
    val invited: Int? = null,
    val accepted: Int? = null,
    val reports: Int? = null
) {
    fun isEmpty() = invited == null && accepted == null && reports == null
    fun merge(newer: ReviewProgress): ReviewProgress = ReviewProgress(
        invited = newer.invited ?: invited,
        accepted = newer.accepted ?: accepted,
        reports = newer.reports ?: reports
    )
}

data class ReviewEvent(val at: Long, val source: String, val progress: ReviewProgress)

/** Keep a single preferred display status while retaining every source's last status. */
object TrackingSources {
    fun effective(manuscript: Manuscript): List<TrackingSource> {
        val links = manuscript.trackingSources.toMutableList()
        val legacy = manuscript.statusUrl.trim()
        if (legacy.isNotBlank() && links.none { it.url == legacy }) {
            links += TrackingSource(TrackingKind.fromUrl(legacy), legacy)
        }
        return links.distinctBy { it.url }.sortedByDescending { it.kind.priority }
    }

    fun isSupportedUrl(url: String): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        return uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() &&
            uri.userInfo == null && uri.port in setOf(-1, 443) &&
            JournalPageClassifier.isSaveableStatusPage(url)
    }

    fun preferred(sources: List<TrackingSource>): TrackingSource? = sources
        .filter { it.lastStatus.isNotBlank() }
        .maxWithOrNull(compareBy<TrackingSource> { it.kind.priority }.thenBy { it.lastChecked })
}
