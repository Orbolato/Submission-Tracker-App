package org.manuscriptwatch.app

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceResponse
import org.json.JSONTokener
import org.json.JSONObject
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity

/**
 * Browses journal websites and lets the user save a *status-list* URL, not an OAuth redirect.
 * Some publishers disallow WebView SSO. External Chrome is a login fallback only; its
 * cookies do not become available to this WebView or to background checks.
 */
class BrowserActivity : ComponentActivity() {
    private lateinit var host: FrameLayout
    private lateinit var primary: WebView
    private lateinit var pageHint: TextView
    private val popupStack = mutableListOf<WebView>()

    private fun activeWebView(): WebView = popupStack.lastOrNull() ?: primary

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("url") ?: ""
        if (JournalPageClassifier.classify(url) == JournalPageClassifier.PageKind.INVALID) { finish(); return }
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 6, 8, 6)
        }
        fun action(title: String, click: () -> Unit) {
            toolbar.addView(Button(this).apply {
                text = title
                textSize = 11f
                isAllCaps = false
                setOnClickListener { click() }
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        action("Back") {
            val active = activeWebView()
            if (active.canGoBack()) active.goBack()
            else if (active !== primary) closePopup(active)
            else finish()
        }
        action("Resume login") {
            // Reopen the stable entry point with the existing WebView cookie jar.
            // Never reload temporary OAuth state/code URLs or replay POSTed credentials.
            CookieManager.getInstance().flush()
            popupStack.toList().forEach { closePopup(it) }
            primary.loadUrl(url)
            pageHint.text = "Restarting at the journal entry page with existing session cookies. If prompted, sign in manually. Your password is never retried by the app."
        }
        action("Track page") {
            val manuscriptId = intent.getStringExtra("manuscriptId")
            val current = activeWebView().url ?: ""
            val kind = JournalPageClassifier.classify(current)
            if (!JournalPageClassifier.isSaveableStatusPage(current)) {
                val message = when (kind) {
                    JournalPageClassifier.PageKind.AUTHENTICATION ->
                        "Finish signing in, then open the page showing your manuscript status."
                    JournalPageClassifier.PageKind.NOTIFICATIONS ->
                        "Open your manuscript on Springer Nature Submissions before selecting a tracking page."
                    else -> "Open the journal page that shows your manuscript status first."
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                return@action
            }
            if (manuscriptId.isNullOrBlank()) {
                Toast.makeText(this, "No manuscript is selected.", Toast.LENGTH_LONG).show()
                return@action
            }
            val record = SubmissionStore.all(this).find { it.id == manuscriptId } ?: return@action
            val savedUrl = JournalPageClassifier.canonicalStatusUrl(current)
            CookieManager.getInstance().flush()
            // Unlike the worker's plain HTTP request, this captures the live DOM after
            // React/other client-side rendering. Does not log or persist the HTML.
            activeWebView().evaluateJavascript(
                """(function(){
                    var b=document.body;
                    if(!b) return '';
                    var c=b.cloneNode(true);
                    c.querySelectorAll('script,style,svg,noscript').forEach(function(e){e.remove();});
                    var h=c.outerHTML;
                    return h.length<=1700000 ? h : '';
                })()""".trimIndent()
            ) { result ->
                val html = runCatching { JSONTokener(result).nextValue() as? String }.getOrNull()
                if (!html.isNullOrEmpty() &&
                    EditorialManagerDiscovery.isMenuPage(html, savedUrl)) {
                    SubmissionStore.setEditorialMenu(this, record.id, savedUrl)
                    Toast.makeText(this, "Editorial Manager Author Main Menu saved. The app will check accessible manuscript folders.", Toast.LENGTH_LONG).show()
                    finish()
                    return@evaluateJavascript
                }
                SubmissionStore.addTrackingSource(this, record.id, savedUrl)
                val source = TrackingSource(TrackingKind.fromUrl(savedUrl), savedUrl)
                val parsed = if (!html.isNullOrEmpty() && html.length <= 1_700_000) {
                    if (source.kind == TrackingKind.ELSEVIER_HUB || source.kind == TrackingKind.ELSEVIER_PRODUCTION)
                        TrackingPageParser.elsevierTracking(html, savedUrl)
                    else StatusExtractor.extract(html, record.manuscriptId, record.cssSelector, savedUrl)
                } else ExtractResult.NeedsAttention("Page HTML unavailable or exceeds 1.7 MB.")
                if (parsed is ExtractResult.Found) {
                    SubmissionStore.observeSource(this, record.id, source, parsed)
                        .forEach { AppNotifications.notifyChange(this, it) }
                    Toast.makeText(this, "Tracking started: ${parsed.status}", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    val issue = parsed as ExtractResult.NeedsAttention
                    issue.reviewProgress?.let { SubmissionStore.observeReviewProgress(this, record.id, source, it) }
                    SubmissionStore.recordSourceError(this, record.id, source, issue.reason)
                    val reason = issue.reason
                    pageHint.text = "Tracking page saved, but I couldn't read a clear status yet. " +
                        "Wait until your manuscript and its current status are visible, then tap Track page again. Details: $reason"
                    Toast.makeText(this, "Page saved, but status not yet recognized.", Toast.LENGTH_LONG).show()
                }
            }
        }
        action("Chrome") {
            // Never reuse a transient state/code URL in another browser session.
            if (JournalPageClassifier.classify(url) != JournalPageClassifier.PageKind.POSSIBLE_STATUS_PAGE) {
                Toast.makeText(this, "Edit this manuscript and enter its stable journal login URL first, not the temporary SSO redirect.", Toast.LENGTH_LONG).show()
            } else {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                Toast.makeText(this, "Chrome login is separate: its session is not shared with automatic checks.", Toast.LENGTH_LONG).show()
            }
        }
        outer.addView(toolbar)
        outer.addView(Button(this).apply {
            text = "Fill saved ORCID (official ORCID pages only)"
            textSize = 11f
            isAllCaps = false
            setOnClickListener { fillSavedOrcid() }
        }, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        pageHint = TextView(this).apply {
            textSize = 12f
            setPadding(16, 4, 16, 8)
            text = "Loading journal page..."
        }
        outer.addView(pageHint)
        host = FrameLayout(this)
        outer.addView(host, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(outer)
        keepContentInsideSystemBars(outer)
        primary = makeWebView()
        host.addView(primary, FrameLayout.LayoutParams(-1, -1))
        primary.loadUrl(url)
        updateHint(url)
    }

    private fun updateHint(current: String) {
        pageHint.text = when (JournalPageClassifier.classify(current)) {
            JournalPageClassifier.PageKind.AUTHENTICATION ->
                "Sign-in page: tap the username/password field to use Android Password Manager. If autofill is unavailable, check your phone's Autofill service settings. Resume login can restart an expired redirect while retaining cookies. Passwords are never accessible to background checks."
            JournalPageClassifier.PageKind.NOTIFICATIONS ->
                "This is Springer Nature Notifications, not the journal's manuscript dashboard. Navigate to the editorial submission system for manuscript status."
            JournalPageClassifier.PageKind.POSSIBLE_STATUS_PAGE ->
                if (JournalPageClassifier.isSpringerSubmissionDetail(current))
                    "Springer manuscript detected. When its current status appears, tap Track page. The app will try to check this page periodically."
                else if (EditorialManagerDiscovery.isEditorialManager(current))
                    "Open the Author Main Menu and tap Track page to search its manuscript folders. You can also track a specific manuscript status page."
                else "When you can see your manuscript and its current status, tap Track page. You do not need any HTML settings."
            JournalPageClassifier.PageKind.INVALID ->
                "Unsupported page. Open an HTTPS journal page."
        }
    }

    /** User-triggered only; never inject passwords into a publisher or arbitrary redirect. */
    private fun fillSavedOrcid() {
        val view = activeWebView()
        val current = view.url ?: ""
        if (!OrcidLoginOrigin.isTrusted(current)) {
            Toast.makeText(this, "Open the official HTTPS orcid.org login page first.", Toast.LENGTH_LONG).show()
            return
        }
        val pair = SecureOrcidCredentials.read(this)
        if (pair == null) {
            Toast.makeText(this, "No encrypted ORCID login saved. Configure ORCID vault on the dashboard.", Toast.LENGTH_LONG).show()
            return
        }
        val (username, password) = pair
        // The JavaScript executes only on the exact trusted origin, and only after a tap.
        // Native input setters + input/change events support common React forms.
        val js = """(function(){
            if (location.protocol !== 'https:' ||
                !['orcid.org','login.orcid.org'].includes(location.hostname)) return 'Wrong domain';
            var pass=document.querySelector('input[type=password]');
            if(!pass) return 'No password field on this page';
            var candidates=Array.from(document.querySelectorAll('input'));
            var user=candidates.find(function(e){return e.type==='email' ||
                (e.type==='text' && /email|user|orcid/i.test((e.name||'')+' '+(e.id||'')));});
            if(!user) return 'No username field on this page';
            function assign(el,v){
                var setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;
                setter.call(el,v);
                el.dispatchEvent(new Event('input',{bubbles:true}));
                el.dispatchEvent(new Event('change',{bubbles:true}));
            }
            assign(user,${JSONObject.quote(username)});
            assign(pass,${JSONObject.quote(password)});
            return 'Filled. Review the page and tap Sign in yourself.';
        })()""".trimIndent()
        view.evaluateJavascript(js) { message ->
            val value = runCatching { JSONTokener(message).nextValue() as? String }.getOrNull()
            Toast.makeText(this, value ?: "Autofill attempt finished.", Toast.LENGTH_LONG).show()
        }
    }

    private fun closePopup(popup: WebView) {
        popupStack.remove(popup)
        host.removeView(popup)
        CookieManager.getInstance().flush()
        popup.destroy()
        updateHint(activeWebView().url ?: "")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun makeWebView(): WebView {
        val w = WebView(this)
        // Let the user-selected Android AutofillService (e.g., Google Password Manager)
        // offer website credentials for HTML form controls in this WebView.
        // By default the Android password manager handles login. The separate optional
        // ORCID vault injects a password only after a user tap on the trusted ORCID origin.
        w.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
        w.isFocusable = true
        w.isFocusableInTouchMode = true
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.settings.setSupportMultipleWindows(true)
        w.settings.javaScriptCanOpenWindowsAutomatically = true
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)
        w.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // Auth providers can set cookies during redirects or popup completion.
                // Persist them, but never log or expose cookie contents.
                CookieManager.getInstance().flush()
                if (view === activeWebView() && url != null) updateHint(url)
                super.onPageFinished(view, url)
            }
            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                // The code alone cannot tell invalid credentials from expired CSRF/SSO state.
                if (view === activeWebView() && request?.isForMainFrame == true) {
                    val code = errorResponse?.statusCode ?: 0
                    if (code in 400..599) {
                        pageHint.text = "The website returned HTTP $code. This can be a sign-in state problem, expired session, or server-side refusal. Try Resume login to start a fresh redirect while keeping cookies. Do not repeatedly submit your password."
                    }
                }
                super.onReceivedHttpError(view, request, errorResponse)
            }
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (view === activeWebView() && request?.isForMainFrame == true) {
                    pageHint.text = "Unable to load this page in the embedded browser. If the publisher blocks WebView sign-in, try Chrome. External browser sessions cannot be monitored by this app."
                }
                super.onReceivedError(view, request, error)
            }
        }
        w.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                if (resultMsg == null) return false
                val popup = makeWebView()
                popupStack.add(popup)
                host.addView(popup, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
                val transport = resultMsg.obj as WebView.WebViewTransport
                transport.webView = popup
                resultMsg.sendToTarget()
                return true
            }
            override fun onCloseWindow(window: WebView?) {
                if (window != null && window !== primary) closePopup(window)
            }
        }
        return w
    }

    override fun onDestroy() {
        CookieManager.getInstance().flush()
        popupStack.toList().forEach { closePopup(it) }
        super.onDestroy()
    }
}
