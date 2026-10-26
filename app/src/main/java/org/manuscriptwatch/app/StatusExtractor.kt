package org.manuscriptwatch.app

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

sealed class ExtractResult {
    data class Found(val status: String, val reviewProgress: ReviewProgress? = null) : ExtractResult()
    data class NeedsAttention(val reason: String) : ExtractResult()
}

/**
 * Generic, deliberately conservative HTML extraction.
 * Editorial Manager and ScholarOne can customize their markup and load content
 * with JavaScript, so this is not a universal publisher adapter.
 */
object StatusExtractor {
    private val statusPhrases = listOf(
        "required reviews completed", "reviews completed", "decision in process",
        "revised manuscript submitted", "revision submitted", "revision needs approval",
        "submissions waiting for author", "sent back to author", "submitted to journal",
        "revision required", "minor revision", "major revision", "revisions required",
        "transfer pending", "completed - accept", "completed - reject",
        "under review", "with editor", "with journal", "technical check", "editorial assignment",
        "editor assigned", "reviewer invited", "reviewers invited",
        "reviewers assigned", "reviewers accepted", "peer review", "in peer review",
        "editorial assessment", "editorial review", "submission received", "submission complete",
        "quality check", "technical screening", "awaiting editor assignment",
        "accepted", "rejected", "revise", "incomplete", "withdrawn"
    )
    fun extract(html: String, manuscriptId: String, selector: String, pageUrl: String = ""): ExtractResult {
        val kind = TrackingKind.fromUrl(pageUrl)
        if (kind == TrackingKind.ELSEVIER_HUB || kind == TrackingKind.ELSEVIER_PRODUCTION)
            return TrackingPageParser.elsevierTracking(html, pageUrl)
        val doc = Jsoup.parse(html)
        val review = if (JournalPageClassifier.isSpringerSubmissionDetail(pageUrl))
            TrackingPageParser.reviewProgress(html) else null
        val rawText = doc.body()?.text()?.lowercase() ?: ""
        if (rawText.isBlank()) return ExtractResult.NeedsAttention("No visible text; the site may require JavaScript.")
        if (doc.selectFirst("input[type=password]") != null &&
            (rawText.contains("log in") || rawText.contains("sign in")) &&
            !rawText.contains(manuscriptId.lowercase())) {
            return ExtractResult.NeedsAttention("Login may be required. Open the journal and sign in again.")
        }
        if (rawText.contains("captcha") || rawText.contains("verify you are human")) {
            return ExtractResult.NeedsAttention("Site requests human verification. Manual login needed.")
        }
        if (manuscriptId.isBlank()) {
            return ExtractResult.NeedsAttention("A manuscript ID is required for automatic matching.")
        }
        val needle = manuscriptId.lowercase().trim()
        val directSpringer = JournalPageClassifier.isSpringerSubmissionDetail(pageUrl)
        try {
            if (selector.isNotBlank()) {
                val matches = doc.select(selector)
                if (matches.isEmpty()) return ExtractResult.NeedsAttention("A previously saved custom detection rule did not find the status. The journal page may have changed.")
                val connected = matches.filter { candidate ->
                    candidate.text().lowercase().contains(needle) ||
                    candidate.parents().any { it.text().lowercase().contains(needle) && it.text().length < 1400 }
                }
                val candidates = if (connected.isNotEmpty()) connected else if (matches.size == 1 &&
                    (rawText.contains(needle) || directSpringer)) matches else emptyList()
                if (candidates.isEmpty()) return ExtractResult.NeedsAttention("Could not associate any status element with this manuscript ID.")
                for (candidate in candidates) {
                    val raw = candidate.text().trim()
                    val status = if (raw.length <= 120 && !raw.lowercase().contains(needle)) raw else knownStatus(raw)
                    if (!status.isNullOrBlank()) return ExtractResult.Found(status)
                }
                return ExtractResult.NeedsAttention("Matched element contained no unambiguous status.")
            }
        } catch (e: IllegalArgumentException) {
            return ExtractResult.NeedsAttention("Previously saved custom detection settings are no longer valid for this page.")
        }

        // A direct Springer submission page can omit the editorial manuscript ID.
        // The UUID in its URL identifies the selected submission. Only extract a clearly
        // labelled status within that page, never a status appearing in unrelated prose.
        if (directSpringer) {
            val detailStatus = extractDetailStatus(doc)
            if (detailStatus != null) return ExtractResult.Found(detailStatus, review)
            return ExtractResult.NeedsAttention(
                "Springer submission page recognized, but no unambiguous status label was found " +
                "in the HTML. The content may load through JavaScript; open the journal and tap Track page " +
                "after the details finish loading."
            )
        }

        val row = doc.select("tr").firstOrNull { it.text().lowercase().contains(needle) }
        if (row != null) {
            val table = row.closest("table")
            val headers = table?.select("tr")?.firstOrNull { it.select("th").isNotEmpty() }
                ?.select("th")?.map { it.text().lowercase() } ?: emptyList()
            val cells = row.children().filter { it.tagName().lowercase() in setOf("td", "th") }
            val statusColumn = headers.indexOfFirst { it.contains("current status") || it.trim() == "status" }
            if (statusColumn >= 0 && statusColumn < cells.size) {
                val text = cells[statusColumn].text().trim()
                if (text.length in 1..120) return ExtractResult.Found(text)
            }
            row.select("[class*=status], [id*=status]").firstOrNull()?.let { element ->
                val value = element.text().trim()
                if (value.length in 1..120) return ExtractResult.Found(value)
            }
            knownStatus(row.text())?.let { return ExtractResult.Found(it) }
        }
        // Do not scan entire page for phrases: other manuscripts may have different statuses.
        return ExtractResult.NeedsAttention("Could not match this manuscript to a status on the selected page. Open the manuscript's own status page in the journal browser and tap Track page after it loads.")
    }

    private fun extractDetailStatus(doc: org.jsoup.nodes.Document): String? {
        // Explicit status data fields are stronger evidence than a generic page-wide phrase.
        val namedStatus = doc.select("[data-testid*=status], [data-test*=status], [aria-label*=status], [class*=status], [id*=status]")
        for (node in namedStatus) {
            val content = node.text().trim()
            if (content.length in 3..120) {
                val candidate = content.replace(Regex("(?i)^\\s*(?:submission|current|manuscript)?\\s*status\\s*[:–-]?\\s*"), "").trim()
                if (candidate.isNotBlank() && candidate != content && candidate.length <= 90 &&
                    !candidate.equals("unknown", true)) return candidate
                knownStatus(content)?.let { if (content.length <= 85) return it }
            }
        }
        // Some Springer tracking pages highlight the current workflow step.
        // Read only a marked CURRENT step, not all steps in the timeline.
        for (node in doc.select("[aria-current=step], [aria-current=true], [data-current=true]")) {
            val stage = node.text().trim()
            if (stage.length in 3..90) {
                knownStatus(stage)?.let { return it }
            }
        }
        // Labels in definition lists, headings or paired text nodes.
        for (label in doc.select("dt, label, th, strong, b, span, div, p")) {
            val text = label.ownText().trim().lowercase().trimEnd(':')
            if (text !in setOf("status", "current status", "submission status", "manuscript status", "editorial status", "current stage", "editorial stage", "submission stage")) continue
            val sibling = label.nextElementSibling()
            val siblingText = sibling?.text()?.trim().orEmpty()
            if (siblingText.length in 3..100 && !siblingText.lowercase().contains("sign in") &&
                !siblingText.lowercase().contains("log in")) {
                return siblingText
            }
            val parent = label.parent() ?: continue
            val remainder = parent.text().trim().replaceFirst(Regex("(?i)^(?:(?:submission|current|manuscript|editorial)\\s*)?(?:status|stage)\\s*[:–-]?\\s*"), "").trim()
            if (remainder.isNotBlank() && remainder != parent.text().trim() && remainder.length <= 90) return remainder
        }
        return null
    }

    private fun knownStatus(text: String): String? {
        val lowered = text.lowercase()
        return statusPhrases.firstOrNull { lowered.contains(it) }?.let { phrase ->
            phrase.split(' ').joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }
        }
    }
}
