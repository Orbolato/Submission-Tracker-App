# Manuscript Watch v0.1.8 — Experimental Pre-release

**Development preview for researchers tracking journal manuscript submissions.**

This pre-release provides the Android source code for an experimental, local-first journal submission tracker. This is **not a production-ready app** or a guarantee of background status monitoring.

## Features

- Dashboard for manuscripts, journal names, IDs, current statuses, and timestamped status history.
- Local WorkManager scheduling and Android notifications for detected website status changes.
- Best-effort Springer Nature submission-detail status extraction using JavaScript-rendered WebView content.
- General HTML status extraction for Elsevier/Editorial Manager and other supported site layouts.
- Interactive publisher login with retained WebView cookies where permitted.
- Optional locally encrypted ORCID credentials, filled only on an explicit user action on trusted ORCID hosts.
- Simpler **Open journal → Track page** workflow without requiring CSS selectors.

## Validation

- The Android app compiled in GitHub Actions using Android SDK 35 and JDK 17.
- The included JVM unit tests passed in GitHub Actions.
- Authenticated Elsevier and Springer status monitoring is not covered by these CI tests and must be validated on each device and publisher account.

## Known limitations

- ORCID does not provide universal access to journal submission statuses.
- Authentication can expire or require interactive login.
- Background checks may be delayed or blocked by Android battery restrictions or publisher access controls.
- Publisher page changes can prevent status recognition, and the app may report that a manual check is necessary.

## Installation and signing

This release marks the **source code** as a development preview. Experimental debug APKs are available under the [GitHub Actions workflow](https://github.com/Orbolato/Submission-Tracker-App/actions/workflows/build-apk.yml), but these are **not stable signed releases**. They may not be able to update an existing installation signed with a different key. **Do not uninstall a working copy just to try a debug APK**, because that will remove locally stored manuscript data and browser sessions.

For build and setup instructions, see the repository [README](https://github.com/Orbolato/Submission-Tracker-App#readme) and [phone installation guide](https://github.com/Orbolato/Submission-Tracker-App/blob/main/PHONE_INSTALL.md).

## Licensing

Copyright (c) 2026 Lucas Orbolato Carvalho. Released under the [MIT License](https://github.com/Orbolato/Submission-Tracker-App/blob/main/LICENSE).

Independent project. Not affiliated with, authorized by, or endorsed by ORCID, Elsevier, or Springer Nature.
