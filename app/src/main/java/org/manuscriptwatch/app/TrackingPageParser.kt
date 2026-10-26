package org.manuscriptwatch.app

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** Explicit, labeled fields only. Never infer a status/count from unrelated article prose. */
object TrackingPageParser {
    private val statusLabels = setOf(
        "status", "current status", "manuscript status", "submission status",
        "review status", "current stage", "production status", "article status",
        "publication status", "editorial status"
    )
    private val namedField = Regex("(?i)^(?:current |manuscript |submission |production |article |review |publication |editorial )?(?:status|stage)\\s*[:–-]\\s*(.+)$")
    private val progressPatterns = mapOf(
        "invited" to listOf("reviewers invited", "reviewer invitations sent", "invitations sent", "reviewers contacted"),
        "accepted" to listOf("reviewers accepted", "reviewers agreed", "invitations accepted", "reviewers agreed to review"),
        "reports" to listOf("reviews received", "reviewer reports received", "reports received", "completed reviews")
    )

    fun reviewProgress(html: String): ReviewProgress? {
        val doc = Jsoup.parse(html)
        fun labeledCount(terms: List<String>): Int? {
            // Inspect compact visible labels and their immediately adjacent value.
            val nodes = doc.select("dt,dd,span,div,p,li,td,th,strong,b")
            for (node in nodes) {
                val text = node.ownText().trim().lowercase().replace(Regex("\\s+"), " ")
                if (text.length !in 3..100) continue
                val term = terms.firstOrNull { text.contains(it) } ?: continue
                val escaped = Regex.escape(term)
                val before = Regex("\\b(\\d{1,3})\\s+$escaped\\b").find(text)
                val after = Regex("\\b$escaped\\s*[:–-]?\\s*(\\d{1,3})\\b").find(text)
                val n = (before?.groupValues?.get(1) ?: after?.groupValues?.get(1))?.toIntOrNull()
                if (n != null) return n
                if (text == term || text == "$term:") {
                    val sibling = node.nextElementSibling()?.text()?.trim().orEmpty()
                    if (sibling.matches(Regex("\\d{1,3}"))) return sibling.toInt()
                    val parent = node.parent()
                    if (parent != null && parent.childrenSize() == 2) {
                        val pair = parent.children().firstOrNull { it !== node }?.text()?.trim().orEmpty()
                        if (pair.matches(Regex("\\d{1,3}"))) return pair.toInt()
                    }
                }
            }
            return null
        }
        val progress = ReviewProgress(
            invited = labeledCount(progressPatterns.getValue("invited")),
            accepted = labeledCount(progressPatterns.getValue("accepted")),
            reports = labeledCount(progressPatterns.getValue("reports"))
        )
        return progress.takeUnless { it.isEmpty() }
    }

    fun elsevierTracking(html: String, url: String): ExtractResult {
        val kind = TrackingKind.fromUrl(url)
        if (kind != TrackingKind.ELSEVIER_HUB && kind != TrackingKind.ELSEVIER_PRODUCTION) {
            return ExtractResult.NeedsAttention("Not an Elsevier tracking page.")
        }
        val doc = Jsoup.parse(html)
        val text = doc.body()?.text().orEmpty()
        if (text.isBlank()) return ExtractResult.NeedsAttention("Tracking page has no rendered content.")
        if (Regex("(?i)captcha|verify you are human|access denied").containsMatchIn(text)) {
            return ExtractResult.NeedsAttention("Tracking website requires manual verification.")
        }
        val status = explicitStatus(doc)
        val progress = reviewProgress(html)
        if (status != null) return ExtractResult.Found(status, progress)
        return ExtractResult.NeedsAttention(
            "Tracking page loaded, but no clearly labeled current status was found. Open the page in the app to inspect it."
        )
    }

    private fun explicitStatus(doc: Document): String? {
        for (node in doc.select("dt,th,strong,b,label,span,div,p,li")) {
            if (node.childrenSize() > 2) continue
            val field = namedField.find(node.ownText().trim())?.groupValues?.get(1)?.trim()
            if (field != null && reasonable(field)) return field
        }
        for (node in doc.select("[data-testid*=status], [data-test*=status], [aria-label*=status], [class*=status], [id*=status]")) {
            if (node.childrenSize() > 3) continue
            val contents = node.text().trim()
            val found = namedField.find(contents)?.groupValues?.get(1)?.trim()
            if (found != null && reasonable(found)) return found
        }
        for (node in doc.select("dt,th,strong,b,label,span,div,p")) {
            val title = node.ownText().trim().lowercase().trimEnd(':')
            if (title !in statusLabels) continue
            val sibling = node.nextElementSibling()?.text()?.trim().orEmpty()
            if (reasonable(sibling)) return sibling
            if (node.parent()?.childrenSize() == 2) {
                val companion = node.parent()?.children()?.firstOrNull { it !== node }?.text()?.trim().orEmpty()
                if (reasonable(companion)) return companion
            }
        }
        return null
    }

    private fun reasonable(value: String): Boolean {
        if (value.length !in 3..90) return false
        if (value.contains("sign in", true) || value.contains("log in", true)) return false
        if (value.contains("reviewers invited", true) || value.contains("reviews received", true)) return false
        return value.none { it == '<' || it == '>' }
    }
}
