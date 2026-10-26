# Manuscript Watch

**An experimental Android app for monitoring journal manuscript submission statuses.**

Manuscript Watch helps researchers keep track of submitted and revised papers across editorial websites from one on-device dashboard. It records status changes and can display local Android notifications when a change is detected.

[![Android CI](https://github.com/Orbolato/Submission-Tracker-App/actions/workflows/build-apk.yml/badge.svg)](https://github.com/Orbolato/Submission-Tracker-App/actions/workflows/build-apk.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

> **Development status: experimental v0.1.8.** The project builds and its included unit tests run in GitHub Actions. Publisher-specific sign-in, JavaScript rendering, and Android background restrictions may prevent successful checks. Always confirm important editorial decisions directly with the publisher.

## Features

- **Manuscript dashboard:** Manage manuscript titles, journals, IDs, status-page links, and current statuses.
- **Status history:** Retain timestamped changes for each tracked manuscript.
- **Website monitoring:** Schedule local checks using Android WorkManager; choose a check interval from 1 to 24 hours.
- **Springer Nature:** A best-effort WebView checker reads JavaScript-rendered individual submission-detail pages using the app's existing signed-in WebView session.
- **Elsevier and other editorial portals:** A general HTTPS/HTML checker looks for manuscript-specific statuses in author pages. Coverage depends on each publisher's page structure.
- **Notifications:** Android alerts for detected website status changes. No email monitoring is included.
- **Optional ORCID credential vault:** User-approved AES-256-GCM encryption with an Android Keystore key and user-triggered filling on verified ORCID pages. The app does **not** submit passwords automatically in the background.
- **Local-first storage:** Manuscript data and session information stay on the device; the app does not require a dedicated server or app account.

## Supported environment

- Android 8.0 or newer (API 26+).
- Android Studio with Android SDK 35 and JDK 17, or the provided GitHub Actions build workflow.
- A legitimate author account and permission to access each journal's editorial website.

**Publisher coverage is experimental, not certified.** Springer Nature and Elsevier have not sponsored, endorsed, or authorized this independent project.

## Getting started

1. Add a manuscript and the journal's stable HTTPS login URL.
2. Tap **Open journal** and complete the publisher's normal sign-in process. Use ORCID where the publisher supports it.
3. Navigate to the manuscript's actual status page and tap **Track page**.
4. Tap **Check**, then **Refresh**, to see whether the app successfully recognized the current status.
5. Select the background check interval and allow Android notifications if desired.

You do **not** need to enter CSS selectors, inspect HTML, or share your ORCID password to use normal website monitoring.

### Build and test with Android Studio

Open the repository root (the directory containing `settings.gradle.kts`) and build with JDK 17 and Android SDK 35:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

The debug APK appears under `app/build/outputs/apk/debug/app-debug.apk`.

### Build with GitHub Actions

Use [Build Android APK](https://github.com/Orbolato/Submission-Tracker-App/actions/workflows/build-apk.yml) and download the debug APK from a successful run's **Artifacts** section. See [PHONE_INSTALL.md](PHONE_INSTALL.md) for installation and update precautions.

**Signing warning:** GitHub-hosted debug builds may use a different signing key from Android Studio on your PC. An APK with a different signature cannot update an installed copy of Manuscript Watch in place. **Do not uninstall** a working installation merely to bypass this limitation: uninstalling removes local manuscript records, status history, and stored publisher sessions.

## How monitoring works

| Component | Function |
|---|---|
| `MainActivity.kt` | Manuscript dashboard and configuration |
| `BrowserActivity.kt` | Embedded browser for interactive journal sign-in and page selection |
| `SubmissionStore.kt` | Local manuscript records and status history |
| `SpringerWebMonitor.kt` | Best-effort JavaScript-rendered Springer Nature status checks |
| `SiteMonitor.kt` | HTML-based status checks for Elsevier and other portals |
| `StatusExtractor.kt` | Manuscript-scoped status parsing and ambiguous-result handling |
| `MonitorWorker.kt` | Scheduled and manual WorkManager checks |
| `SecureOrcidCredentials.kt` | Optional encrypted, user-triggered ORCID credential storage |

## Important limitations

- **ORCID sign-in is not a universal submissions API.** Manuscripts must be added manually, and each publisher controls its own account and session.
- **Session persistence is not guaranteed.** Publishers can expire cookies, require CAPTCHA or multifactor authentication, or reject embedded browsers.
- **Monitoring is best effort.** JavaScript-heavy pages and publisher layout changes may prevent status extraction. The app should report a failed check rather than invent a status.
- **Background execution is not continuous.** Android and device manufacturers can delay or suspend scheduled checks.
- **No production release or security certification.** This repository is an early prototype, not a substitute for checking an official editorial portal.

## Privacy and security

- Do not include credentials, access tokens, authentication cookies, private manuscript URLs, or confidential paper details in GitHub issues or commits.
- Saved ORCID credentials are optional and encrypted at rest using Android Keystore. They are only filled after an explicit action on trusted ORCID pages.
- Manuscript metadata is stored in app-private storage, but does not have a separate app-level encryption key; Android device encryption and a screen lock are recommended.
- Cloud backup of the app's data is disabled. Uninstalling the app will remove locally stored records.
- Publisher site-access terms and human-verification requirements must be respected.

To report a possible security issue, see [SECURITY.md](SECURITY.md). See [CONTRIBUTING.md](CONTRIBUTING.md) for development guidance.

## Documentation

- [Phone installation and APK signing](PHONE_INSTALL.md)
- [Springer authentication notes](SPRINGER_AUTH.md)
- [ORCID and Android Password Manager](PASSWORD_MANAGER.md)
- [Version 0.1.8 details](VERSION_0_1_8.md)
- [Springer rendered-page monitoring details](VERSION_0_1_7.md)

## License

Released under the [MIT License](LICENSE). Copyright (c) 2026 Lucas Orbolato Carvalho.

This project is independent of ORCID, Elsevier, and Springer Nature. Publisher names are used solely to describe website compatibility.
