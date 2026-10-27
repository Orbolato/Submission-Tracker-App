package org.manuscriptwatch.app

import org.jsoup.Jsoup
import java.time.LocalDate

/**
 * Elsevier's "Track Your Accepted Article" page shows dated production events,
 * not necessarily a field named "current status".
 *
 * Parse only events under an explicit "Production events" heading. A milestone
 * is a dated fact about production, not an inferred editorial decision.
 * No tracking URL, manuscript ID, or HTML is stored or logged here.
 */
object ElsevierProductionEvents {
    data class Event(val date: LocalDate, val description: String)

    private val eventNames = listOf(
        "Printed journal shipped to subscribers",
        "The Share Link has been sent to you",
        "Final version of your article published online",
        "Your proof corrections have been returned to Elsevier",
        "Proofs available for checking",
        "Article published online",
        "Rights & Access form completed by you",
        "Rights & Access form sent to you for completion",
        "Offprint order letter sent to you for completion",
        "Received for production"
    )

    private val datePattern = Regex(
        """(?i)(?<![a-z0-9])(\d{1,2})\s+([a-z]{3,9})\s*(\d{4})(?!\d)"""
    )
    private val monthNumbers = listOf(
        "jan", "feb", "mar", "apr", "may", "jun",
        "jul", "aug", "sep", "oct", "nov", "dec"
    )

    fun events(html: String): List<Event> {
        val doc = Jsoup.parse(html)
        val body = doc.body()?.text()?.replace(Regex("""\s+"""), " ") ?: return emptyList()
        val marker = Regex("""(?i)\bproduction events\b""").find(body) ?: return emptyList()
        // The production timeline is normally reverse chronological. The document
        // can also have navigation, details, and dates for unrelated content.
        // Only read dated *known production milestones* following its heading.
        val timeline = body.substring(marker.range.last + 1).take(24_000)
        val matches = datePattern.findAll(timeline).take(120).toList()
        if (matches.isEmpty()) return emptyList()

        return matches.mapIndexedNotNull { index, match ->
            val day = match.groupValues[1].toIntOrNull() ?: return@mapIndexedNotNull null
            val month = monthNumbers.indexOf(match.groupValues[2].lowercase().take(3)) + 1
            val year = match.groupValues[3].toIntOrNull() ?: return@mapIndexedNotNull null
            if (month == 0 || year !in 1900..2100) return@mapIndexedNotNull null
            val date = runCatching { LocalDate.of(year, month, day) }.getOrNull()
                ?: return@mapIndexedNotNull null
            val end = if (index + 1 < matches.size) matches[index + 1].range.first else timeline.length
            val following = timeline.substring(match.range.last + 1, end)
                .trimStart(' ', '\t', ':', '-', '–')
                .take(210)
            val description = eventNames.firstOrNull { name ->
                following.startsWith(name, ignoreCase = true) &&
                    (following.length == name.length ||
                        !following[name.length].isLetterOrDigit())
            } ?: return@mapIndexedNotNull null
            Event(date, description)
        }.distinct().sortedWith(compareByDescending<Event> { it.date })
    }

    fun latest(html: String): Event? = events(html).firstOrNull()
}
