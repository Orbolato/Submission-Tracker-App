# Signing Android APKs and preserving installed data

Manuscript Watch uses the Android application ID `org.manuscriptwatch.app`. Android requires a compatible signing certificate when installing a newer build over an existing installation.

## Current release

GitHub's **v0.2.0** release contains tagged **source code only**. The automatic build workflow produces an experimental `app-debug.apk`, not a production release. The current source version is `0.2.0`, `versionCode = 17`.

**Do not publish a debug artifact as a production-signed APK.** The GitHub Actions debug signing key can differ from the key used by a local Android Studio installation and even from other CI runs.

## Create and protect a long-term release key

Before publishing APKs intended for public installation:

1. In Android Studio, open **Build → Generate Signed Bundle / APK**, select **APK**, then select **Create new** keystore.
2. Choose a secure, private location **outside the repository**. Use a strong keystore password, a strong key password, and an appropriate key alias. Set the key validity long enough for the expected lifetime of updates.
3. Keep at least two protected backups of the keystore and securely preserve its alias and passwords separately. If you lose the key, you may not be able to publish in-place updates to applications signed with it.
4. Generate a **release** build signed with this key. Verify the resulting signing certificate fingerprint with Android SDK `apksigner` (for example, `apksigner verify --print-certs your-release.apk`).
5. Keep the keystore and passwords **out of Git commits, pull requests, screenshots, and CI logs**. Do not upload them into this chat or attach them to public issues.
6. If automating release signing through GitHub Actions later, use restricted repository secrets and a carefully reviewed workflow. This repository does **not** yet configure a persistent production signing key or distribute automatically signed release APKs.

## Existing Android Studio debug installations

If Manuscript Watch is currently installed through Android Studio using a debug key, **a newly created production release key will not be compatible with it**. Updating `versionCode` does not solve a signing mismatch.

For immediate, lossless updates, continue building via the **same local Android Studio debug keystore**. Do not uninstall just to switch to a CI-produced debug APK.

For an eventual migration to the long-term release key:

1. Update your working debug-signed installation to **v0.2.0 or later**, and use **Backup records** to export a verified, private JSON backup.
2. Make an additional offline copy of the file. Check that your manuscript count and history are represented; do **not** share the JSON publicly.
3. Understand that switching to the new key requires uninstalling the old, incompatible signature first; Android will clear that app's private data, encrypted ORCID vault, and WebView session cookies.
4. Only after confirming backups and accepting loss of private sessions, uninstall the old app and install the new production-signed APK.
5. Select **Restore records**, choose the JSON backup, and confirm replacement. Sign in to publishers again. ORCID credentials must be configured anew if needed.

This is a **one-time migration** to a new signing identity; afterward, every future production APK must be signed with the *same* release key.

## Source-release policy

Until the long-term signing key has been established and safely managed, the GitHub release contains only source archives. The debug APK under Actions is for testing and local development, not for a general public upgrade path.

Project files ignore `*.jks`, `*.keystore`, and common signing-secret file formats, but these ignore rules are not a substitute for securely managing secrets.
