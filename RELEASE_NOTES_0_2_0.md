# Manuscript Watch v0.2.0 — Release Notes

Manuscript Watch v0.2.0 expands the experimental Android submission tracker with multiple publisher-specific tracking sources, reviewer-progress observations, and user-initiated backup and restore. Its Android application ID remains `org.manuscriptwatch.app` and the version code is **17**.

This is an **experimental research-oriented source-code release**. The project is independent of publishers and is not an official Elsevier, Springer Nature, Editorial Manager, or ORCID app. Monitoring remains best effort, and significant decisions or deadlines must be verified with the publisher.

## What's new

### Multiple sources per manuscript

- Store multiple tracking URLs for the same manuscript and inspect each source's last known status, timestamp, and extraction error.
- Check and refresh individual sources, open them inside the app, and remove links without restarting the Tracking links dialog.
- Maintain timestamped status and reviewer-progress observations locally.

### Springer Nature

- Recognize status information on supported `submission-details` pages, including pages that render asynchronously.
- Read explicitly displayed reviewer invitation and acceptance counts, including sentences such as "The editor has invited 6 reviewer(s)".
- Read completed/received reviewer-report counts **only when the publisher reports a number**; otherwise show **Not displayed**, not zero.
- Retain previous status checking and preserve the distinction between reported figures and missing information.

### Elsevier Author Hub

- Capture supported Author Hub status banners and explicit review counters, including lower-bound invitation counts such as **2+**, accepted invitations, and completed reviews.
- Handle some same-host URL changes after a tracking page loads.
- Depend on the current published page markup and available user session; no universal support is claimed.

### Editorial Manager

- Recognize author menus and supported submission-folder table or card layouts.
- Provide **Save Editorial Manager Author Main Menu** and read-only folder discovery for accessible same-host GET links.
- Allow manually opened JavaScript/POST-navigated folders to be recognized in the embedded browser.
- Retain journal-specific limitations: not every folder navigation mechanism can be crawled automatically.

### Elsevier Track Your Accepted Article

- Read explicitly dated milestones in the **Production events** timeline, such as received for production, proofs, final online publication, and print shipment.
- Report the latest recognized dated milestone as the article-production source status, separately from Editorial Manager's editorial acceptance decision.

### Local backup and restore

- Export manuscript records, tracking links, statuses, status-change history, reviewer observations, and the monitoring interval to a user-selected JSON file.
- Validate the JSON before restore and require confirmation to **replace**, not merge, existing local manuscript records.
- Exclude ORCID credentials, Android Keystore keys, and browser cookies/sessions.

**Privacy warning:** Backup JSON is **not encrypted** and may contain confidential manuscript IDs and tracking URLs with access tokens. Store it securely. Do not post backups, real tracking URLs, or credentials to public GitHub issues.

## Verification

- Android Gradle unit tests and APK compilation are run in GitHub Actions using JDK 17 and Android SDK 35.
- Tests cover synthetic Springer, Elsevier Author Hub, Editorial Manager, production-timeline, and backup data cases.
- The release candidate was tested on the author's Android device; this is not a guarantee of compatibility for every journal or device.
- Background WorkManager execution, publisher authentication, session expiry, and notification timing depend on Android and the journal systems. Synthetic tests cannot validate these live dependencies.

## Installation and signing

**This GitHub release distributes tagged source code, not a production-signed APK.** GitHub Actions provides an experimental **debug APK** as a workflow artifact, which is **not** a general-purpose update for existing installations.

- To update an existing installation without deleting private app data, build with the **same local Android Studio signing key** used previously.
- A different debug or production signing key cannot update an existing Android installation in place. Do **not** uninstall a working app to force installation.
- For future distributable APKs, securely create and retain a dedicated release-signing key. A key cannot be reconstructed from an already installed app. See [Release signing and migration](RELEASE_SIGNING.md).
- Make and safeguard a verified backup before any planned signing-key transition. Restored records do not include ORCID credentials or publisher sessions.

See [README](README.md) for build instructions, [phone installation guidance](PHONE_INSTALL.md), and [validation checklist](RELEASE_CANDIDATE_0_2_0.md).

## Known limitations

- Some publisher sites require interactive sign-in, verification, or navigation.
- JavaScript/POST-only Editorial Manager folders may need manual opening before capture.
- Background checks are delayed or restricted by Android power management and publisher access policies.
- The date of the latest production event is **not equivalent to a currently verified editorial status**, and only explicitly recognized event labels are parsed.
- User-initiated backups are not encrypted, and restoring them replaces all existing manuscript records.

## License

MIT License. Copyright (c) 2026 Lucas Orbolato Carvalho. Not affiliated with, authorized by, or endorsed by Springer Nature, Elsevier, ORCID, or journal publishers.
