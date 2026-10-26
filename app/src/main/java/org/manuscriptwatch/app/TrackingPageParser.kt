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
        "reports" to listOf("reviews completed", "reviews received", "reviewer reports received", "reports received", "completed reviews")
    )

    fun reviewProgress(html: String): ReviewProgress? {
        val doc = Jsoup.parse(html)
        // The publisher's "2+" means at least two invitations, not exactly two.
        // Preserve that qualifier rather than coercing it to an exact count.
        data class Count(val value: Int, val atLeast: Boolean)
        fun labeledCount(terms: List<String>): Count? {
            val nodes = doc.select("dt,dd,span,div,p,li,td,th,strong,b")
            for (node in nodes) {
                val text = node.ownText().trim().lowercase().replace(Regex("\\s+"), " ")
                if (text.length !in 3..120) continue
                for (term in terms) {
                    val escaped = Regex.escape(term)
                    // Pair either "Review invitations sent: 2+" or "2+ reviewers invited".
                    val after = Regex("\\b$escaped\\s*[:\u2013-]?\\s*(\\d{1,3})(\\+?)(?!\\d)").find(text)
                    val before = Regex("\\b(\\d{1,3})(\\+?)\\s+$escaped\\b").find(text)
                    val result = after ?: before
                    if (result != null) return Count(result.groupValues[1].toInt(), result.groupValues[2] == "+")
                    if (text == term || text == "$term:") {
                        val sibling = node.nextElementSibling()?.text()?.trim().orEmpty()
                        val paired = sibling.takeIf { it.matches(Regex("\\d{1,3}\\+?")) }
                            ?: node.parent()?.takeIf { it.childrenSize() == 2 }?.children()
                                ?.firstOrNull { it !== node }?.text()?.trim()
                        if (paired != null && paired.matches(Regex("\\d{1,3}\\+?")))
                            return Count(paired.trimEnd('+').toInt(), paired.endsWith('+'))
                    }
                }
            }
            return null
        }
        val invited = labeledCount(progressPatterns.getValue("invited"))
        val accepted = labeledCount(progressPatterns.getValue("accepted"))
        val reports = labeledCount(progressPatterns.getValue("reports"))
        val progress = ReviewProgress(
            invited = invited?.value,
            invitedAtLeast = invited?.atLeast ?: false,
            accepted = accepted?.value,
            reports = reports?.value
        )
        return progress.takeUnless { it.isEmpty() }
    }

    /**
     * SNAPP's author-facing reviewer summary uses sentences rather than labeled fields:
     * "The editor has invited 6 reviewer(s)"
     * "There is 1 reviewer(s) that has accepted to review your manuscript"
     *
     * Only count statements explicitly present in the manuscript's own detail page.
     * In particular, narrative mentioning "reports" does NOT establish that any
     * reviews have been received; unknown is different from zero.
     */
    fun springerReviewProgress(html: String): ReviewProgress? {
        val generic = reviewProgress(html)
        val doc = Jsoup.parse(html)
        val invitedSentence = Regex(
            """(?i)^the editor has invited\s+(\d{1,3})(\+?)\s+reviewer(?:\(s\)|s)?(?:\s+to review(?:\s+your manuscript)?)?\.?$"""
        )
        val acceptedSentence = Regex(
            """(?i)^there (?:is|are)\s+(\d{1,3})\s+reviewer(?:\(s\)|s)?\s+(?:that|who)\s+(?:has|have)\s+accepted(?:\s+to review(?:\s+your manuscript)?)?\.?$"""
        )
        var invited: Int? = null
        var atLeast = false
        var accepted: Int? = null

        // Prefer compact list items/paragraphs. Avoid scanning whole-page text for
        // bare numbers, which could refer to an unrelated submission or help text.
        for (node in doc.select("li,p,span,div")) {
            val text = node.text().trim().replace(Regex("""\s+"""), " ")
            if (text.length !in 25..170) continue
            val invitedMatch = invitedSentence.matchEntire(text)
            if (invitedMatch != null) {
                invited = invitedMatch.groupValues[1].toInt()
                atLeast = invitedMatch.groupValues[2] == "+"
            }
            val acceptedMatch = acceptedSentence.matchEntire(text)
            if (acceptedMatch != null) {
                accepted = acceptedMatch.groupValues[1].toInt()
            }
        }
        val sentences = ReviewProgress(invited = invited, invitedAtLeast = atLeast, accepted = accepted)
        val combined = generic?.merge(sentences) ?: sentences
        return combined.takeUnless { it.isEmpty() }
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
        val status = explicitStatus(doc) ?: activeTimelineStage(doc) ?: clearlyLabeledBanner(doc)
        val progress = reviewProgress(html)
        if (status != null) return ExtractResult.Found(status, progress)
        return ExtractResult.NeedsAttention(
            "Tracking page loaded, but no clearly labeled current status was found. " +
                "Try opening it with Open tracking page and select Track page after it renders.",
            progress
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

    /** A prominent single-status banner (such as Elsevier Author Hub's "Under Review")
     * can be a status even without a "Status:" label. Never infer from a general timeline.
     */
    private fun clearlyLabeledBanner(doc: Document): String? {
        val known = setOf("under review", "in peer review", "with editor", "decision in process",
            "submitted to journal", "required reviews completed", "revision requested",
            "major revision", "minor revision", "accepted", "in production", "published")
        val candidates = doc.select("h1,h2,h3,h4,[data-testid*=status],[class*=status],[class*=Status]")
            .mapNotNull { node ->
                val label = node.text().trim().replace(Regex("\\s+"), " ")
                label.takeIf { it.lowercase() in known }
            }.distinct()
        if (candidates.size == 1) return candidates[0]
        // Some publisher templates put the current status directly above the last review activity.
        val banners = doc.select("div,section,aside").mapNotNull { node ->
            if (node.childrenSize() > 8) return@mapNotNull null
            val text = node.text().trim().replace(Regex("\\s+"), " ")
            if (text.length !in 20..150 || !text.contains("Last review activity", true)) return@mapNotNull null
            known.firstOrNull { text.startsWith(it, true) }?.let { term ->
                term.takeIf { text.length > it.length && text.substring(it.length).startsWith(" Last review activity", true) }
            }
        }.distinct()
        return banners.singleOrNull()?.split(' ')?.joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }
    }

    /** Only a current/active milestone can determine the stage; historical items are not current. */
    private fun activeTimelineStage(doc: Document): String? {
        val recognized = setOf(
            "submitted to journal", "with editor", "under review", "in peer review",
            "peer review", "required reviews completed", "decision in process",
            "revision requested", "major revision", "minor revision", "accepted",
            "article accepted", "in production", "proofs", "proof correction",
            "published", "reviewers invited", "reviewer invited"
        )
        val active = doc.select(
            "[aria-current=step], [aria-current=true], [data-current=true], " +
                "[data-state=active], [data-status=current], [class*=current-step], " +
                "[class*=active-stage]"
        ).mapNotNull { node ->
            val label = node.text().trim().replace(Regex("\\s+"), " ")
            label.takeIf { it.length in 4..50 && label.lowercase() in recognized }
        }.distinct()
        return active.singleOrNull()
    }

    private fun reasonable(value: String): Boolean {
        if (value.length !in 3..90) return false
        if (value.contains("sign in", true) || value.contains("log in", true)) return false
        if (value.contains("reviewers invited", true) || value.contains("reviews received", true)) return false
        return value.none { it == '<' || it == '>' }
    }
}
