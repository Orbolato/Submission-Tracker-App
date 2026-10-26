# Manuscript Watch v0.1.6 — Website monitoring only

Removed the optional email-notification monitoring feature. This update removes:

- The **Enable email alerts** button and email monitoring permission instructions.
- The Android `NotificationListenerService` declaration and its implementation.
- Gmail/Outlook/other mail notification preview parsing and associated unit test.

**Retained:** Android push-style *local device notifications* when a manuscript's status **changes based on a website check**. These require normal Android notification permission, not the powerful notification-listener access permission. Manual/foreground website observation and the existing background website checker remain unchanged, as do the optional encrypted ORCID vault and Springer detail-page support.

**Existing users:** This keeps the original Android `applicationId` and increases `versionCode` to 7, so updating with the same signing key preserves manuscripts, history, and encrypted credentials. Old history events previously sourced from an email preview are preserved as historical data, but **no new email events can be collected**. If the new build is signed with a different key, Android will not install it as an update; do not uninstall the old app before deciding how to preserve its data.

This source update does not solve JavaScript-only background status pages and has not been built or device-tested here. Verify in Android Studio before relying on it.
