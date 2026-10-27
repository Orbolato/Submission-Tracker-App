# Manuscript Watch v0.2.0-rc.1 — Validation checklist

This is a **development release candidate**, not a stable release. Do not merge `feature/v0.2.0` into `main` or publish a public production APK until the device checks below pass.

## Build and data safety

- [ ] GitHub Actions tests and APK compilation pass on the exact candidate commit.
- [ ] Use the **same Android Studio installation/signing key** to upgrade a phone with existing manuscripts. Do not uninstall the previous version.
- [ ] Verify that all pre-existing manuscripts, source URLs, statuses, status histories, and reviewer histories remain visible.
- [ ] Export with **Backup records**, saving the JSON in a private location. Verify a nonempty file was created.
- [ ] Test **Restore records** on a **separate test installation** or after creating a verified additional backup. Confirm the preview manuscript count, cancel once to verify no changes occur, then confirm a restore.
- [ ] Verify that restoring preserves the manuscript identifiers, source URLs, saved statuses, reviewer progress, and histories.
- [ ] Check that malformed/unrelated JSON is rejected **before** any data is replaced.
- [ ] Check the restore experience on an installation where the backup contains fewer manuscripts than are currently saved, confirming that replace (not merge) is understood.
- [ ] Verify ORCID credentials and cookies are **not** present in the exported JSON. On a replacement phone, log in manually: journal sessions are not included.
- [ ] Keep JSON backups private, especially those containing non-public manuscript IDs or tracking links with query tokens.

## Publisher smoke tests

- [ ] **Springer Nature:** Open a submission-detail page in the app. Confirm current status, invited count, accepted count, and *Completed: Not displayed* when no count is shown. Verify a new observation updates history without inventing values.
- [ ] **Elsevier Author Hub:** Open and check a page. Confirm its reviewer counts, including exact values, `2+` lower bounds, and an explicitly reported completed count of zero.
- [ ] **Editorial Manager:** Save the Author Main Menu. Confirm it is not counted as an unsuccessful tracking source. Locate a manuscript inside Submissions Being Processed, then another inside Submissions With a Decision if available.
- [ ] **Elsevier accepted-article tracker:** Confirm the latest dated Production events milestone is read and displayed separately from the editorial acceptance decision.
- [ ] Verify source-specific **Check**, **Refresh tracking results**, and **Remove** work without closing the dialog.
- [ ] Check that a failed website read shows a source-specific error without deleting previously known good observations.

## Background and notification behavior

- [ ] Run a one-time check for each source; verify last attempt time and source-specific success/error.
- [ ] Set a permitted interval and verify it survives app restart and backup/restore.
- [ ] Allow Android notifications, then verify a **real change in a previously observed status** triggers one notification; no notification should be fabricated for a first observation or a failed login.
- [ ] Check session-expiration behavior: an expired publisher login should produce an attention message, not a false change in status.
- [ ] Observe at least one scheduled WorkManager check on the actual device; battery-saving policies may delay it.

## Release signing and distribution

- [ ] Create and securely retain a dedicated release-signing keystore **outside the public repository**; make a protected backup of it.
- [ ] Configure a separate production signing process using secure CI secrets or a local offline keystore. Never commit keystore files, passwords, cookies, or manuscript URLs.
- [ ] Verify signing certificates before upgrading an installed app. A release key different from the existing debug signing key **cannot replace that installation in place**. Document a safe transition and backup-first recovery procedure.
- [ ] Publish public artifacts only after the release candidate passes the device tests, and clearly label any remaining publisher-specific limitations.
- [ ] Update public release notes and versioning on `main` only after approval.

## Current CI scope

The automated workflow runs `testDebugUnitTest` and `assembleDebug`. Those tests use synthetic HTML and JSON. They do **not** verify live authenticated publisher sites, Android WorkManager scheduling on a phone, notifications, system document-provider behavior, or keystore compatibility.
