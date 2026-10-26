# Springer Nature login and manuscript monitoring (v0.1.2)

## What the link means

An URL beginning `https://idp-personal-authenticator.springernature.com/gateway?response_type=code&...` is an **authentication redirect** with a temporary OAuth-style state value. It is not a stable manuscript-status address and should not be pasted into the app's Status-list URL field or shared with others. Links pointing to `notifications.springernature.com` are for Springer Nature notifications, not necessarily a journal's submission-tracking dashboard.

## How to use the app

1. Edit the manuscript. Set **Journal sign-in page** to the stable HTTPS home page of the journal's actual submission platform (Editorial Manager, ScholarOne or the publisher's own editorial portal), not the temporary gateway link.
2. Tap Sign in and complete the journal-specific sign-in. If the publisher refuses embedded browsers, use the **Chrome** button for manual access only.
3. Once the actual manuscript folder/status table is visible in the in-app browser, tap **Use page**. The app blocks known OAuth URLs and the Springer Nature Notifications host from being saved as status pages.
4. Tap Check. If the portal renders content with JavaScript, expires its session or blocks background HTTP access, this app cannot automatically check the site; an authorized website-specific connector may be needed.

## What this update does and does not fix

- Shows a meaningful warning on authentication pages and Springer Nature Notifications.
- Supports Back and Reload within the embedded journal browser, and tracks popup windows when saving a candidate page URL.
- Prevents saving known temporary authorization redirects as manuscript-status URLs in the browser, editor and background checker.
- **Does not** bypass anti-bot protections, CAPTCHA, WebView OAuth restrictions, device verification or publisher access policies.
- **Does not** share cookies between Chrome and the embedded browser, or ensure Springer-specific background scraping works. For that, a documented and authorized publisher integration or supported notification channel is needed.

Only source was edited. This project has not been compiled or run against Springer Nature and there is no assurance that sign-in will succeed in the embedded browser.
