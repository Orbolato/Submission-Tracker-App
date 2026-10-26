# Manuscript Watch v0.2.0 development preview

**This is a development branch, not a stable release.** v0.1.8 remains the published experimental baseline.

## Implemented in this branch

- Backward-compatible multiple tracking sources per manuscript; older `statusUrl` records remain readable.
- New **Tracking links** UI to add or remove read-only HTTPS tracking pages without CSS selectors.
- Dedicated, JavaScript-rendered Elsevier Author Hub and accepted-article production link checking, restricted to the expected Elsevier hosts. Status recognition requires explicit labels.
- Springer reviewer invitation, acceptance, and report counters when explicitly available in the page. Missing fields remain unknown, and changes are recorded in a separate timestamped history.
- Best-effort Editorial Manager Author Main Menu discovery: recognize the menu in the app browser, save it once, scan safe same-host GET folder links and limited pagination, and locate the manuscript by its exact ID.
- Per-source status observations with a preferred display status, plus synthetic parser and discovery tests.
- Incremented Android version code to 10 while keeping `org.manuscriptwatch.app` and the previous Android storage keys for in-place upgrades when signed with the same key.

## Not yet verified

- The provided Elsevier tracking URLs could not be inspected from the authoring environment. Their exact live HTML and API behavior remain unknown. Parsing tests use synthetic examples only.
- Editorial Manager varies by journal and often uses POST or JavaScript navigation; this first adapter intentionally does not issue POST requests or execute links that can change submissions. Some journals will require a manual tracking link.
- Springer page counters may be labeled differently; this branch only captures explicitly labeled counts and does not infer them from timeline prose.
- Android background scheduling, session lifetime, and all four publisher integrations require on-device verification.
- GitHub Actions debug APKs may not share the local Android Studio signing key. Do not uninstall the working v0.1.7/v0.1.8 installation; doing so deletes your on-device data.

## Recommended manual validation

1. Build from this branch using the **same local signing key** as your existing installation, or use a separate test device.
2. Confirm all existing manuscripts and Springer status checks remain intact.
3. Add an Elsevier Author Hub link and an accepted-article tracking link under **Tracking links**. Confirm fields explicitly displayed on their pages are found.
4. On Editorial Manager, sign in and save the **Author Main Menu** by tapping **Track page**; check whether the app discovers an exact manuscript ID in a visible folder.
5. Recheck when a manuscript moves from Submissions Being Processed to Revisions Being Processed. Report the error text if it does not work.
6. Compare Springer reviewer counts to the visible submission page; no count should appear when it is not shown.

Privacy: Use synthetic IDs and redacted screenshots when reporting issues. Never post cookies, ORCID credentials, private manuscript tracking UUIDs, or full personal URLs in GitHub issues.

## Tracking-source diagnostics update

- Each tracking link now saves its own last checked status, last attempt time, and extraction error. One successful source no longer conceals a different source's error.
- **Tracking links** now offers **Check [source]** and **Open tracking page**. Open a link in the app and select **Track page** after JavaScript content renders to try foreground extraction.
- Explicit reviewer count observations can be saved even when the page has no recognized overall editorial status.
- Explicitly marked active steps in Elsevier tracking timelines can be recognized; the app deliberately avoids guessing from unmarked historical steps.

Live Elsevier Author Hub markup remains unverified. Do not share real tracking tokens or private manuscript links in public issues.


## v0.2.0-dev.1: Reviewer detail and journal discovery fixes

- Author Hub reviewer counts now distinguish **Invited: 2+** from an exact invitation count of two. `Reviews completed: 0` is explicitly captured and shown as **Completed: 0**.
- Author Hub can recognize an explicitly prominent **Under Review** banner even if the page does not contain a field literally labeled `Current status`. Recognition is conservative and may still require further live-page adaptation.
- The **Tracking links** dialog now has a **Refresh tracking results** button, updates the visible list immediately after removing or adding a link, and keeps per-source error details visible.
- The journal browser has a **Scan Editorial Manager folders** action. It uses folder links discovered from the **rendered, authenticated Author Main Menu** and only follows read-only, same-host GET links. For menus that navigate with JavaScript or form POSTs, browsing the folders manually inside the app now attempts to recognize the manuscript automatically after each folder opens.
- This is a development build with `versionCode = 11`; the Android package identifier remains unchanged for in-place upgrades signed with the same local Android Studio key.

**Verification:** Unit tests use synthetic Author Hub HTML corresponding to the visible labels `Review invitations sent: 2+`, `Review invitations accepted: 2`, and `Reviews completed: 0`. Real publisher navigation remains unverified. Do not share live tracking URLs or manuscript identifiers in public issues.
