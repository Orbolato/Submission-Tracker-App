# Submission Tracker App

**Manuscript Watch** is an experimental Android application for monitoring editorial submission statuses. The v0.1.8 source includes Elsevier monitoring and Springer Nature's rendered-page status checker.

## Import v0.1.8 source (one-time setup)

The public-ready source archive is provided in the ChatGPT conversation. To populate this repository:

1. Download `ManuscriptWatch-v0.1.8-GitHub-public-ready.zip`.
2. On the GitHub repository page, choose **Add file → Upload files**.
3. Upload the ZIP **without extracting it**, keeping the exact filename in the repository root.
4. Choose **Commit changes** on `main`.
5. Open **Actions → Import Manuscript Watch v0.1.8 source** to verify the import. The workflow checks the ZIP's SHA-256 before extracting and committing its files.

After import, source files will appear under `app/`, with Gradle and tests. The ZIP is removed by the import workflow.

## Building

Use `gradlew.bat assembleDebug` in Android Studio on Windows. Alternatively, run the **Build Android APK** workflow from the GitHub Actions tab after the source import completes.

This code is experimental and has not been verified against all publisher pages. Do not commit your ORCID credentials, cookies, private manuscript URLs, local device databases, signing keys, or any file containing account secrets.
