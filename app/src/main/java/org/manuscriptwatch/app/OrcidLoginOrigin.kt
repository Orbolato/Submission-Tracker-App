package org.manuscriptwatch.app

import java.net.URI

object OrcidLoginOrigin {
    fun isTrusted(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.scheme.equals("https", true) &&
            uri.host?.lowercase() in setOf("orcid.org", "login.orcid.org") &&
            (uri.port == -1 || uri.port == 443) &&
            uri.userInfo == null
    }
}
