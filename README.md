# Manuscript Watch

[![Android APK build and unit tests](https://github.com/Orbolato/Submission-Tracker-App/actions/workflows/build-apk.yml/badge.svg)](https://github.com/Orbolato/Submission-Tracker-App/actions/workflows/build-apk.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**Manuscript Watch** is an experimental, open-source Android app for tracking academic journal submissions and revisions across editorial websites. It helps researchers keep manuscript statuses, check history, and status-change notifications in one place.

The current implementation focuses on **Springer Nature** and **Elsevier / Editorial Manager**. Journal interfaces, login sessions, and access policies vary. A successful build does not mean that monitoring is reliable for every journal or on every Android device.

## Features

- Track manuscript titles, journals, identifiers, submission links, and current statuses.
- Open a journal's website inside the app and save its manuscript-status page with **Track page**, without entering HTML or CSS selectors.
- Read supported status pages, including JavaScript-rendered Springer Nature submission-detail pages, using the app's authenticated WebView session.
- Preserve a local status history and send Android notifications when a website check detects a change.
- Schedule checks with Android WorkManager (selectable intervals of 1, 3, 6, 12, or 24 hours; default: 3 hours).
- Optionally store ORCID credentials encrypted with Android Keystore for **user-triggered** autofill on verified ORCID login pages.
- Store manuscript information locally, without an application account, analytics service, or email-monitoring integration.

## Current support and limitations

| Website or feature | Current approach | Limitations |
|---|---|---|
| Springer Nature submissions | WebView/JavaScript monitoring of individual `submission-details/` pages | Depends on an authorized session, the site's page structure, and Android background execution |
| Elsevier / Editorial Manager | Authenticated HTML retrieval and manuscript-specific status extraction | Journal-specific layouts, redirects, or JavaScript pages may not work |
| Other editorial systems | Experimental generic HTML checking | No verified publisher-specific adapter |
| ORCID | Interactive login on publisher websites; optional encrypted local credentials | ORCID does not provide a universal API for private submission statuses or unattended login |

**Important:** This project does not automatically discover manuscripts from ORCID. Website access requires you to sign in through each authorized journal portal. Browser sessions may expire. CAPTCHA, multi-factor authentication, and other interactive steps are not bypassed. The app does not silently submit saved ORCID credentials.

Manuscript status monitoring is **best effort**. Android may delay periodic checks, and websites can restrict automated access. Verify important decisions and deadlines directly with the journal. Use polling responsibly and respect publisher terms.

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

**Updating an installed app:** debug builds from GitHub and your PC may use different signing keys. An APK signed by a different key cannot replace an existing installation while preserving app data. Do not uninstall an existing installation just to force an update: local manuscript records, status history, encrypted credentials, and publisher sessions would be lost. See [phone installation instructions](PHONE_INSTALL.md).

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
- App backups are disabled. There is no synchronization server or email monitoring.
- Never include private manuscript IDs, tracking URLs, passwords, cookies, access tokens, or keystores in issues, commits, or shared screenshots.

For vulnerability reporting, see [SECURITY.md](SECURITY.md).

## Development and contributing

The project is a research-oriented prototype and is **not affiliated with or endorsed by Springer Nature, Elsevier, ORCID, or any publisher**. Its core source is under `app/src/main/java/org/manuscriptwatch/app/`, with tests under `app/src/test/`.

Contributions and reproducible, **redacted** bug reports are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md), and read the [v0.1.8 release notes](RELEASE_NOTES_v0.1.8.md) for current scope. Previous engineering notes are retained in the version-specific Markdown files.

## License

Released under the [MIT License](LICENSE). Copyright (c) 2026 Lucas Orbolato Carvalho.
