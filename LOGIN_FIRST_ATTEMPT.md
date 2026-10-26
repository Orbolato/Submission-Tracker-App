# First Springer Nature login attempt fails, second succeeds (v0.1.3)

## What we can infer

The same credentials succeeding on a second attempt suggests (but does not prove) an SSO-state, cookie-timing, redirect, or provider-side issue, not necessarily a typo. The app already permits JavaScript, DOM storage, and first- and third-party cookies. No specific root cause has been confirmed.

## Changes

- **Resume login** returns to the saved stable journal sign-in page while preserving WebView cookies; it does not reload expired transient OAuth query parameters or silently retry submitted passwords.
- Flush cookie updates at page completion and when popup windows close.
- Display a basic main-frame HTTP error hint if the provider returns 4xx/5xx. This code is not enough to identify a failed password.
- Do not store ORCID or Springer passwords, authorization codes or temporary OAuth `state` values.

## How to diagnose

1. Start sign-in at the stable journal URL saved in the manuscript entry; not an idp `gateway?...state=...` link.
2. Complete authentication in the in-app browser. If the website rejects the first attempt, tap **Resume login**, then retry interactively only when prompted.
3. Note the exact error text and the page host (never share the full URL if it has `code`, `state`, `session` or tokens).
4. If the site consistently rejects embedded browsers, use Chrome for manual login. Chrome cookies cannot be transferred to the app's background HTTP checker.

## Limitations

This only attempts to improve navigation and cookie persistence, not guaranteed first-try login. Publisher defenses or redirect logic could cause failures that this app cannot fix. The app's background HTML checker is independent of WebView JavaScript and may not work even if login succeeds. This version has not been compiled or tested against the publisher.
