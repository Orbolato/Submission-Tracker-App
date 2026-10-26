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
        catch (_: Exception) { return@withContext ExtractResult.NeedsAttention("Unable to open Android WebView.") }
        try {
            browser.settings.javaScriptEnabled = true
            browser.settings.domStorageEnabled = true
            browser.settings.javaScriptCanOpenWindowsAutomatically = false
            browser.settings.setSupportMultipleWindows(false)
            CookieManager.getInstance().setAcceptCookie(true)
            withTimeoutOrNull(30_000L) { renderAndRead(browser, source) }
                ?: ExtractResult.NeedsAttention("Elsevier tracking page did not expose a readable status within 30 seconds.")
        } catch (e: Exception) {
            ExtractResult.NeedsAttention("Elsevier page check failed (${e.javaClass.simpleName}).")
        } finally {
            browser.stopLoading()
            browser.webViewClient = WebViewClient()
            browser.destroy()
            CookieManager.getInstance().flush()
        }
    }

    private suspend fun renderAndRead(browser: WebView, source: TrackingSource): ExtractResult =
        suspendCancellableCoroutine { continuation ->
            val handler = Handler(Looper.getMainLooper())
            var completed = false
            var reading = false
            var started = false
            var polls = 0
            var lastError = "Page has not rendered a clearly labeled status."
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
                if (TrackingKind.fromUrl(current) != source.kind) {
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
                        lastError = (parsed as ExtractResult.NeedsAttention).reason
                    }
                    polls++
                    if (polls >= 17) finish(ExtractResult.NeedsAttention(lastError))
                    else handler.postDelayed({ poll() }, 1_500L)
                }
            }
            browser.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    started = true
                    if (url != null && TrackingKind.fromUrl(url) != source.kind)
                        finish(ExtractResult.NeedsAttention("Redirected outside the expected Elsevier tracking page."))
                    super.onPageStarted(view, url, favicon)
                }
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (!completed) poll()
                    super.onPageFinished(view, url)
                }
                override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                    if (request?.isForMainFrame == true && (errorResponse?.statusCode ?: 0) >= 400)
                        finish(ExtractResult.NeedsAttention("Elsevier tracking page returned an HTTP error."))
                    super.onReceivedHttpError(view, request, errorResponse)
                }
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    if (request?.isForMainFrame == true)
                        finish(ExtractResult.NeedsAttention("Could not load the Elsevier tracking page."))
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
