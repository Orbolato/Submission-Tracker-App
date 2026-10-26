# Manuscript Watch v0.1.8

**Experimental pre-release candidate.** This file provides release notes for a future GitHub pre-release; it does not mean a GitHub Release or stable signed APK has been published.

## Highlights

- Simplified manuscript setup without a required CSS selector field.
- Renamed the journal browser action to **Open journal** and the status capture action to **Track page**.
- Preserved the Springer Nature JavaScript-rendered submission-detail status checker added in v0.1.7.
- Preserved the Elsevier/Editorial Manager HTML checking path, manuscript history, Android status-change notifications, and optional encrypted ORCID credential vault.
- Website-only monitoring: no email-notification monitoring or mailbox access.

## Requirements

Android 8.0+ (API 26+). To build, use Android SDK 35, JDK 17, and Gradle 8.9.

## Important limitations

The app cannot obtain all manuscripts from ORCID automatically, cannot guarantee unattended login, and may fail to check pages that require interactive authentication or block background WebView access. Elsevier's generic HTML parser is not validated against every journal. Statuses must be verified against the publisher website.

The project's GitHub Actions pipeline compiles a **debug APK** and runs unit tests, but it does not publish a production-signed release. Debug signing keys may differ across machines or workflow runs and prevent in-place upgrades.

## Getting started

1. Add a manuscript with its identifier and journal website.
2. Open the journal and sign in interactively.
3. Navigate to the manuscript's status page and select **Track page**.
4. Run **Check** to confirm that the current status is recognized.

See the [README](README.md) for supported behavior and [SECURITY.md](SECURITY.md) for sensitive-data handling.
