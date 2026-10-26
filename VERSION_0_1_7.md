# Manuscript Watch v0.1.7 — Springer rendered status checks

## Scope

**Nothing was removed.** All functionality from v0.1.6 remains, including the existing Elsevier/generic HTML checker, Android status-change notifications, manuscript history, Springer login browser, and optional local encrypted ORCID vault. Email monitoring remains absent as requested previously.

## Springer Nature changes

- Saved HTTPS deep links on `submission.springernature.com/submission-details/<UUID>` are handled by `SpringerWebMonitor` rather than a raw `HttpURLConnection` request.
- During a scheduled or manual check, it creates a WebView on Android's main thread, shares the app's existing WebView cookies and DOM storage, permits site JavaScript to render, polls the exact submission page, and extracts **explicitly labelled** current status/stage information.
- The checker ignores OAuth redirects and does not read credentials or automatically sign in. It never records a status change when the session expires or a page is ambiguous. Background checks have a 30-second timeout and report a readable error if they cannot extract the status.
- The interactive journal browser's **Use page** action also reads a smaller copy of the rendered DOM, excluding scripts and styles.
- Existing Elsevier and other site handling in `SiteMonitor` is unchanged.

## Test using your existing phone and account

1. Build and install v0.1.7 in Android Studio using the **same debug signing key**, so your manuscripts and cookies remain intact. If you uninstall first, app data and stored sessions will be lost.
2. Open the app's Springer login browser, sign in if needed, and navigate to an individual `submission-details/` page.
3. Wait for the current stage to appear, tap **Use page**, and check the extracted status shown in the message.
4. On the dashboard, tap **Check** for the Springer manuscript and **Refresh** after the queued check finishes. A recognized current stage should appear with an updated check time and the source `Springer rendered webpage` in status history. Check the error displayed on the manuscript if no status was read.
5. Repeat with an Elsevier manuscript to confirm its prior HTML checker works as before.
6. If the status is visible in the journal browser but **Use page** or **Check** still fails, share a **redacted screenshot of the status section** and the app's error. Avoid sharing passwords, cookies, session tokens or private manuscript text.

## Important limitations

**The app cannot guarantee background monitoring of an authenticated Springer website.** WebView in a background WorkManager job may be limited by Android/HyperOS battery restrictions, publisher bot detection or authentication policies. The status UI may change its labels or structure. The page must still be accessible through the app's own signed-in WebView. We could not test a real signed-in Springer submission or compile the full Android APK in this environment. Unit and source validation are not substitutes for on-device testing. No unattended password replay was added.
