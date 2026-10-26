package org.manuscriptwatch.app

import java.net.URI

/** Classify *only* URLs; never persist or log SSO codes, state or credentials. */
object JournalPageClassifier {
    enum class PageKind { AUTHENTICATION, NOTIFICATIONS, POSSIBLE_STATUS_PAGE, INVALID }

    fun classify(url: String): PageKind {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return PageKind.INVALID
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank()) return PageKind.INVALID
        val host = uri.host.lowercase()
        val path = (uri.rawPath ?: "").lowercase()
        val query = (uri.rawQuery ?: "").lowercase()
        val isSpringer = host == "springernature.com" || host.endsWith(".springernature.com")
        val isIdentity = host == "orcid.org" || host.endsWith(".orcid.org") ||
            host.startsWith("idp.") || host.startsWith("idp-") || host.startsWith("login.") ||
            host.startsWith("auth.") || host.contains("authenticator")
        if (isSpringer && host == "notifications.springernature.com") return PageKind.NOTIFICATIONS
        if (isIdentity || path.contains("/oauth/") || path == "/gateway" ||
            path.startsWith("/login") || (query.contains("response_type=code") && query.contains("state="))) {
            return PageKind.AUTHENTICATION
        }
        return PageKind.POSSIBLE_STATUS_PAGE
    }

    /** A Springer submission detail URL points to one submission, not a list of manuscripts. */
    fun isSpringerSubmissionDetail(url: String): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        return uri.scheme.equals("https", true) && host == "submission.springernature.com" &&
            Regex("^/submission-details/[0-9a-fA-F-]{36}/?$").matches(uri.path ?: "") &&
            (uri.path?.trimEnd('/')?.substringAfterLast('/')?.isNotBlank() == true)
    }

    /** Strip marketing/referral parameters from Springer submission-detail deep links. */
    fun canonicalStatusUrl(url: String): String {
        val source = url.trim()
        if (!isSpringerSubmissionDetail(source)) return source
        return runCatching {
            val uri = URI(source)
            URI(uri.scheme, uri.authority, uri.path, null, null).toASCIIString()
        }.getOrDefault(source)
    }

    fun isSaveableStatusPage(url: String) = classify(url) == PageKind.POSSIBLE_STATUS_PAGE
}
