package org.manuscriptwatch.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.MainThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONTokener
import kotlin.coroutines.resume

/**
 * Best-effort Springer Nature SNAPP submission-detail monitor.
 *
 * Uses an Android WebView rather than a raw HTTP GET, so the already-authorized
 * WebView cookie jar, web storage, and JavaScript-driven submission UI can load.
 * No passwords are read, autofilled, or submitted in background. Auth challenges
 * and unsupported pages return NeedsAttention; they NEVER imply a status change.
 *
 * Android/OEM background execution restrictions or website anti-automation rules
 * can still prevent checks. This is not a guaranteed always-on session.
 */
object SpringerWebMonitor {
    private const val POLL_MS = 1600L
    private const val TIMEOUT_MS = 30_000L
    private const val HTML_LIMIT = 1_700_000

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun check(context: Context, manuscript: Manuscript): ExtractResult =
        withContext(Dispatchers.Main) {
            val saved = JournalPageClassifier.canonicalStatusUrl(manuscript.statusUrl)
            if (!JournalPageClassifier.isSpringerSubmissionDetail(saved)) {
                return@withContext ExtractResult.NeedsAttention("Open and save a Springer submission-details page first.")
            }
            val browser = try {
                WebView(context.applicationContext)
            } catch (e: Exception) {
                return@withContext ExtractResult.NeedsAttention(
                    "Android could not start the background browser (${e.javaClass.simpleName})."
                )
            }
            try {
                browser.settings.javaScriptEnabled = true
                browser.settings.domStorageEnabled = true
                browser.settings.javaScriptCanOpenWindowsAutomatically = false
                browser.settings.setSupportMultipleWindows(false)
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(browser, true)
                val result = withTimeoutOrNull(TIMEOUT_MS) {
                    runBrowserCheck(browser, saved, manuscript)
                }
                result ?: ExtractResult.NeedsAttention(
                    "Springer did not expose a readable current status within 30 seconds. " +
                    "Open the submission in Manuscript Watch and check your session."
                )
            } catch (e: Exception) {
                ExtractResult.NeedsAttention("Springer browser check failed (${e.javaClass.simpleName}).")
            } finally {
                // A WebView MUST be released on its creation (main) thread.
                browser.stopLoading()
                browser.webViewClient = WebViewClient()
                browser.destroy()
                CookieManager.getInstance().flush()
            }
        }

    @MainThread
    private suspend fun runBrowserCheck(
        browser: WebView,
        expectedUrl: String,
        manuscript: Manuscript
    ): ExtractResult = suspendCancellableCoroutine { continuation ->
        val handler = Handler(Looper.getMainLooper())
        var started = false
        var reading = false
        var completed = false
        var lastReason = "Springer content has not rendered its current status."
        var lastProgress: ReviewProgress? = null
        var bestFound: ExtractResult.Found? = null
        var firstStatusPoll: Int? = null
        var polls = 0

        fun finish(result: ExtractResult) {
            if (completed) return
            completed = true
            handler.removeCallbacksAndMessages(null)
            if (continuation.isActive) continuation.resume(result)
        }

        fun poll() {
            if (completed || !continuation.isActive) return
            val current = browser.url ?: ""
            if (!started || current.isEmpty() || current == "about:blank") {
                handler.postDelayed({ poll() }, POLL_MS)
                return
            }
            if (!JournalPageClassifier.isSpringerSubmissionDetail(current)) {
                finish(ExtractResult.NeedsAttention(
                    "Springer redirected to sign-in or another page. Open the journal browser and log in."
                ))
                return
            }
            if (reading) {
                handler.postDelayed({ poll() }, POLL_MS)
                return
            }
            reading = true
            // Read only the same-origin document, never a login provider's DOM.
            // No manuscript content, page HTML or cookies are written to logs/files.
            browser.evaluateJavascript(
                """(function(){
                    if (location.protocol !== 'https:' || location.hostname !== 'submission.springernature.com') return '';
                    var b=document.body;
                    if (!b) return '';
                    var c=b.cloneNode(true);
                    c.querySelectorAll('script,style,svg,noscript').forEach(function(e){e.remove();});
                    var h=c.outerHTML;
                    return h.length > $HTML_LIMIT ? '' : h;
                })()""".trimIndent()
            ) { encoded ->
                reading = false
                if (completed || !continuation.isActive) return@evaluateJavascript
                val html = runCatching { JSONTokener(encoded).nextValue() as? String }.getOrNull()
                if (!html.isNullOrBlank()) {
                    val parsed = StatusExtractor.extract(html, manuscript.manuscriptId,
                        manuscript.cssSelector, expectedUrl)
                    if (parsed is ExtractResult.Found) {
                        // Springer may render its status card before the reviewer-news
                        // widget. Allow several short polls for the explicit counts.
                        // Never turn a missing completed-review field into zero.
                        bestFound = if (parsed.reviewProgress != null) parsed
                            else bestFound?.takeIf { it.reviewProgress != null } ?: parsed
                        if (firstStatusPoll == null) firstStatusPoll = polls
                        if (bestFound?.reviewProgress != null ||
                            polls - (firstStatusPoll ?: polls) >= 7) {
                            finish(bestFound!!)
                            return@evaluateJavascript
                        }
                    } else {
                        val issue = parsed as ExtractResult.NeedsAttention
                        lastReason = issue.reason
                        lastProgress = issue.reviewProgress ?: lastProgress
                    }
                } else {
                    lastReason = "Springer page is not ready or its DOM is too large to inspect safely."
                }
                polls++
                if (polls >= 16) {
                    finish(bestFound ?: ExtractResult.NeedsAttention(
                        "Springer page loaded, but a current status was not identified. $lastReason", lastProgress
                    ))
                } else {
                    handler.postDelayed({ poll() }, POLL_MS)
                }
            }
        }

        browser.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                started = true
                if (url != null && !JournalPageClassifier.isSpringerSubmissionDetail(url)) {
                    finish(ExtractResult.NeedsAttention(
                        "Springer authentication is required or the saved submission URL redirected. " +
                        "Open Sign in and refresh your Springer session."
                    ))
                }
                super.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (!completed && url != null && JournalPageClassifier.isSpringerSubmissionDetail(url)) {
                    poll()
                }
                super.onPageFinished(view, url)
            }

            override fun onReceivedHttpError(
                view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?
            ) {
                val code = errorResponse?.statusCode ?: 0
                if (request?.isForMainFrame == true && code in 400..599) {
                    finish(ExtractResult.NeedsAttention("Springer returned HTTP $code. Check login in the app."))
                }
                super.onReceivedHttpError(view, request, errorResponse)
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    finish(ExtractResult.NeedsAttention("Springer page could not be loaded. Check connection and login."))
                }
                super.onReceivedError(view, request, error)
            }
        }
        handler.postDelayed({ poll() }, POLL_MS)
        browser.loadUrl(expectedUrl)
        continuation.invokeOnCancellation {
            handler.removeCallbacksAndMessages(null)
            completed = true
        }
    }
}
