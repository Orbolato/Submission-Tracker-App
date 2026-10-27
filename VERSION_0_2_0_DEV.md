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

## v0.2.0-dev.2: Springer Nature peer-review news

- Recognize explicitly displayed SNAPP reviewer-news sentences such as **"The editor has invited 6 reviewer(s)"** and **"There is 1 reviewer(s) that has accepted to review your manuscript"**.
- Record the example as **Invited: 6 | Accepted: 1**. Do **not** show **Completed: 0** when Springer does not report completed or received reviews. Unavailable information remains unknown.
- Continue using existing labeled reviewer metrics if Springer displays them, including an explicitly stated "Reviews received" count.
- During background monitoring, allow a few additional WebView polls after the current status renders so asynchronous reviewer-news content can appear.
- Add synthetic regression tests covering both sentence patterns, explicit received reviews, and misleading unrelated prose.
- Increase Android versionCode to 12, versionName to `0.2.0-dev.2`, while retaining the same application ID for same-key updates.

Actual Springer DOM layouts and live account session behavior still require an on-device check. Do not commit screenshots containing manuscript IDs or private tracking links.

## v0.2.0-dev.4: Elsevier and Editorial Manager recognition hardening

- Preserve the originally opened manuscript-specific Elsevier tracking URL when an Author Hub or article-production page updates its address to an otherwise identical, query-free route. Keep tracking identifiers local and never reuse them on a different host.
- Recognize Editorial Manager author menus when the title is rendered as ordinary page text, or when several folder controls are rendered using JavaScript rather than direct links. The menu can be saved even if folder navigation is not safely automatable.
- Add synthetic regression tests for query-preserving navigation, wrong-host rejection, and JavaScript-only Editorial Manager folder menus.
- Android versionCode 14, versionName `0.2.0-dev.4`; same application ID and on-device data format.

**Known limitation:** Editorial Manager menus requiring JavaScript or POST requests still cannot be searched completely by the read-only GET folder crawler. Opening the relevant folder manually remains a fallback. The publisher pages and login sessions are not available in CI, so passing synthetic tests does not prove live compatibility.

**Testing:** Install from the same Android Studio signing key as the previous version, open the existing Elsevier tracking link using **Tracking links → Open tracking page → Track page**, then review its individual check result. On Editorial Manager, open the signed-in **Author Main Menu**, select **Track page**, and use **Scan Editorial Manager folders**. If either fails, share only the error text with tracking IDs redacted.
