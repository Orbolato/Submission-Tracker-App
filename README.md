# Manuscript Watch

[![Android APK build and unit tests](https://github.com/Orbolato/Submission-Tracker-App/actions/workflows/build-apk.yml/badge.svg)](https://github.com/Orbolato/Submission-Tracker-App/actions/workflows/build-apk.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**Manuscript Watch** is an experimental, open-source Android app for tracking academic journal submissions and revisions across editorial websites. It helps researchers keep manuscript statuses, check history, and status-change notifications in one place.

The current implementation focuses on **Springer Nature** and **Elsevier / Editorial Manager**. Journal interfaces, login sessions, and access policies vary. A successful build does not mean that monitoring is reliable for every journal or on every Android device.

## Features

- Track manuscript titles, journals, identifiers, submission links, and current statuses.
- Save multiple tracking sources per manuscript and inspect each source’s last check and error separately.
- Capture explicitly displayed peer-review counts (invitations sent, acceptances, and completed reports) with local observation history; undisclosed completed counts remain **Not displayed**.
- Interpret dated post-acceptance milestones from supported Elsevier article-production tracking pages.
- Open a journal's website inside the app and save its manuscript-status page with **Track page**, without entering HTML or CSS selectors.
- Read supported status pages, including JavaScript-rendered Springer Nature submission-detail pages, using the app's authenticated WebView session.
- Preserve a local status history and send Android notifications when a website check detects a change.
- Schedule best-effort checks with Android WorkManager using presets of 15 or 30 minutes, 1, 3, 6, 12, or 24 hours, or a custom interval of 15–10,080 minutes (default: 3 hours). Android may delay periodic checks.
- Optionally store ORCID credentials encrypted with Android Keystore for **user-triggered** autofill on verified ORCID login pages.
- Manual **Check** actions refresh the dashboard and tracking-source details automatically when the background check completes.
- Store manuscript information locally, without an application account, analytics service, or email-monitoring integration.
- Export manuscript records, tracking sources, status/reviewer histories, and the check interval to a user-selected JSON backup; restore after validation and explicit replacement confirmation. Passwords and browser sessions are never exported.

## Current support and limitations

| Website or feature | Current approach | Limitations |
|---|---|---|
| Springer Nature submissions | WebView/JavaScript monitoring of individual `submission-details/` pages and explicit reviewer-count sentences | Depends on an authorized session, the site’s page structure, and Android background execution |
| Elsevier Author Hub | Renders submission tracking pages and extracts explicit editorial status and reviewer counts | Site layouts and access requirements can change; successful capture is not guaranteed |
| Elsevier Track Your Accepted Article | Reads dated events in the production timeline, retaining a separate production-source status | Event wording varies; the newest recognized event is a production milestone, not necessarily the editorial decision |
| Elsevier Editorial Manager | Manuscript-specific status extraction and discovery from accessible author folders | JavaScript- or POST-only navigation may require opening the folder manually inside the app |
| Other editorial systems | Experimental generic HTML checking | No verified publisher-specific adapter |
| ORCID | Interactive login on publisher websites; optional encrypted local credentials | ORCID does not provide a universal API for private submission statuses or unattended login |

**Important:** This project does not automatically discover manuscripts from ORCID. Website access requires you to sign in through each authorized journal portal. Browser sessions may expire. CAPTCHA, multi-factor authentication, and other interactive steps are not bypassed. The app does not silently submit saved ORCID credentials.

Manuscript status monitoring is **best effort**. Android may delay periodic checks, and websites can restrict automated access. Verify important decisions and deadlines directly with the journal. Use polling responsibly and respect publisher terms.

## Manual backup and restore (v0.2.0)

From the dashboard, select **Backup records**, confirm that the file contains sensitive manuscript information, and choose a private storage location using Android's document picker. To recover data, select **Restore records**, choose a JSON backup, review the manuscript count, and explicitly confirm replacement.

**Restoring replaces the entire set of locally saved manuscripts**, including per-source observations and histories. It is not a merge. The app validates the backup's format, size (2 MB limit), manuscript identifiers, and tracking URLs before writing it. New backups preserve minute-based check intervals, while backups from v0.2.0 using hourly intervals remain restorable. Keep a second copy of an existing backup before trying restore for the first time. Do not uninstall your existing app just to test restore.

Backups are **not encrypted**. They may include manuscript identifiers, journal and tracking URLs (including private tokens embedded in URLs), statuses, and review histories. Store them securely and do not upload them to public repositories or issue trackers. **ORCID credentials, Android Keystore keys, browser cookies, and publisher sessions are excluded**; after moving to a different phone, sign in to journal websites again. Android automatic app backup remains disabled.

## Requirements

- Android 8.0 (API 26) or newer
- Android Studio with Android SDK 35 and JDK 17 for local builds
- Gradle 8.9 (configured for the project)

## Build from source

Clone the repository and open it as an Android Studio project:

```bash
git clone https://github.com/Orbolato/Submission-Tracker-App.git
```

In Android Studio, let Gradle synchronize and run the `app` configuration on an emulator or a connected Android device. On Windows, you can build the debug APK using:

```powershell
.\gradlew.bat assembleDebug
```

The resulting file is `app/build/outputs/apk/debug/app-debug.apk`.

The [Build Android APK workflow](https://github.com/Orbolato/Submission-Tracker-App/actions/workflows/build-apk.yml) runs unit tests and builds a downloadable **debug APK**. Find it in the successful run's **Artifacts** section. This is not a signed production release.

**Updating an installed app:** debug builds from GitHub and your PC may use different signing keys. An APK signed by a different key cannot replace an existing installation while preserving app data. Do not uninstall an existing installation just to force an update: local manuscript records, status history, encrypted credentials, and publisher sessions would be lost. The **v0.2.0 GitHub release is source-only**, not a production-signed APK. See [phone installation instructions](PHONE_INSTALL.md) and [release-signing and migration guidance](RELEASE_SIGNING.md).

## Getting started

1. Select **Add paper** and enter the manuscript title, journal, manuscript ID, and journal sign-in URL.
2. Select **Open journal**, authenticate with the journal, and navigate to the page displaying the manuscript status.
3. Select **Track page** once the status is visible.
4. Return to the dashboard and select **Check** to verify extraction. You can also set a periodic check interval.

If the app cannot identify an unambiguous status or the login session expires, it displays a message instead of guessing. Track original submissions and revisions individually when the publisher assigns distinct pages or identifiers.

## Privacy and security

- The app uses on-device app-private storage. Manuscript metadata is **not separately encrypted**; protect your device with a screen lock and Android device encryption.
- ORCID credential storage is optional and uses AES-GCM encryption with an Android Keystore key. Saved credentials are only filled after a user action on verified ORCID pages, not by automatic background login.
- Publisher sessions use WebView cookie storage, which is independent of Chrome.
- Android automatic app backup is disabled. User-initiated, unencrypted JSON export and confirmed restore are available in v0.2.0. There is no synchronization server or email monitoring.
- Never include private manuscript IDs, tracking URLs, passwords, cookies, access tokens, or keystores in issues, commits, or shared screenshots.

For vulnerability reporting, see [SECURITY.md](SECURITY.md).

## Development and contributing

The project is a research-oriented prototype and is **not affiliated with or endorsed by Springer Nature, Elsevier, ORCID, or any publisher**. Its core source is under `app/src/main/java/org/manuscriptwatch/app/`, with tests under `app/src/test/`.

Contributions and reproducible, **redacted** bug reports are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md), and read the [v0.2.0 release notes](RELEASE_NOTES_0_2_0.md) for current scope. Prior [v0.1.8 release notes](RELEASE_NOTES_0_1_8.md) and engineering notes remain available.

## License

Released under the [MIT License](LICENSE). Copyright (c) 2026 Lucas Orbolato Carvalho.
