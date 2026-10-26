# Manuscript Watch v0.1.8

Experimental Android app for tracking editorial submission statuses. Includes Springer Nature's JavaScript-rendered status checker and the existing Elsevier-compatible checking path. Publisher websites and login sessions can change, so successful background monitoring is not guaranteed.

**Privacy:** Do not commit manuscript identifiers, individual tracking links, passwords, ORCID tokens, cookies, `local.properties`, or signing keys. The source includes only synthetic identifiers for test pages. Runtime credentials and manuscript records are stored on the device, not in this repository.

**Build:** Open this folder in Android Studio, configure SDK 35 / JDK 17, and run `gradlew.bat assembleDebug` on Windows. This repository contains source code, not a signed production APK. [GitHub Actions](https://github.com/Orbolato/Submission-Tracker-App/actions/workflows/build-apk.yml) builds a downloadable *debug* APK and runs unit tests; the CI debug signing key may differ from your local Android Studio key. An APK signed with a different key cannot update an installed app without preserving the original signing identity.

---

> **v0.1.7:** Added best-effort background **WebView/JavaScript status checks for Springer Nature SNAPP submission-detail URLs**, reusing the app's signed-in WebView session. Existing Elsevier and other publisher HTML checks remain unchanged. No functionality was removed. See [VERSION_0_1_7.md](VERSION_0_1_7.md) for testing and limitations.

> **v0.1.6:** Website-only monitoring. Removed email notification access, listener, UI toggle, and email-preview parsing. Android alerts for changes detected from website checks remain enabled. See [VERSION_0_1_6.md](VERSION_0_1_6.md).

> **v0.1.5:** Springer submission-details links supported, foreground rendered-DOM status capture, optional encrypted ORCID credential vault and user-triggered autofill. **Background monitoring of JavaScript-only pages remains unsupported.** See [VERSION_0_1_5.md](VERSION_0_1_5.md).

> **v0.1.4:** Android Password Manager integration: WebView login fields explicitly participate in autofill. No password access, storage or background auto-login. See [PASSWORD_MANAGER.md](PASSWORD_MANAGER.md).

> **v0.1.3:** Springer first-login recovery: Resume login from the stable journal URL while preserving cookies, persist cookies after navigation/popup closing, and show HTTP error hints. Not a guarantee of automated login. See [LOGIN_FIRST_ATTEMPT.md](LOGIN_FIRST_ATTEMPT.md).

> **Springer Nature sign-in handling (v0.1.2):** The browser identifies OAuth/gateway redirects (including `idp-personal-authenticator.springernature.com/gateway`) and refuses to save them as editorial status pages. It distinguishes the Springer Nature Notifications service from actual editorial submission systems. WebView sign-in may still be rejected by the provider; the external Chrome button does **not** transfer Chrome sessions to background monitoring. See [SPRINGER_AUTH.md](SPRINGER_AUTH.md).

> Android 15 / Xiaomi HyperOS layout update (v0.1.1): See [SAFE_AREA_FIX.md](SAFE_AREA_FIX.md) for the top/bottom system-bar fix and testing steps.

# Manuscript Watch — Android source project (experimental MVP)

An on-device Android starter app for tracking manuscripts across journal systems.

**What it actually does**

- Add and edit manuscripts, journals, IDs, current status, and links.
- Record a timestamped local history whenever a status changes.
- Periodically request **user-configured HTTPS status pages** using Android WorkManager (default every 3 hours, subject to battery optimization). Android notifications are sent only for website-detected changes.
- The app tries to recognize the current status automatically from the selected journal page, without requiring HTML knowledge. Previously saved custom detection rules remain supported for existing records. It refuses to guess if matching fails.
- Launch a journal browser where **you personally sign in** (optionally using ORCID if the publisher enables it). Navigate to the submissions/revisions list, then tap **Track page** to save the exact status URL. Android WebView retains its own first-party session cookies where the portal permits this.
- No app account, cloud service, backend or third-party analytics. Optional encrypted ORCID credential storage is available only after user opt-in; see v0.1.5 notes. Data is stored locally in app-private SharedPreferences; WebView handles session cookies independently.

**What is NOT implemented or guaranteed**

- **There is no universal ORCID-based retrieval of all in-progress submissions.** ORCID authentication into this app would not give it access to the journals' records. Editorial Manager sites can offer ORCID sign-in individually after the journal account is linked; other publishers may not.
- Springer `submission.springernature.com/submission-details/<UUID>` links now use a separate WebView-based monitor that waits for JavaScript content, reuses WebView session storage, and reports sign-in expiry and parsing failures without guessing a status. This may fail under publisher security controls or Android background restrictions.
- There is **no guaranteed general-purpose scraper** for Editorial Manager, ScholarOne, Editorial Express or bespoke portals. Their author pages often require authentication, JavaScript, anti-automation protections, and publisher-specific navigation. The generic HTML checker may fail even when login succeeds. Journal-by-journal adapters and real-account testing are needed for robust support.
- The embedded browser may be disallowed by some OAuth providers. The **Chrome** fallback opens the external browser, whose cookies the app cannot import. The app **never** attempts to bypass CAPTCHAs, bot protections or access restrictions.
- **Compilation has passed in GitHub Actions**. This does not establish publisher compatibility or reliable background monitoring on a physical Android device. Test with your own accounts before relying on notifications.
- No claim that all publishers authorize polling/scraping; follow the journal's terms, use gentle check intervals and stop on access restrictions.
- No push notification server. WorkManager checks run locally and can be delayed or suppressed by Android battery management. Force-stopping the app suspends scheduled work until the user opens it again.
- The app does **not** discover new submissions automatically: add manuscripts manually initially.

## Phone-only GitHub build

To build without Android Studio, go to [Build Android APK](https://github.com/Orbolato/Submission-Tracker-App/actions/workflows/build-apk.yml), choose **Run workflow**, open its completed run, and download the debug APK under **Artifacts**. Do not uninstall an existing app to install an APK signed by a different debug key: uninstalling clears local manuscript data and authenticated sessions. [PHONE_INSTALL.md](PHONE_INSTALL.md) describes a legacy setup for creating a separate GitHub repository; it is not needed when using this one.

## Build and install (Android Studio)

1. Install Android Studio (with **Android SDK 35**, JDK 17, and an emulator or Android device).
2. Open the `manuscript-watch-android` directory as an Android Studio project.
3. If Gradle sync complains about missing wrapper JAR, run `./gradlew help` from a terminal once (or `gradlew.bat help` on Windows). This fetches the official Gradle wrapper JAR from Gradle's tagged source. Alternatively, with a locally installed Gradle, run `gradle wrapper --gradle-version 8.9`. **Network access required**.
4. Run the `app` configuration on an Android device or emulator running Android 8.0+ (API 26+). Android 13+ prompts for notification permission.

## How to set up a journal

1. **Add paper**: enter title, journal, manuscript ID and the editorial portal HTTPS login URL (for example, its Editorial Manager journal sign-in page). Set the current known status if desired.
2. Tap **Open journal** and authenticate manually. If the journal offers an ORCID button, use that. Link ORCID to your existing journal account first if needed.
3. From the portal's **Author Main Menu**, navigate into the manuscript's relevant folder, such as **Submissions Being Processed**, **Revisions Being Processed**, or **Submissions with a Decision**. Once the status table is visible, tap **Track page**.
4. Tap **Check** for the manuscript. Open the app and tap **Refresh** after the check completes. If it shows an attention message, the page was not accessible or not parseable. Check the status-list URL. For a nonstandard HTML layout, open the exact manuscript-status page in the journal browser and select **Track page**. If the status cannot be identified, the page may require a publisher-specific adapter.
5. Choose **Check interval** (1, 3, 6, 12 or 24 hours). The default 3 hours minimizes unnecessary requests. You can edit the manuscript later or record a manual status.

The **Current Status** column is used in Editorial Manager author manuscript folders, but its labels and layout may vary by journal.

## Architecture

| Module | Responsibility |
|---|---|
| `MainActivity.kt` | Android native dashboard, manual entry, history and configuration |
| `SubmissionStore.kt` | On-device JSON persistence and event log |
| `BrowserActivity.kt` | First-party journal browser and cookie-bearing sign-in session |
| `SiteMonitor.kt` | Restricted HTTPS HTML retrieval with same-host redirect handling (existing Elsevier/general path) |
| `SpringerWebMonitor.kt` | Background WebView-based Springer submission-detail checks, using JavaScript and existing cookies; timeouts/auth failures return attention |
| `StatusExtractor.kt` | Manuscript-scoped HTML status parsing and error states |
| `MonitorWorker.kt` | Periodic/one-time WorkManager checks |
| `AppNotifications.kt` | OS alerts when a status change is detected |
| `StatusExtractorTest.kt` | Unit tests for table matching, legacy rules, and Springer detail-page extraction |


## Project maturity and sharing

**Status: experimental prototype.** Source availability and a successful CI build do not guarantee that status extraction will work reliably for Elsevier, Springer Nature, or other portals. Each publisher can change its pages, authentication requirements, or access policies without notice. Monitoring may be delayed or blocked by Android background execution policies. Please verify important submission updates directly with the journal.

**Credentials and privacy:** ORCID credential storage is optional, local, and encrypted using Android Keystore; the optional vault decrypts credentials only after a user action in the app's embedded browser. The app does not implement unattended password login. Journal session cookies and manuscript metadata are local app data. Do not share private status links, cookies, passwords, keystores, or actual manuscript IDs in issues, screenshots, or commits.

**Licensing:** No open-source license has been selected yet. Public availability does not by itself grant permission to redistribute or modify the code. A license must be added before presenting the project as openly reusable software.

**Sharing vs. installing:** You may share this repository as public *source code* for evaluation. The GitHub-generated APK is a development/debug build, not a production release. GitHub-runner signing keys may differ from the key used for an existing phone installation. Avoid distributing a debug APK as an official release until signed-release, privacy, and security reviews are complete.

## Security and privacy

- Do **not** paste passwords, ORCID tokens or cookies into the journal URL or tracking-page fields. Authenticate only in the publisher's website. Site monitoring uses session cookies held by Android WebView if available.
- Manuscript metadata is private app data, **not encrypted with a separate application key**. Use device encryption and screen lock. Disabling backups avoids automatic cloud copies; uninstalling the app deletes data.
- Only HTTPS status URLs are accepted; page redirects to another host are not followed while fetching a manuscript, to avoid sending cookies cross-origin.
- Status extraction is deliberately conservative: no status is inferred if the manuscript isn't found. The app may still miss changes and is not a source of authoritative editorial decisions.

## Improving this into a reliable daily-use app

Next step: develop and validate specific adapters for the journal portals you use (starting with individual Editorial Manager journals). An adapter needs authenticated examples of the author manuscript table **without passwords or exported cookies**, tests covering revised submissions, and an explicit approach for session expiration. For portals that block background scraping, a publisher-approved website integration would be needed; the app will report the limitation instead of guessing.

## References

- Elsevier: ORCID single sign-on to Editorial Manager — https://www.elsevier.support/publishing/answer/how-can-i-use-orcid-for-single-signon-to-editorial-manager
- Elsevier: Tracking Editorial Manager author statuses — https://www.elsevier.support/publishing/answer/how-can-i-track-the-status-of-my-submitted-article
- Elsevier: Editorial status descriptions — https://www.elsevier.support/publishing/answer/what-does-the-status-of-my-submission-mean-in-editorial-manager
- Android: WorkManager periodic scheduling — https://developer.android.com/reference/androidx/work/PeriodicWorkRequest
