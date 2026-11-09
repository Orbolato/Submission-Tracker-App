# Manuscript Watch v0.2.2 — Release Notes

Manuscript Watch v0.2.2 improves the reliability and transparency of periodic checks, and incorporates the automatic dashboard refresh and minute-based scheduling introduced after v0.2.0. Android application ID remains `org.manuscriptwatch.app` and `versionCode` is **19**.

This is a **source-code release**. No production-signed APK is provided. Experimental APK artifacts built by GitHub Actions can use signing keys incompatible with apps previously installed from Android Studio.

## Highlights

- **Automatic refresh on Check:** Dashboard-wide, per-manuscript, and per-tracking-source checks refresh displayed results after the check finishes. Standalone manual Refresh buttons were removed.
- **Flexible intervals:** Background check presets of 15 and 30 minutes, 1, 3, 6, 12, and 24 hours, plus custom intervals between 15 minutes and 7 days. The default remains 3 hours.
- **Bounded retries for temporary errors:** Transient network/WebView failures, timeouts, and HTTP 408/425/429/5xx responses trigger up to three additional attempts with exponential backoff. Subsequent scheduled cycles continue after retries are exhausted.
- **Verified timestamps:** Tracking-source details distinguish *Last attempt* from *Last verified status*. A successful verification does not require a status change.
- **Manual intervention when needed:** Login expiry, CAPTCHA, and human verification are not bypassed or retried indefinitely. Existing Elsevier Author Hub, production tracking, Springer Nature, and Editorial Manager extraction logic is retained.
- **Backup compatibility:** Minute-based check settings are preserved in new JSON backups; earlier v0.2.0 hourly backups can still be imported.

## Testing

GitHub Actions successfully built the Android APK and ran unit tests for the v0.2.2 development branch and the main branch. The updated periodic-check behavior was also reported as working on the developer's Android phone. Android/HyperOS background scheduling and journal availability cannot be guaranteed, so checks remain **best effort**.

## Installation and signing

The GitHub release publishes **source archives only**, in accordance with the project's release-signing policy. GitHub Actions test artifacts must not be mistaken for production-signed or universally upgradable APKs. To update an existing installation without losing local data, build the project with the same Android Studio debug signing key used to install the app. Do not uninstall to work around a signature mismatch. See [RELEASE_SIGNING.md](RELEASE_SIGNING.md).

## Limitations

- Android may defer or suppress background execution because of power management, network availability, or OEM battery policies. Even a 15-minute configured interval is not a guaranteed deadline.
- Status extraction depends on the journal's current website, authentication state, and authorizations.
- Failed checks do not imply the manuscript's editorial status has changed.
- Manual backups are unencrypted; do not publish confidential manuscript URLs or IDs.

This independent open-source app is not affiliated with publishers. MIT License.
