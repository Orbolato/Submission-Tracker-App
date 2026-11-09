package org.manuscriptwatch.app

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONTokener
import kotlin.coroutines.resume

/** Read-only renderer for explicit Elsevier tracking URLs. No hidden login or site crawling. */
object ElsevierWebMonitor {
    private const val LIMIT = 1_700_000

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun check(context: Context, source: TrackingSource): ExtractResult = withContext(Dispatchers.Main) {
        if (source.kind !in setOf(TrackingKind.ELSEVIER_HUB, TrackingKind.ELSEVIER_PRODUCTION) ||
            !TrackingSources.isSupportedUrl(source.url)) {
            return@withContext ExtractResult.NeedsAttention("Enter a valid Elsevier tracking URL.")
        }
        val browser = try { WebView(context.applicationContext) }
        catch (_: Exception) { return@withContext ExtractResult.NeedsAttention("Unable to open Android WebView.", retryable = true) }
        try {
            browser.settings.javaScriptEnabled = true
            browser.settings.domStorageEnabled = true
            browser.settings.javaScriptCanOpenWindowsAutomatically = false
            browser.settings.setSupportMultipleWindows(false)
            CookieManager.getInstance().setAcceptCookie(true)
            withTimeoutOrNull(30_000L) { renderAndRead(browser, source) }
                ?: ExtractResult.NeedsAttention("Elsevier tracking page did not expose a readable status within 30 seconds.",
                    retryable = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ExtractResult.NeedsAttention("Elsevier page check failed (${e.javaClass.simpleName}).",
                retryable = true)
        } finally {
            browser.stopLoading()
            browser.webViewClient = WebViewClient()
            browser.destroy()
            CookieManager.getInstance().flush()
        }
    }

    /** A tracking site may rewrite its same-origin URL after loading the UUID.
     * Do not require the UUID query string on every internal navigation.
     */
    private fun onExpectedHost(url: String, kind: TrackingKind): Boolean {
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
        val expected = if (kind == TrackingKind.ELSEVIER_HUB)
            "track.authorhub.elsevier.com" else "authors.elsevier.com"
        return uri.scheme.equals("https", true) && uri.host.equals(expected, true) &&
            uri.userInfo == null && uri.port in setOf(-1, 443)
    }

    private suspend fun renderAndRead(browser: WebView, source: TrackingSource): ExtractResult =
        suspendCancellableCoroutine { continuation ->
            val handler = Handler(Looper.getMainLooper())
            var completed = false
            var reading = false
            var started = false
            var polls = 0
            var lastError = "Page has not rendered a clearly labeled status."
            var lastProgress: ReviewProgress? = null
            fun finish(result: ExtractResult) {
                if (completed) return
                completed = true
                handler.removeCallbacksAndMessages(null)
                if (continuation.isActive) continuation.resume(result)
            }
            fun poll() {
                if (completed || !continuation.isActive) return
                val current = browser.url.orEmpty()
                if (!started || current.isBlank() || current == "about:blank" || reading) {
                    handler.postDelayed({ poll() }, 1_500L)
                    return
                }
                if (!onExpectedHost(current, source.kind)) {
                    finish(ExtractResult.NeedsAttention("Tracking page redirected away. Open the link and complete any required sign-in."))
                    return
                }
                reading = true
                // JavaScript is limited to extracting sanitized DOM on the page's own host.
                val requiredHost = if (source.kind == TrackingKind.ELSEVIER_HUB)
                    "track.authorhub.elsevier.com" else "authors.elsevier.com"
                browser.evaluateJavascript(
                    """(function(){
                        if (location.protocol !== 'https:' || location.hostname !== '$requiredHost') return '';
                        var body=document.body;if(!body)return '';
                        var copy=body.cloneNode(true);
                        copy.querySelectorAll('script,style,svg,noscript,input,textarea').forEach(function(n){n.remove();});
                        var result=copy.outerHTML;
                        return result.length <= $LIMIT ? result : '';
                    })()""".trimIndent()
                ) { encoded ->
                    reading = false
                    if (completed || !continuation.isActive) return@evaluateJavascript
                    val html = runCatching { JSONTokener(encoded).nextValue() as? String }.getOrNull()
                    if (!html.isNullOrBlank()) {
                        val parsed = TrackingPageParser.elsevierTracking(html, source.url)
                        if (parsed is ExtractResult.Found) { finish(parsed); return@evaluateJavascript }
                        val issue = parsed as ExtractResult.NeedsAttention
                        lastError = issue.reason
                        lastProgress = issue.reviewProgress ?: lastProgress
                        if (MonitorRetryPolicy.requiresUserAction(issue.reason)) {
                            finish(issue)
                            return@evaluateJavascript
                        }
                    }
                    polls++
                    if (polls >= 17) finish(ExtractResult.NeedsAttention(lastError, lastProgress,
                        retryable = true))
                    else handler.postDelayed({ poll() }, 1_500L)
                }
            }
            browser.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    started = true
                    if (url != null && !onExpectedHost(url, source.kind))
                        finish(ExtractResult.NeedsAttention("Redirected outside the expected Elsevier tracking page."))
                    super.onPageStarted(view, url, favicon)
                }
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (!completed) poll()
                    super.onPageFinished(view, url)
                }
                override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                    val code = errorResponse?.statusCode ?: 0
                    if (request?.isForMainFrame == true && code >= 400)
                        finish(ExtractResult.NeedsAttention("Elsevier tracking page returned HTTP $code.",
                            retryable = MonitorRetryPolicy.isTransientHttpStatus(code)))
                    super.onReceivedHttpError(view, request, errorResponse)
                }
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    if (request?.isForMainFrame == true)
                        finish(ExtractResult.NeedsAttention("Could not load the Elsevier tracking page.",
                            retryable = true))
                    super.onReceivedError(view, request, error)
                }
            }
            handler.postDelayed({ poll() }, 1_500L)
            browser.loadUrl(source.url)
            continuation.invokeOnCancellation {
                handler.removeCallbacksAndMessages(null)
                completed = true
            }
        }
}
