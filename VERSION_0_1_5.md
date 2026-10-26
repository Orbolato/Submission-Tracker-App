# Manuscript Watch v0.1.5 — Springer detail pages and encrypted ORCID vault

## Springer detail pages

The app recognizes `https://submission.springernature.com/submission-details/<UUID>`
as a *single manuscript detail page*. It removes URL tracking query parameters such as `_gl`
when saving this page. The UUID is an internal page identifier and does not need to
match the editorial manuscript ID entered in the dashboard.

**Use:** Open **Sign in** for the correct paper, navigate to the Springer submission
detail page, wait until the status is visible, tap **Use page**. The browser captures
its *currently rendered DOM* and tries to find an explicit status label/value.
A successful match is recorded locally with source `Rendered journal page`.
If extraction fails, it saves the URL but explains why it cannot read the status.

**Major limitation:** WorkManager's background checker fetches plain HTML using
HttpURLConnection, which does NOT run JavaScript. For React-rendered Springer pages,
a successful foreground status capture does **not** guarantee background status
monitoring. The app reports this as a limitation rather than fabricating a result.
Further work requires a tested publisher-specific connector, a carefully engineered
headless rendering approach where permitted, or a publisher-supported website interface.

## Optional encrypted ORCID login

In the dashboard choose **ORCID vault (optional)** and provide your ORCID user ID / email
and password. The app encrypts both values locally using AES-256-GCM, with an
Android Keystore key. The app declares `allowBackup=false`; it does not store
credentials in plaintext SharedPreferences, transmit them to its own server,
or print them in logs. Tap **Delete saved login** to remove the encrypted blob.

In the app's browser, navigate to a genuine `https://orcid.org/` or
`https://login.orcid.org/` sign-in page, then tap **Fill saved ORCID**. The app only
injects credentials into this verified origin, on user request. The sign-in button
must be pressed by you. This does not bypass captcha, 2FA, bot checks, publisher
policies, or establish a session in Chrome. An untrusted website is refused.

**Not implemented:** automatic background password submission/re-login. It is not
safe or reliable to replay an OAuth login flow over HTTP; dynamic forms, state,
anti-bot protection and login sessions are publisher-specific. The encrypted vault
makes a foreground assisted login possible; background refresh must rely on
valid publisher-issued cookies/session tokens. The Keystore key is configured to
allow decryption without biometrics for optional future background features. A
compromised phone or app process can still potentially capture the decrypted password.

**Build & test:** This source project is uncompiled in this environment. In Android
Studio, synchronize and build the APK, then test against your account. The embedded
publisher DOM structure is not available to the author of this patch, so the live
Springer selector is best-effort and may need adjustment using a redacted screenshot
or DOM sample. Never share credentials, tokens, cookies, manuscript URLs containing
authentication parameters, or private manuscript contents.
