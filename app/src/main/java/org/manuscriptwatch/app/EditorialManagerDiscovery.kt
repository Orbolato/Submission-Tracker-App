package org.manuscriptwatch.app

import android.webkit.CookieManager
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Read-only, same-host GET discovery for Editorial Manager's accessible author folders.
 * JavaScript-only/POST navigation is intentionally not automated.
 */
object EditorialManagerDiscovery {
    data class FolderLink(val title: String, val url: String)
    data class Found(val url: String, val folder: String, val status: ExtractResult.Found)
    sealed class Result {
        data class Located(val value: Found) : Result()
        data class NeedsAttention(val reason: String) : Result()
    }

    private val folderPattern = Regex(
        "(?i)(submissions?|revisions?|transfer|completed|decisions?|declined).{0,70}" +
            "(processing|revision|decision|complete|author|approval|transfer|submitted|withdrawn)|" +
            "(submissions? being processed|revisions? being processed|submissions? with a decision|" +
            "all submissions? with decision|submissions? needing revision|incomplete submissions?)"
    )
    private val dangerous = Regex("(?i)(logout|delete|remove|withdraw|approve|decline|submit|update|edit|action=|command=)")
    private val pager = Regex("(?i)^(next|next page|more|>)$")

    fun isEditorialManager(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase().orEmpty()
        return uri.scheme.equals("https", true) && uri.userInfo == null && uri.port in setOf(-1, 443) &&
            (host == "editorialmanager.com" || host.endsWith(".editorialmanager.com"))
    }

    /** Only explicit author-folder links; never follow form actions or javascript URLs. */
    fun folders(html: String, pageUrl: String): List<FolderLink> {
        if (!isEditorialManager(pageUrl)) return emptyList()
        val doc = Jsoup.parse(html, pageUrl)
        return doc.select("a[href]").mapNotNull { anchor ->
            val text = anchor.text().trim().replace(Regex("\\s+"), " ")
            if (text.length !in 6..100 || !folderPattern.containsMatchIn(text) ||
                dangerous.containsMatchIn(text)) return@mapNotNull null
            val href = anchor.attr("abs:href")
            val uri = runCatching { URI(href) }.getOrNull() ?: return@mapNotNull null
            if (!safeNavigation(pageUrl, uri)) return@mapNotNull null
            FolderLink(text, uri.toASCIIString())
        }.distinctBy { it.url }.take(12)
    }

    /** Recognize the author menu even when folder navigation is JavaScript/POST based.
     * Detection does not imply that those links can be fetched automatically.
     */
    fun isMenuPage(html: String, url: String): Boolean {
        if (!isEditorialManager(url)) return false
        val doc = Jsoup.parse(html, url)
        val namedFolders = doc.select("a,button,li,td,span,[role=button],[onclick]")
            .map { it.ownText().trim().replace(Regex("\\s+"), " ") }
            .filter { it.length in 6..120 && folderPattern.containsMatchIn(it) }
            .distinct()
        // Editorial Manager's "Author Main Menu" text may be in a regular div,
        // not a heading. Many installations render the folders as POST controls
        // or JavaScript actions, which are discoverable but not safe to crawl.
        val visibleText = doc.body()?.text().orEmpty()
        val authorMenu = Regex(
            "(?i)\\b(author main menu|main menu for authors|author center|author centre)\\b"
        ).containsMatchIn(visibleText)
        return folders(html, url).size >= 2 ||
            (authorMenu && namedFolders.isNotEmpty()) ||
            namedFolders.size >= 3
    }


    /** Scan folder GET links extracted from the actual, signed-in WebView DOM.
     * This avoids losing menus that are rendered with JavaScript after page load.
     */
    fun locateFromRenderedMenu(manuscript: Manuscript, menuUrl: String, html: String): Result {
        if (!isEditorialManager(menuUrl)) return Result.NeedsAttention("Open an Editorial Manager author page first.")
        val links = folders(html, menuUrl)
        if (links.isEmpty()) return Result.NeedsAttention(
            "Author menu detected, but its folders use JavaScript or form navigation. " +
                "Open the folders normally inside this browser; Manuscript Watch will try to recognize your manuscript as each page loads."
        )
        return scanFolders(manuscript, links)
    }

    fun folderTitle(html: String): String {
        val doc = Jsoup.parse(html)
        return doc.select("h1,h2,h3,h4,header,strong").map { it.text().trim() }
            .firstOrNull { it.length in 6..100 && folderPattern.containsMatchIn(it) }.orEmpty()
    }

    fun nextPage(html: String, pageUrl: String): String? {
        val doc = Jsoup.parse(html, pageUrl)
        return doc.select("a[href]").firstNotNullOfOrNull { a ->
            val text = a.text().trim()
            val rel = a.attr("rel")
            if (!pager.matches(text) && !rel.equals("next", true)) return@firstNotNullOfOrNull null
            val uri = runCatching { URI(a.attr("abs:href")) }.getOrNull() ?: return@firstNotNullOfOrNull null
            uri.toASCIIString().takeIf { safeNavigation(pageUrl, uri) }
        }
    }

    private fun safeNavigation(base: String, next: URI): Boolean {
        val previous = runCatching { URI(base) }.getOrNull() ?: return false
        return next.scheme.equals("https", true) &&
            next.host.equals(previous.host, true) && next.port in setOf(-1, 443) &&
            next.userInfo == null && next.fragment == null &&
            !dangerous.containsMatchIn(next.rawPath.orEmpty()) &&
            !dangerous.containsMatchIn(next.rawQuery.orEmpty())
    }

    fun locate(manuscript: Manuscript): Result {
        val startUrls = listOf(manuscript.editorialMenuUrl, manuscript.journalUrl, manuscript.statusUrl)
            .filter { isEditorialManager(it) }.distinct()
        if (startUrls.isEmpty()) return Result.NeedsAttention("No Editorial Manager journal page is configured.")

        var menu: Pair<String, List<FolderLink>>? = null
        var lastError = "Journal author menu was not found. Open the journal, sign in, and save the Author Main Menu."
        for (candidate in startUrls.take(3)) {
            val html = fetch(candidate) ?: continue
            val links = folders(html, candidate)
            if (links.isNotEmpty()) { menu = candidate to links; break }
            val direct = StatusExtractor.extract(html, manuscript.manuscriptId, manuscript.cssSelector, candidate)
            if (direct is ExtractResult.Found) return Result.Located(Found(candidate, "Previously tracked folder", direct))
        }
        val links = menu?.second ?: return Result.NeedsAttention(lastError)
        return scanFolders(manuscript, links)
    }

    private fun scanFolders(manuscript: Manuscript, links: List<FolderLink>): Result {
        val ordered = links.sortedByDescending { if (it.title.equals(manuscript.currentFolder, true)) 1 else 0 }
        for (folder in ordered) {
            var page = folder.url
            val seen = mutableSetOf<String>()
            for (index in 0 until 3) {
                if (!seen.add(page)) break
                val html = fetch(page) ?: break
                val parsed = StatusExtractor.extract(html, manuscript.manuscriptId, "", page)
                if (parsed is ExtractResult.Found) return Result.Located(Found(page, folder.title, parsed))
                val next = nextPage(html, page) ?: break
                page = next
            }
        }
        return Result.NeedsAttention(
            "The manuscript was not found in ${links.size} accessible Editorial Manager folders. " +
                "This can happen with JavaScript/POST navigation, additional pages, or an expired session."
        )
    }

    private fun fetch(url: String): String? {
        val target = runCatching { URL(url) }.getOrNull() ?: return null
        if (!isEditorialManager(url)) return null
        val connection = (target.openConnection() as? HttpURLConnection) ?: return null
        return try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 12_000
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
            val cookies = runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()
            if (!cookies.isNullOrBlank()) connection.setRequestProperty("Cookie", cookies)
            if (connection.responseCode != 200 ||
                !connection.contentType.orEmpty().contains("html", true)) null
            else {
                val bytes = connection.inputStream.use { it.readNBytes(1_250_001) }
                if (bytes.size > 1_250_000) null else bytes.toString(Charsets.UTF_8)
            }
        } catch (_: Exception) { null }
        finally { connection.disconnect() }
    }
}
