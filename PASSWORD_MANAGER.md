# Android Password Manager support (v0.1.4)

## What has been implemented

- BrowserActivity sets `WebView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES` on the main WebView and on authentication pop-up WebViews.
- Login screens display instructions on using your phone’s saved credentials.
- This app does **not** read, transmit, store, or replay your ORCID password. It uses the Android autofill framework to let the user-selected password manager fill website fields interactively.
- Existing WebView cookie persistence is retained. Authentication into a journal does not provide universal authentication to other journals.

## On Xiaomi HyperOS

1. Open **Settings** and search for **Autofill service** or **Passwords, passkeys & accounts**. Select **Google** (or another password manager you trust) as the service.
2. Check that your login is saved for `orcid.org` using Google Password Manager (https://passwords.google.com).
3. Open Manuscript Watch -> select manuscript -> **Sign in** -> proceed to the ORCID sign-in form.
4. Tap the email/password field and select the autofill suggestion if it appears. Confirm using your fingerprint, PIN, or password-manager unlock as required.
5. Complete the ORCID/publisher login and open the actual journal manuscript status table. Tap **Use page**.
6. Close and reopen the app to check whether publisher cookies are preserved.

If a suggestion does not appear, check whether the account is saved for the specific sign-in domain shown, whether your selected provider supports this WebView, and whether the site permits login in embedded browsers. Alternatively sign in with Chrome manually; Chrome cookies are not transferred to Manuscript Watch.

## Fundamental limitation

Android password managers are user-mediated credential providers, not background password APIs. A periodic WorkManager job cannot silently extract the user's ORCID password or force autofill without an interactive login screen. Publisher authentication can also require MFA, CAPTCHA, or prohibit embedded login; do not bypass those protections. Reusing valid publisher sessions and providing user-visible reauthentication prompts are the supported approaches. Automatic authentication to a provider's website is possible only through an authorized publisher integration with appropriate tokens/scopes and without bypassing restrictions.

The app has not been compiled or tested against live ORCID/Springer sites in this environment.

References:
- https://developer.android.com/identity/autofill
- https://developer.android.com/reference/android/webkit/WebView
- https://developer.chrome.com/docs/android/custom-tabs/guide-auth-tab
