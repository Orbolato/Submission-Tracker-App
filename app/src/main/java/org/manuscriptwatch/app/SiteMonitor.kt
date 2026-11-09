package org.manuscriptwatch.app

import android.webkit.CookieManager
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL

object SiteMonitor {
    fun check(manuscript: Manuscript): ExtractResult {
        val rawUrl = JournalPageClassifier.canonicalStatusUrl(manuscript.statusUrl.trim())
        if (rawUrl.isBlank()) return ExtractResult.NeedsAttention("No status-page URL configured.")
        if (!JournalPageClassifier.isSaveableStatusPage(rawUrl)) {
            return ExtractResult.NeedsAttention("Saved URL is an authentication redirect or notification page, not a manuscript status list.")
        }
        val target = runCatching { URL(rawUrl) }.getOrNull()
            ?: return ExtractResult.NeedsAttention("Invalid status-page URL.")
        if (target.protocol != "https") return ExtractResult.NeedsAttention("HTTPS is required for private status pages.")
        var current = target
        repeat(5) {
            val connection = (current.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) ManuscriptWatch/0.1")
                setRequestProperty("Accept", "text/html,application/xhtml+xml")
                val cookies = runCatching { CookieManager.getInstance().getCookie(current.toString()) }.getOrNull()
                if (!cookies.isNullOrBlank()) setRequestProperty("Cookie", cookies)
            }
            try {
                val code = connection.responseCode
                if (code in listOf(301, 302, 303, 307, 308)) {
                    val location = connection.getHeaderField("Location")
                        ?: return ExtractResult.NeedsAttention("Redirect has no destination.")
                    val next = URL(current, location)
                    if (next.protocol != "https" || next.host != target.host) {
                        return ExtractResult.NeedsAttention("Redirected to another domain, likely a login provider. Please authenticate in the journal browser.")
                    }
                    current = next
                } else if (code == 401 || code == 403) {
                    return ExtractResult.NeedsAttention("Access denied or session expired. Please sign in again.")
                } else if (code != 200) {
                    return ExtractResult.NeedsAttention("Journal site returned HTTP $code.",
                        retryable = MonitorRetryPolicy.isTransientHttpStatus(code))
                } else {
                    val type = connection.contentType ?: ""
                    if (!type.contains("html", ignoreCase = true)) {
                        return ExtractResult.NeedsAttention("Status URL returned a non-HTML response.")
                    }
                    val input = BufferedInputStream(connection.inputStream)
                    val bytes = input.use { it.readNBytes(2_000_001) }
                    if (bytes.size > 2_000_000) return ExtractResult.NeedsAttention("Page exceeds 2 MB safety limit.")
                    val charset = runCatching {
                        connection.contentType?.substringAfter("charset=", "")
                            ?.substringBefore(';')?.trim()?.trim('"')?.takeIf { it.isNotEmpty() }
                            ?.let { java.nio.charset.Charset.forName(it) }
                    }.getOrNull() ?: Charsets.UTF_8
                    val result = StatusExtractor.extract(bytes.toString(charset), manuscript.manuscriptId,
                        manuscript.cssSelector, current.toString())
                    if (result is ExtractResult.NeedsAttention &&
                        JournalPageClassifier.isSpringerSubmissionDetail(current.toString())) {
                        return ExtractResult.NeedsAttention(
                            "Springer detail page is recognized, but its status was not readable by " +
                            "the background HTML fetch. It may require JavaScript rendering " +
                            "or browser-only session state. Open the page inside the app and tap Track page."
                        )
                    }
                    return result
                }
            } finally {
                connection.disconnect()
            }
        }
        return ExtractResult.NeedsAttention("Too many redirects.")
    }
}
