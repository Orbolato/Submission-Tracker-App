# Manuscript Watch v0.1.1: Android status/navigation bar fix

This patch addresses content overlapping the status bar (top) and gesture/navigation area (bottom), particularly on HyperOS / Android 15+.

## Changes

- Adds `SystemBarInsets.kt` with a single safe-area handler using AndroidX WindowInsetsCompat.
- Applies status-bar, navigation-bar and display-cutout insets to both `MainActivity` and `BrowserActivity`.
- Protects the journal browser from keyboard (IME) occlusion when logging in.
- Preserves original padding on every inset update, avoiding accumulating extra space on orientation changes.
- Increases versionCode from 1 to 2 and versionName to 0.1.1.

## Test in Android Studio

1. Unzip the updated project into a new directory, open that directory in Android Studio, and let Gradle sync.
2. Run on your Xiaomi phone over USB, or on an emulator with Android 15 or 16 and gesture navigation.
3. Confirm the dashboard heading stays **below the status-bar icons** and the bottom explanation stays **above the navigation gesture bar** when fully scrolled down.
4. Use a manuscript's **Sign in** button and confirm the web page and buttons remain inside the safe area.
5. Switch between gesture and 3-button navigation if available; rotate the device if allowed.
6. Open the Add paper dialog, focus an input, and confirm it is still reachable while the keyboard is visible.

This has been checked by source inspection but **not compiled or tested on a device** in this environment. You must rebuild the APK in Android Studio. If Android rejects the updated APK with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, it was signed using a different debug key; reinstall may require uninstalling the original and would erase the app's local manuscript data. Avoid uninstalling if you have data to preserve.
