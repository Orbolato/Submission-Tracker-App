# Build and install Manuscript Watch using only an Android phone

The ZIP is **source code**, not an installable app. GitHub Actions can compile a test APK on GitHub's servers, without installing Android Studio on your phone. This first build is EXPERIMENTAL, and has not yet been compiled or tested on a device.

## One-time setup on Android

1. Download `ManuscriptWatch-phone-build.zip` to your Android Downloads folder. Do not try to install the ZIP.
2. Install [Termux](https://termux.dev/) from a trusted official distribution and open it.
3. Paste these commands into Termux one at a time:

```sh
pkg update
pkg install -y unzip git gh
termux-setup-storage
```

Grant storage access when Android asks. Then:

```sh
cd "$HOME"
unzip -o "$HOME/storage/downloads/ManuscriptWatch-phone-build.zip"
cd manuscript-watch-android
git init -b main
git add .
git -c user.name='ManuscriptWatch Builder' -c user.email='local-builder@example.invalid' commit -m 'Add Manuscript Watch app and APK build'
gh auth login -w
gh repo create manuscript-watch-android --private --source=. --remote=origin --push
```

When `gh auth login -w` runs, choose GitHub.com, HTTPS, and follow browser sign-in. The device-code authorization may ask you to open `https://github.com/login/device` in Chrome and enter the code displayed in Termux. Do not share that code with anyone.

The final command creates a **private** GitHub repository and pushes the app; your first GitHub Actions build starts on the `main` push. You do **not** need to manually create the repository in the browser.

## Download your APK

1. In your phone browser, visit your GitHub account, open repository `manuscript-watch-android` and choose **Actions**.
2. Open **Build Android APK**, then the latest workflow run. It must have a green check mark; a red X means compilation failed.
3. Under **Artifacts**, download `ManuscriptWatch-debug`. This workflow requests a direct APK download. If GitHub supplies a ZIP instead, extract it first, then find `app-debug.apk`.
4. In Android Files/Downloads, tap `app-debug.apk`, accept the installation prompt, and permit installation from your browser or file manager *only if you trust the app you built*. The menu is usually Android **Settings > Apps > Special app access > Install unknown apps**. Depending on your Android version/location, there can be additional developer-verification prompts.
5. Once installed, launch Manuscript Watch and manually add your manuscripts. Grant notifications if prompted.

Note: This is a debug build signed with an automatically generated testing key; it is not a production or Play Store release. Future debug builds on different GitHub runners may be signed with different keys and may not update the old installation without uninstalling it. Uninstalling removes the app's locally saved data, so do not rely on this as a permanent archive.

## Limitations

- The workflow has not run yet. The source may need fixes if GitHub reports build errors. Check the failed build log for details.
- This app is a prototype. Journal authentication, access rules, site-specific parsing, session expiry, and battery optimization may block reliable background checking.
- ORCID is not a universal API for retrieving all private journal submission statuses.
- Never put editorial or ORCID passwords, authentication tokens, or cookies in the repository, workflow logs, or source files.
