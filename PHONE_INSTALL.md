# Install Manuscript Watch using a GitHub Actions build

This repository contains **Android source code**. Its [Build Android APK](https://github.com/Orbolato/Submission-Tracker-App/actions/workflows/build-apk.yml) workflow compiles a **debug APK**, not a signed production release.

## Build and download without Android Studio

1. Sign in to GitHub and open **Actions → Build Android APK** in this repository.
2. Choose **Run workflow** on `main` (or open the most recent completed run).
3. Confirm that the workflow shows a green check mark for **Run unit tests**, **Compile installable APK** and **Provide APK download**.
4. At the bottom of the run page, download its APK from **Artifacts**. GitHub may provide a ZIP; if so, extract `app-debug.apk` first.
5. Transfer the APK to your Android phone and install it through its file manager, following the phone's normal unknown-app source approval prompts. Do not disable system security protections to force installation.

## Important: preserving your existing app data

A debug APK from GitHub may use a different signing key from the APK you installed using Android Studio. Android normally **refuses an in-place update** when signatures differ. Do not uninstall an existing Manuscript Watch installation merely to force the new debug APK to install: uninstalling clears local manuscript records, history, ORCID vault contents and publisher login sessions.

For development on the same PC, use Android Studio's Run or an APK signed with the same existing key. For distributing repeatable releases, configure a securely managed **release signing key** before public distribution. Never commit signing keys or their passwords.

## Capabilities and limitations

- You must add manuscripts and sign in to journal websites inside Manuscript Watch manually.
- The background scheduler runs on Android and may be delayed by battery optimization.
- Springer Nature's JavaScript-rendered checker is experimental; Elsevier uses a generic HTML checker. Neither guarantees recognition of every manuscript status.
- Use ORCID credentials only with the official ORCID service. Password storage in the app is optional and local.
- No publisher, journal, or ORCID organization endorses this independent project.
